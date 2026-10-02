package com.wtm.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wtm.application.port.out.MemeRepository;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.domain.meme.Meme;
import com.wtm.domain.meme.MemeId;
import com.wtm.domain.meme.TemplateRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetMemeImageHandlerTest {

    private final MemeRepository memes = mock(MemeRepository.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final GetMemeImageHandler handler = new GetMemeImageHandler(memes, storage);
    private final UUID owner = UUID.randomUUID();

    private Meme meme(String imageKey) {
        Meme meme = Meme.compose(MemeId.newId(), owner, new TemplateRef(UUID.randomUUID(), 1, List.of()), Map.of());
        if (imageKey != null) {
            meme.attachImage(imageKey);
        }
        when(memes.findById(meme.id())).thenReturn(Optional.of(meme));
        return meme;
    }

    @Test
    void returnsTheImageWithItsTypeAndAFileName() {
        Meme meme = meme("memes/x.png");
        when(storage.get("memes/x.png")).thenReturn(new byte[] {1, 2, 3});

        var image = handler.handle(owner, meme.id().value());

        assertThat(image.content()).containsExactly(1, 2, 3);
        assertThat(image.contentType()).isEqualTo("image/png");
        assertThat(image.filename()).isEqualTo("meme-" + meme.id().value() + ".png");
    }

    @Test
    void recognisesJpegImages() {
        Meme meme = meme("memes/x.jpg");
        when(storage.get("memes/x.jpg")).thenReturn(new byte[] {1});

        assertThat(handler.handle(owner, meme.id().value()).contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void someoneElsesMemeAndAMemeWithoutAnImageLookMissing() {
        Meme withImage = meme("memes/x.png");
        Meme withoutImage = meme(null);

        assertThatThrownBy(() -> handler.handle(UUID.randomUUID(), withImage.id().value()))
                .isInstanceOf(MemeNotFoundException.class);
        assertThatThrownBy(() -> handler.handle(owner, withoutImage.id().value()))
                .isInstanceOf(MemeNotFoundException.class);
        assertThatThrownBy(() -> handler.handle(owner, UUID.randomUUID()))
                .isInstanceOf(MemeNotFoundException.class);
    }
}
