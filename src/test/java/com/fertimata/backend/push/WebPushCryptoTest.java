package com.fertimata.backend.push;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    // RFC 8291 §5 "Push Message Encryption Example".
    private static final String PLAINTEXT = "When I grow up, I want to be a watermelon";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";
    private static final String AS_PUBLIC =
            "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String UA_PRIVATE = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
    private static final String UA_PUBLIC =
            "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AUTH_SECRET = "BTBZMqHH6r4Tts7J_aSIgg";
    private static final String SALT = "DGv6ra1nlYgDCS1FRnbzlw";
    private static final String EXPECTED_BODY = "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6"
            + "e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexS"
            + "gSxsj_Qulcy4a-fN";

    @Test
    @DisplayName("encryption reproduces the RFC 8291 example byte for byte")
    void matchesRfc8291Example() throws Exception {
        KeyPair serverKeys = new KeyPair(
                EcKeys.publicKey(EcKeys.decodeBase64Url(AS_PUBLIC)), EcKeys.privateKey(EcKeys.decodeBase64Url(AS_PRIVATE)));

        byte[] body = WebPushEncryption.encrypt(
                EcKeys.decodeBase64Url(UA_PUBLIC),
                EcKeys.decodeBase64Url(AUTH_SECRET),
                PLAINTEXT.getBytes(StandardCharsets.UTF_8),
                serverKeys,
                EcKeys.decodeBase64Url(SALT));

        assertThat(EcKeys.encodeBase64Url(body)).isEqualTo(EXPECTED_BODY);
    }

    @Test
    @DisplayName("a browser holding the subscription keys can decrypt a freshly encrypted message")
    void roundTripsWithRandomKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(EcKeys.P256);
        KeyPair browser = generator.generateKeyPair();
        byte[] uaPublic = EcKeys.encode((ECPublicKey) browser.getPublic());
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        byte[] message = "{\"title\":\"Listrik padam\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushEncryption.encrypt(uaPublic, auth, message);

        assertThat(decrypt(body, (ECPrivateKey) browser.getPrivate(), uaPublic, auth)).isEqualTo(message);
    }

    @Test
    @DisplayName("the VAPID token names the push service origin and verifies with the public key")
    void vapidTokenVerifies() throws Exception {
        ECPrivateKey privateKey = EcKeys.privateKey(EcKeys.decodeBase64Url(AS_PRIVATE));
        ECPublicKey publicKey = EcKeys.publicKey(EcKeys.decodeBase64Url(AS_PUBLIC));
        VapidSigner signer = new VapidSigner(privateKey, publicKey, "mailto:ops@example.com");
        Instant now = Instant.parse("2026-10-29T03:00:00Z");

        String header = signer.authorization(URI.create("https://fcm.googleapis.com/fcm/send/abc:def"), now);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + AS_PUBLIC);
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = token.split("\\.");
        String claims = new String(EcKeys.decodeBase64Url(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims)
                .contains("\"aud\":\"https://fcm.googleapis.com\"")
                .contains("\"exp\":" + now.plusSeconds(12 * 3600).getEpochSecond())
                .contains("\"sub\":\"mailto:ops@example.com\"");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(publicKey);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(EcKeys.decodeBase64Url(parts[2]))).isTrue();
    }

    /** Sisi browser RFC 8291, cukup untuk membuktikan enkripsi bisa dibuka. */
    private static byte[] decrypt(byte[] body, ECPrivateKey uaPrivate, byte[] uaPublic, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        buffer.getInt();
        byte[] asPublic = new byte[buffer.get()];
        buffer.get(asPublic);
        byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(uaPrivate);
        agreement.doPhase(EcKeys.publicKey(asPublic), true);
        byte[] keyInfo = ByteBuffer.allocate(14 + 65 + 65)
                .put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(uaPublic)
                .put(asPublic)
                .array();
        byte[] ikm = WebPushEncryption.hkdf(auth, agreement.generateSecret(), keyInfo, 32);
        byte[] cek = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushEncryption.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);
        assertThat(padded[padded.length - 1]).isEqualTo((byte) 0x02);
        return Arrays.copyOf(padded, padded.length - 1);
    }
}
