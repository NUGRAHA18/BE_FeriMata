package com.fertimata.backend.ai;

/**
 * Tidak ada foto patroli yang cukup baru untuk dikirim. {@code run_irrigation} dan {@code run_fertilization} AI
 * menolak permintaan tanpa foto ("predict requires at least one image"), jadi AI tidak dipanggil.
 */
public class NoPhotoException extends AiServiceException {

    public NoPhotoException(String message) {
        super(message);
    }
}
