package com.fertimata.backend.push;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Mengirim satu notifikasi terenkripsi. Kunci VAPID rusak/tidak sepasang menghentikan startup (seperti
 * {@code JWT_SECRET} kosong): lebih baik gagal di awal daripada notifikasi darurat diam-diam tidak sampai. */
@Component
public class WebPushClient {

    private static final Logger log = LoggerFactory.getLogger(WebPushClient.class);

    public enum Outcome {
        /** Server push menerima notifikasi. */
        DELIVERED,
        /** Langganan sudah tidak berlaku (404/410): hapus. */
        GONE,
        /** Gagal sementara atau ditolak; langganan disimpan. */
        FAILED
    }

    public record Delivery(Outcome outcome, int status) {}

    public enum Urgency {
        NORMAL,
        HIGH
    }

    private final PushProperties properties;
    private final Clock clock;
    private final VapidSigner signer;
    private final HttpClient http;

    public WebPushClient(PushProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signer = properties.enabled() ? loadSigner(properties) : null;
        this.http = HttpClient.newBuilder()
                .connectTimeout(properties.requestTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        if (signer != null) {
            log.info("Web Push enabled for alerts of severity {} and above", properties.minSeverity());
        }
    }

    public boolean enabled() {
        return signer != null;
    }

    /** Kunci publik VAPID untuk {@code pushManager.subscribe({applicationServerKey})} di browser. */
    public String publicKey() {
        return signer == null ? null : signer.publicKey();
    }

    /** Endpoint harus HTTPS ke salah satu server push yang dikenal. */
    public boolean isAllowedEndpoint(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return properties.allowedEndpointHosts().stream()
                .map(allowed -> allowed.toLowerCase(Locale.ROOT))
                .anyMatch(allowed -> host.equals(allowed) || host.endsWith("." + allowed));
    }

    public Delivery send(String endpoint, String p256dh, String auth, byte[] payload, Urgency urgency) {
        if (signer == null) {
            throw new IllegalStateException("Web Push is not enabled");
        }
        if (!isAllowedEndpoint(endpoint)) {
            log.warn("Refusing to send a push notification to a non-push-service endpoint");
            return new Delivery(Outcome.GONE, 0);
        }
        try {
            URI uri = URI.create(endpoint);
            byte[] body = WebPushEncryption.encrypt(
                    EcKeys.decodeBase64Url(p256dh), EcKeys.decodeBase64Url(auth), payload);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(properties.requestTimeout())
                    .header("TTL", Long.toString(properties.ttl().toSeconds()))
                    .header("Urgency", urgency == Urgency.HIGH ? "high" : "normal")
                    .header("Content-Encoding", "aes128gcm")
                    .header("Content-Type", "application/octet-stream")
                    .header("Authorization", signer.authorization(uri, clock.instant()))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Delivery(Outcome.DELIVERED, status);
            }
            if (status == 404 || status == 410) {
                return new Delivery(Outcome.GONE, status);
            }
            log.warn("Push service {} answered {}: {}", uri.getHost(), status, abbreviate(response.body()));
            return new Delivery(Outcome.FAILED, status);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // Kunci langganan rusak: browser tidak akan pernah bisa membukanya.
            log.warn("Push subscription has unusable keys: {}", ex.getMessage());
            return new Delivery(Outcome.GONE, 0);
        } catch (IOException ex) {
            log.warn("Push service unreachable: {}", ex.getMessage());
            return new Delivery(Outcome.FAILED, 0);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Delivery(Outcome.FAILED, 0);
        }
    }

    private static VapidSigner loadSigner(PushProperties properties) {
        String subject = properties.vapidSubject() == null ? "" : properties.vapidSubject().trim();
        if (!subject.startsWith("mailto:") && !subject.startsWith("https://")) {
            throw new IllegalStateException(
                    "PUSH_ENABLED=true needs VAPID_SUBJECT as mailto:someone@example.com or an https:// URL");
        }
        if (isBlank(properties.vapidPublicKey()) || isBlank(properties.vapidPrivateKey())) {
            throw new IllegalStateException(
                    "PUSH_ENABLED=true needs VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY (npx web-push generate-vapid-keys)");
        }
        try {
            ECPublicKey publicKey = EcKeys.publicKey(EcKeys.decodeBase64Url(properties.vapidPublicKey()));
            ECPrivateKey privateKey = EcKeys.privateKey(EcKeys.decodeBase64Url(properties.vapidPrivateKey()));
            requirePair(publicKey, privateKey);
            return new VapidSigner(privateKey, publicKey, subject);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("VAPID keys are not a valid P-256 key pair: " + ex.getMessage(), ex);
        }
    }

    /** Kunci publik yang bukan pasangan kunci privat membuat setiap notifikasi ditolak 403. */
    private static void requirePair(ECPublicKey publicKey, ECPrivateKey privateKey) throws GeneralSecurityException {
        byte[] probe = "fertimata-vapid-check".getBytes(StandardCharsets.US_ASCII);
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(privateKey);
        signer.update(probe);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey);
        verifier.update(probe);
        if (!verifier.verify(signature)) {
            throw new GeneralSecurityException("VAPID_PUBLIC_KEY does not belong to VAPID_PRIVATE_KEY");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String abbreviate(String body) {
        return body == null || body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
