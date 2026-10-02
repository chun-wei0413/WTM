package com.memehub.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.memehub.application.port.out.ImageFingerprintPort.Fingerprint;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;

class JdkImageFingerprinterTest {

    private final JdkImageFingerprinter fingerprinter = new JdkImageFingerprinter();

    /** A picture with a few large shapes, like a typical meme, so its structure survives resizing. */
    private static BufferedImage picture(int width, int height, boolean flipped) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(flipped
                ? new GradientPaint(width, 0, new Color(20, 30, 60), 0, 0, new Color(240, 220, 200))
                : new GradientPaint(0, 0, new Color(20, 30, 60), width, 0, new Color(240, 220, 200)));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(230, 60, 60));
        g.fillOval(width / 8, height / 6, width / 3, height / 2);
        g.setColor(new Color(40, 160, 90));
        g.fillRect(width / 2, height / 2, width / 3, height / 3);
        g.dispose();
        return image;
    }

    private static byte[] png(BufferedImage image) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(quality);
        var out = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), params);
        }
        writer.dispose();
        return out.toByteArray();
    }

    private static BufferedImage scaled(BufferedImage source, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(source.getScaledInstance(width, height, java.awt.Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return out;
    }

    @Test
    void identicalBytesGiveIdenticalFingerprints() throws Exception {
        byte[] bytes = png(picture(400, 300, false));

        assertThat(fingerprinter.of(bytes)).isEqualTo(fingerprinter.of(bytes));
        assertThat(fingerprinter.of(bytes).sha256()).hasSize(64);
    }

    @Test
    void aResizedAndRecompressedCopyIsDifferentBytesButTheSamePicture() throws Exception {
        BufferedImage original = picture(800, 600, false);
        Fingerprint a = fingerprinter.of(png(original));
        Fingerprint copy = fingerprinter.of(jpeg(scaled(original, 400, 300), 0.6f));

        assertThat(copy.sha256()).isNotEqualTo(a.sha256());
        assertThat(JdkImageFingerprinter.hammingDistance(a.perceptualHash(), copy.perceptualHash()))
                .as("differing bits between the original and its resized, recompressed copy")
                .isLessThanOrEqualTo(4);
    }

    @Test
    void aDifferentPictureIsFarAway() throws Exception {
        Fingerprint a = fingerprinter.of(png(picture(400, 300, false)));
        Fingerprint other = fingerprinter.of(png(picture(400, 300, true)));

        assertThat(JdkImageFingerprinter.hammingDistance(a.perceptualHash(), other.perceptualHash()))
                .isGreaterThan(20);
    }

    @Test
    void transparentAreasCountAsWhiteNotBlack() throws Exception {
        BufferedImage transparent = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        BufferedImage white = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = white.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 200);
        g.dispose();

        assertThat(fingerprinter.of(png(transparent)).perceptualHash())
                .isEqualTo(fingerprinter.of(png(white)).perceptualHash());
    }

    @Test
    void hammingDistanceCountsDifferingBits() {
        assertThat(JdkImageFingerprinter.hammingDistance(0b1010L, 0b0110L)).isEqualTo(2);
        assertThat(JdkImageFingerprinter.hammingDistance(-1L, 0L)).isEqualTo(64);
        assertThat(JdkImageFingerprinter.hammingDistance(7L, 7L)).isZero();
    }
}
