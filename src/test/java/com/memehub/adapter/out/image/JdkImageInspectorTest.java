package com.memehub.adapter.out.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.memehub.application.UnsupportedImageException;
import com.memehub.application.port.out.ImageInspectorPort.ImageInfo;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class JdkImageInspectorTest {

    private final JdkImageInspector inspector = new JdkImageInspector();

    private static byte[] encode(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    @Test
    void readsPngSize() throws Exception {
        ImageInfo info = inspector.inspect(encode("png", 320, 200));

        assertThat(info).isEqualTo(new ImageInfo(320, 200, "png", "image/png"));
    }

    @Test
    void readsJpegSize() throws Exception {
        ImageInfo info = inspector.inspect(encode("jpg", 120, 80));

        assertThat(info).isEqualTo(new ImageInfo(120, 80, "jpg", "image/jpeg"));
    }

    @Test
    void readsGifSize() throws Exception {
        ImageInfo info = inspector.inspect(encode("gif", 160, 90));

        assertThat(info).isEqualTo(new ImageInfo(160, 90, "gif", "image/gif"));
    }

    @Test
    void rejectsFormatsItCannotReadOrKeep() throws Exception {
        assertThatThrownBy(() -> inspector.inspect(encode("bmp", 10, 10)))
                .isInstanceOf(UnsupportedImageException.class);
    }

    @Test
    void rejectsNonImageAndEmptyContent() {
        assertThatThrownBy(() -> inspector.inspect("hello".getBytes()))
                .isInstanceOf(UnsupportedImageException.class);
        assertThatThrownBy(() -> inspector.inspect(new byte[0]))
                .isInstanceOf(UnsupportedImageException.class);
    }
}
