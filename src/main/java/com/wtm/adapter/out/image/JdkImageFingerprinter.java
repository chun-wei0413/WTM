package com.wtm.adapter.out.image;

import com.wtm.application.port.out.ImageFingerprintPort;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * SHA-256 of the bytes, plus a "difference hash" of the picture: shrink it to 9×8 grey pixels and
 * record, for each pixel, whether it is brighter than its right-hand neighbour. Resizing,
 * recompressing or slightly recolouring a picture changes few of those 64 bits.
 */
@Component
class JdkImageFingerprinter implements ImageFingerprintPort {

    @Override
    public Fingerprint of(byte[] image) {
        return new Fingerprint(sha256(image), differenceHash(decode(image)));
    }

    static long hammingDistance(long a, long b) {
        return Long.bitCount(a ^ b);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));   // the first frame of a GIF
            if (image == null) {
                throw new IllegalArgumentException("The image could not be decoded");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("The image could not be read", e);
        }
    }

    private static long differenceHash(BufferedImage source) {
        // Flatten transparency onto white first, or a transparent area would count as black.
        BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D flatG = flat.createGraphics();
        flatG.setColor(Color.WHITE);
        flatG.fillRect(0, 0, flat.getWidth(), flat.getHeight());
        flatG.drawImage(source, 0, 0, null);
        flatG.dispose();

        Image scaled = flat.getScaledInstance(9, 8, Image.SCALE_AREA_AVERAGING);
        BufferedImage grey = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D greyG = grey.createGraphics();
        greyG.drawImage(scaled, 0, 0, null);
        greyG.dispose();

        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int left = grey.getRaster().getSample(x, y, 0);
                int right = grey.getRaster().getSample(x + 1, y, 0);
                hash = (hash << 1) | (left > right ? 1 : 0);
            }
        }
        return hash;
    }
}
