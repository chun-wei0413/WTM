package com.wtm.application.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.application.library.GetLibraryImageHandler.ImageFile;
import com.wtm.application.port.out.FavoritePort;
import com.wtm.application.port.out.LibraryBrowsePort;
import com.wtm.application.port.out.LibraryBrowsePort.LibraryCard;
import com.wtm.application.port.out.LibraryBrowsePort.LibraryImage;
import com.wtm.application.port.out.ObjectStoragePort;
import com.wtm.application.port.out.SearchLogPort;
import com.wtm.application.port.out.SearchLogPort.HotTerm;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LibraryHandlersTest {

    private final LibraryBrowsePort library = mock(LibraryBrowsePort.class);
    private final FavoritePort favorites = mock(FavoritePort.class);
    private final SearchLogPort searchLog = mock(SearchLogPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final UUID user = UUID.randomUUID();

    private LibraryCard card(String key) {
        return new LibraryCard(UUID.randomUUID(), "name", key, 640, 480, "meaning", List.of("tag"), "???", "UPLOAD", null, null, null);
    }

    @Test
    void randomMemesGetATemporaryAddressForTheirPicture() {
        when(storage.presignedGetUrl(eq("library/a.png"), any())).thenReturn("https://storage/a");
        when(library.random(5)).thenReturn(List.of(card("library/a.png")));

        List<LibraryItem> items = new RandomMemesHandler(library, storage).handle(5);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.imageUrl()).isEqualTo("https://storage/a");
            assertThat(item.meaning()).isEqualTo("meaning");
            assertThat(item.imageWidth()).isEqualTo(640);
            assertThat(item.imageHeight()).isEqualTo(480);
        });
    }

    @Test
    void randomMemesAreAskedForWithinTheAllowedRange() {
        var handler = new RandomMemesHandler(library, storage);
        when(library.random(anyInt())).thenReturn(List.of());

        handler.handle(0);
        handler.handle(10_000);

        ArgumentCaptor<Integer> asked = ArgumentCaptor.forClass(Integer.class);
        verify(library, org.mockito.Mockito.times(2)).random(asked.capture());
        assertThat(asked.getAllValues()).containsExactly(1, 50);
    }

    @Test
    void favoritingAnUnpublishedMemeLooksLikeFavoritingAMissingOne() {
        UUID id = UUID.randomUUID();
        when(favorites.add(user, id)).thenReturn(false);

        assertThatThrownBy(() -> new FavoritesHandler(favorites, storage).add(user, id))
                .isInstanceOf(TemplateNotFoundException.class);
    }

    @Test
    void aFavoriteIsAddedAndRemovedForTheUserWhoAskedOnly() {
        UUID id = UUID.randomUUID();
        when(favorites.add(user, id)).thenReturn(true);
        var handler = new FavoritesHandler(favorites, storage);

        handler.add(user, id);
        handler.remove(user, id);

        verify(favorites).add(user, id);
        verify(favorites).remove(user, id);
    }

    @Test
    void thePictureIsReturnedWithItsOwnTypeAndFileName() {
        UUID id = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        when(library.findPublishedImage(id)).thenReturn(Optional.of(new LibraryImage("library/x.GIF", "n")));
        when(storage.get("library/x.GIF")).thenReturn(bytes);

        ImageFile file = new GetLibraryImageHandler(library, storage).handle(id);

        assertThat(file.contentType()).isEqualTo("image/gif");
        assertThat(file.filename()).isEqualTo("meme-" + id + ".gif");
        assertThat(file.content()).isEqualTo(bytes);
    }

    @Test
    void aPictureThatIsNotPublishedIsNotHandedOut() {
        UUID id = UUID.randomUUID();
        when(library.findPublishedImage(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetLibraryImageHandler(library, storage).handle(id))
                .isInstanceOf(TemplateNotFoundException.class);
        verify(storage, never()).get(anyString());
    }

    @Test
    void searchesAreCountedWithoutRegardToCapitalsOrSpacing() {
        var history = new SearchHistoryHandler(searchLog, Clock.systemUTC());

        history.record(user, "  Black   Guy  ");

        verify(searchLog).record(user, "black guy");
    }

    @Test
    void tooShortAndTooLongPhrasesAreNotRemembered() {
        var history = new SearchHistoryHandler(searchLog, Clock.systemUTC());

        history.record(user, "a");
        history.record(user, "   ");
        history.record(user, null);
        history.record(user, "x".repeat(SearchHistoryHandler.MAX_LENGTH + 1));

        verify(searchLog, never()).record(any(), anyString());
    }

    @Test
    void theMostCommonPhrasesOfTheLastWeekAreOffered() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var history = new SearchHistoryHandler(searchLog, Clock.fixed(now, ZoneOffset.UTC));
        when(searchLog.hot(any(), anyInt())).thenReturn(List.of(new HotTerm("黑人問號", 3)));

        List<HotTerm> hot = history.hot(8);

        assertThat(hot).containsExactly(new HotTerm("黑人問號", 3));
        verify(searchLog).hot(now.minus(Duration.ofDays(7)), 8);
    }
}
