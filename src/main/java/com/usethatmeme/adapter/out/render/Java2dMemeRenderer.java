package com.usethatmeme.adapter.out.render;

import com.usethatmeme.application.port.out.MemeRendererPort;
import com.usethatmeme.domain.template.Slot;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Draws captions in classic meme style (white text, black outline), shrinking the
 * font until the wrapped text fits its slot.
 */
@Component
class Java2dMemeRenderer implements MemeRendererPort {

    private static final Logger log = LoggerFactory.getLogger(Java2dMemeRenderer.class);

    private static final List<String> PREFERRED_FONTS = List.of(
            "Noto Sans CJK TC", "Noto Sans TC", "Microsoft JhengHei", "PingFang TC",
            "Heiti TC", "WenQuanYi Zen Hei", "Arial Unicode MS");
    private static final int PADDING = 6;
    private static final int MAX_FONT_SIZE = 72;
    private static final int MIN_FONT_SIZE = 10;

    private final String fontFamily;

    Java2dMemeRenderer(RenderProperties properties) {
        this.fontFamily = pickFont(properties.fontFamily());
        log.info("Meme renderer will use font '{}'", fontFamily);
    }

    @Override
    public RenderedImage render(byte[] templateImage, List<Slot> slots, Map<Integer, String> captions) {
        BufferedImage source = decode(templateImage);
        BufferedImage canvas = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g.drawImage(source, 0, 0, null);
            for (Slot slot : slots) {
                String text = captions.get(slot.slotNo());
                if (text != null && !text.isBlank()) {
                    drawCaption(g, slot, text.strip());
                }
            }
        } finally {
            g.dispose();
        }
        return new RenderedImage(encode(canvas), "png", "image/png");
    }

    private void drawCaption(Graphics2D g, Slot slot, String text) {
        int boxWidth = slot.width() - 2 * PADDING;
        int boxHeight = slot.height() - 2 * PADDING;

        Font font = null;
        FontMetrics metrics = null;
        List<String> lines = List.of(text);
        for (int size = Math.min(boxHeight, MAX_FONT_SIZE); size >= MIN_FONT_SIZE; size -= 2) {
            font = new Font(fontFamily, Font.BOLD, size);
            metrics = g.getFontMetrics(font);
            lines = wrap(text, metrics, boxWidth);
            if (lines.size() * metrics.getHeight() <= boxHeight) {
                break;
            }
        }
        if (font == null) {
            return;
        }

        int textHeight = lines.size() * metrics.getHeight();
        float y = slot.y() + PADDING + (boxHeight - textHeight) / 2f + metrics.getAscent();
        float strokeWidth = Math.max(2f, font.getSize() / 8f);
        g.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (String line : lines) {
            float x = slot.x() + PADDING + (boxWidth - metrics.stringWidth(line)) / 2f;
            GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), line);
            Shape outline = glyphs.getOutline(x, y);
            g.setColor(Color.BLACK);
            g.draw(outline);
            g.setColor(Color.WHITE);
            g.fill(outline);
            y += metrics.getHeight();
        }
    }

    /** Greedy wrap that works for CJK text (no spaces) and prefers breaking at spaces in Latin text. */
    static List<String> wrap(String text, FontMetrics metrics, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int lastSpace = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            i += ch.length();
            if (ch.equals("\n")) {
                lines.add(line.toString());
                line.setLength(0);
                lastSpace = -1;
                continue;
            }
            line.append(ch);
            if (ch.equals(" ")) {
                lastSpace = line.length() - 1;
            }
            if (metrics.stringWidth(line.toString()) > maxWidth && line.length() > 1) {
                if (lastSpace > 0) {
                    lines.add(line.substring(0, lastSpace).stripTrailing());
                    String rest = line.substring(lastSpace + 1);
                    line.setLength(0);
                    line.append(rest);
                } else {
                    String overflow = ch;
                    line.setLength(line.length() - overflow.length());
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(overflow);
                }
                lastSpace = -1;
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private static String pickFont(String requested) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        if (requested != null && !requested.isBlank() && available.contains(requested)) {
            return requested;
        }
        return PREFERRED_FONTS.stream().filter(available::contains).findFirst().orElse(Font.DIALOG);
    }

    private static BufferedImage decode(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalArgumentException("The template image could not be decoded");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("The template image could not be read", e);
        }
    }

    private static byte[] encode(BufferedImage image) {
        try {
            var out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the meme image", e);
        }
    }
}
