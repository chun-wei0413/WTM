package com.memehub.adapter.out.image;

import com.memehub.application.UnsupportedImageException;
import com.memehub.application.port.out.ImageInspectorPort;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Reads only the image header, so oversized or malformed uploads are rejected
 * without decoding the full pixel data.
 */
@Component
class JdkImageInspector implements ImageInspectorPort {

    private static final long MAX_PIXELS = 25_000_000L;

    @Override
    public ImageInfo inspect(byte[] content) {
        if (content == null || content.length == 0) {
            throw new UnsupportedImageException("The image is empty");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new UnsupportedImageException("Unrecognized image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new UnsupportedImageException("The image is too large");
                }
                return switch (format) {
                    case "png" -> new ImageInfo(width, height, "png", "image/png");
                    case "jpeg", "jpg" -> new ImageInfo(width, height, "jpg", "image/jpeg");
                    default -> throw new UnsupportedImageException("Only PNG and JPEG images are supported");
                };
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UnsupportedImageException("The image could not be read");
        }
    }
}
