package com.fertimata.backend.push;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;

/** Header {@code Authorization: vapid} (RFC 8292): bukti ke server push bahwa pengirim memegang kunci langganan. */
final class VapidSigner {

    /** RFC 8292 membatasi exp paling lama 24 jam; 12 jam memberi ruang untuk jam yang tidak sinkron. */
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(12);

    private final ECPrivateKey privateKey;
    private final String publicKeyBase64Url;
    private final String subject;

    VapidSigner(ECPrivateKey privateKey, ECPublicKey publicKey, String subject) {
        this.privateKey = privateKey;
        this.publicKeyBase64Url = EcKeys.encodeBase64Url(EcKeys.encode(publicKey));
        this.subject = subject;
    }

    String publicKey() {
        return publicKeyBase64Url;
    }

    String authorization(URI endpoint, Instant now) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        String header = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}");
        String claims = b64("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                .formatted(audience, now.plus(TOKEN_LIFETIME).getEpochSecond(), subject));
        String signingInput = header + "." + claims;

        // P1363 = r || s, 64 byte: format tanda tangan yang diminta JWS ES256, bukan DER.
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String token = signingInput + "." + EcKeys.encodeBase64Url(signature.sign());
        return "vapid t=" + token + ", k=" + publicKeyBase64Url;
    }

    private static String b64(String json) {
        return EcKeys.encodeBase64Url(json.getBytes(StandardCharsets.UTF_8));
    }
}
