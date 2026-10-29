package com.fertimata.backend.push;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enkripsi payload Web Push, RFC 8291 ({@code Content-Encoding: aes128gcm}, RFC 8188).
 *
 * <p>Ditulis dengan kriptografi JDK saja: ECDH P-256, HKDF-SHA-256 dan AES-128-GCM. Server push
 * (Google, Mozilla, Apple) hanya meneruskan byte terenkripsi; hanya browser pemilik langganan yang
 * bisa membukanya.
 */
final class WebPushEncryption {

    /** Ukuran record; payload notifikasi selalu muat dalam satu record. */
    private static final int RECORD_SIZE = 4096;

    /** Batas plaintext satu record: record - tag GCM (16) - delimiter padding (1). */
    static final int MAX_PLAINTEXT = 3993;

    private static final SecureRandom RANDOM = new SecureRandom();

    private WebPushEncryption() {}

    /**
     * @param uaPublic kunci {@code p256dh} langganan, 65 byte
     * @param authSecret kunci {@code auth} langganan, 16 byte
     * @return badan permintaan HTTP: header aes128gcm diikuti ciphertext
     */
    static byte[] encrypt(byte[] uaPublic, byte[] authSecret, byte[] plaintext) throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(EcKeys.P256);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(uaPublic, authSecret, plaintext, generator.generateKeyPair(), salt);
    }

    /** Versi deterministik untuk diuji dengan contoh RFC 8291 §5. */
    static byte[] encrypt(byte[] uaPublic, byte[] authSecret, byte[] plaintext, KeyPair serverKeys, byte[] salt)
            throws GeneralSecurityException {
        if (plaintext.length > MAX_PLAINTEXT) {
            throw new GeneralSecurityException("Push payload too large: " + plaintext.length + " bytes");
        }
        byte[] asPublic = EcKeys.encode((ECPublicKey) serverKeys.getPublic());

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(serverKeys.getPrivate());
        agreement.doPhase(EcKeys.publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        // RFC 8291 §3.4: gabungkan rahasia ECDH dengan auth secret langganan.
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);

        // RFC 8188 §2.2-2.3: kunci dan nonce konten.
        byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        // Satu-satunya (dan terakhir) record: plaintext diikuti delimiter 0x02, tanpa padding tambahan.
        byte[] padded = Arrays.copyOf(plaintext, plaintext.length + 1);
        padded[plaintext.length] = 0x02;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] ciphertext = cipher.doFinal(padded);

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** HKDF-SHA-256 untuk keluaran paling panjang 32 byte (cukup satu blok expand). */
    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        byte[] prk = hmac(salt, ikm);
        byte[] okm = hmac(prk, concat(info, new byte[] {1}));
        return Arrays.copyOf(okm, length);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
