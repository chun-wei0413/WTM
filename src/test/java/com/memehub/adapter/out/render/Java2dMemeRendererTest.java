package com.memehub.adapter.out.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.memehub.application.port.out.MemeRendererPort.RenderedImage;
import com.memehub.domain.template.Slot;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class Java2dMemeRendererTest {

    private final Java2dMemeRenderer renderer = new Java2dMemeRenderer(new RenderProperties(""));

    private static byte[] whitePng(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static boolean hasNonWhitePixel(BufferedImage image, int x, int y, int width, int height) {
        for (int j = y; j < y + height; j++) {
            for (int i = x; i < x + width; i++) {
                if ((image.getRGB(i, j) & 0xFFFFFF) != 0xFFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void drawsTheCaptionInsideItsSlotAndNowhereElse() throws Exception {
        Slot slot = new Slot(1, "caption", 20, true, 50, 20, 300, 80);

        RenderedImage result = renderer.render(whitePng(400, 300), List.of(slot), Map.of(1, "老闆又改需求"));

        assertThat(result.extension()).isEqualTo("png");
        assertThat(result.contentType()).isEqualTo("image/png");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.content()));
        assertThat(image.getWidth()).isEqualTo(400);
        assertThat(image.getHeight()).isEqualTo(300);
        assertThat(hasNonWhitePixel(image, 50, 20, 300, 80)).as("text inside the slot").isTrue();
        assertThat(hasNonWhitePixel(image, 0, 150, 400, 150)).as("area far below the slot").isFalse();
    }

    @Test
    void leavesTheImageUntouchedWhenThereIsNothingToWrite() throws Exception {
        Slot slot = new Slot(1, "caption", 20, false, 50, 20, 300, 80);

        RenderedImage result = renderer.render(whitePng(400, 300), List.of(slot), Map.of());

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.content()));
        assertThat(hasNonWhitePixel(image, 0, 0, 400, 300)).isFalse();
    }

    @Test
    void fitsLongTextIntoASmallSlotBySmallerLettersAndWrapping() throws Exception {
        Slot slot = new Slot(1, "caption", 40, true, 10, 10, 120, 60);

        RenderedImage result = renderer.render(whitePng(200, 120), List.of(slot),
                Map.of(1, "這是一段比較長的文案,需要換行才放得下"));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.content()));
        assertThat(hasNonWhitePixel(image, 10, 10, 120, 60)).isTrue();
        // Nothing may spill outside the slot.
        assertThat(hasNonWhitePixel(image, 135, 0, 65, 120)).isFalse();
        assertThat(hasNonWhitePixel(image, 0, 80, 200, 40)).isFalse();
    }

    @Test
    void wrapsCjkTextWithoutSpacesAndKeepsEveryCharacter() {
        FontMetrics metrics = metrics();
        String text = "一二三四五六七八九十一二三四五六七八九十";

        List<String> lines = Java2dMemeRenderer.wrap(text, metrics, 100);

        assertThat(lines.size()).isGreaterThan(1);
        assertThat(String.join("", lines)).isEqualTo(text);
        assertThat(lines).allSatisfy(line -> assertThat(metrics.stringWidth(line)).isLessThanOrEqualTo(100));
    }

    @Test
    void wrapsLatinTextAtSpaces() {
        FontMetrics metrics = metrics();

        List<String> lines = Java2dMemeRenderer.wrap("when you pretend everything is fine", metrics, 120);

        assertThat(lines.size()).isGreaterThan(1);
        assertThat(String.join(" ", lines)).isEqualTo("when you pretend everything is fine");
    }

    @Test
    void shortTextStaysOnOneLineAndNewlinesStartNewLines() {
        FontMetrics metrics = metrics();

        assertThat(Java2dMemeRenderer.wrap("短", metrics, 200)).containsExactly("短");
        assertThat(Java2dMemeRenderer.wrap("上\n下", metrics, 200)).containsExactly("上", "下");
    }

    private static FontMetrics metrics() {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        return g.getFontMetrics(new Font(Font.DIALOG, Font.BOLD, 20));
    }
}
