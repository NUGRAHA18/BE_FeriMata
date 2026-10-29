package com.fertimata.backend.push;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Kunci P-256 dalam format yang dipakai Web Push: titik publik tak terkompresi 65 byte
 * ({@code 0x04 || x || y}) dan kunci privat 32 byte, keduanya base64url tanpa padding. Format ini sama
 * dengan keluaran {@code npx web-push generate-vapid-keys} dan dengan {@code PushSubscription.getKey()}
 * di browser.
 */
final class EcKeys {

    static final ECParameterSpec P256 = p256();

    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private EcKeys() {}

    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 0x04) {
            throw new GeneralSecurityException("Expected a 65-byte uncompressed P-256 point");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    static ECPrivateKey privateKey(byte[] raw) throws GeneralSecurityException {
        if (raw.length != 32) {
            throw new GeneralSecurityException("Expected a 32-byte P-256 private key");
        }
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, raw), P256));
    }

    static byte[] encode(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 0x04;
        copyUnsigned(key.getW().getAffineX(), out, 1);
        copyUnsigned(key.getW().getAffineY(), out, 33);
        return out;
    }

    static byte[] decodeBase64Url(String value) {
        // Browser kadang memberi base64 biasa atau dengan padding; terima keduanya.
        String normalised = value.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return B64_DECODER.decode(normalised);
    }

    static String encodeBase64Url(byte[] value) {
        return B64_ENCODER.encodeToString(value);
    }

    /** Menulis bilangan sebagai 32 byte big-endian, membuang byte tanda dan menambah nol di depan. */
    private static void copyUnsigned(BigInteger value, byte[] target, int offset) {
        byte[] bytes = value.toByteArray();
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, bytes.length - length, target, offset + 32 - length, length);
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("The JDK does not provide P-256", ex);
        }
    }
}
