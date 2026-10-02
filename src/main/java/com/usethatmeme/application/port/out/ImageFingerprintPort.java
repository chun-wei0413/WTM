package com.usethatmeme.application.port.out;

/**
 * Outbound port that reduces a picture to values for telling pictures apart.
 */
public interface ImageFingerprintPort {

    Fingerprint of(byte[] image);

    /**
     * @param sha256 identifies exactly these bytes
     * @param perceptualHash 64 bits describing what the picture looks like, so a resized or
     *                       recompressed copy of the same picture has almost the same hash
     */
    record Fingerprint(String sha256, long perceptualHash) {
    }
}
