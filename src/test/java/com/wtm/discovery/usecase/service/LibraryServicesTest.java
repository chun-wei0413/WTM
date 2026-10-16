package com.wtm.discovery.usecase.service;

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

import com.wtm.curation.usecase.port.in.query.GetTemplateUseCase;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import com.wtm.discovery.entity.Favorite;
import com.wtm.discovery.usecase.port.in.query.GetLibraryImageUseCase.ImageFile;
import com.wtm.discovery.usecase.port.in.query.HotTerm;
import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteListPort;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteRepository;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryCard;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryImage;
import com.wtm.discovery.usecase.port.out.searchlog.SearchLogPort;
import com.wtm.shared.TemplateNotFoundException;
import com.wtm.shared.port.ObjectStoragePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LibraryServicesTest {

    private final LibraryBrowsePort library = mock(LibraryBrowsePort.class);
    private final FavoriteRepository favorites = mock(FavoriteRepository.class);
    private final FavoriteListPort shelf = mock(FavoriteListPort.class);
    private final GetTemplateUseCase templates = mock(GetTemplateUseCase.class);
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

        List<LibraryItem> items = new RandomMemesService(library, storage).handle(5);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.imageUrl()).isEqualTo("https://storage/a");
            assertThat(item.meaning()).isEqualTo("meaning");
            assertThat(item.imageWidth()).isEqualTo(640);
            assertThat(item.imageHeight()).isEqualTo(480);
        });
    }

    @Test
    void randomMemesAreAskedForWithinTheAllowedRange() {
        var handler = new RandomMemesService(library, storage);
        when(library.random(anyInt())).thenReturn(List.of());

        handler.handle(0);
        handler.handle(10_000);

        ArgumentCaptor<Integer> asked = ArgumentCaptor.forClass(Integer.class);
        verify(library, org.mockito.Mockito.times(2)).random(asked.capture());
        assertThat(asked.getAllValues()).containsExactly(1, 50);
    }

    private static final java.time.Instant NOW = java.time.Instant.parse("2026-10-09T12:00:00Z");

    private FavoritesService favoriting() {
        return new FavoritesService(favorites, shelf, templates, storage,
                java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
    }

    private TemplateView view(UUID id, String status) {
        return new TemplateView(id, "貓", status, 1, 600, 400, "library/a.png", null, null, List.of(),
                NOW, NOW);
    }

    @Test
    void favoritingAnUnpublishedMemeLooksLikeFavoritingAMissingOne() {
        UUID id = UUID.randomUUID();
        when(templates.handle(id)).thenReturn(view(id, "RETIRED"));

        assertThatThrownBy(() -> favoriting().add(user, id)).isInstanceOf(TemplateNotFoundException.class);
        verify(favorites, never()).save(any());
    }

    @Test
    void aFavoriteIsAddedAndRemovedForTheUserWhoAskedOnly() {
        UUID id = UUID.randomUUID();
        when(templates.handle(id)).thenReturn(view(id, "APPROVED"));
        var service = favoriting();

        service.add(user, id);
        service.remove(user, id);

        verify(favorites).save(Favorite.make(user, id, NOW));
        verify(favorites).remove(user, id);
    }

    @Test
    void theShelfShowsTheUsersFavoritesAsItems() {
        when(shelf.list(user)).thenReturn(List.of());

        assertThat(favoriting().list(user)).isEmpty();
    }

    @Test
    void thePictureIsReturnedWithItsOwnTypeAndFileName() {
        UUID id = UUID.randomUUID();
        byte[] bytes = {1, 2, 3};
        when(library.findPublishedImage(id)).thenReturn(Optional.of(new LibraryImage("library/x.GIF", "n")));
        when(storage.get("library/x.GIF")).thenReturn(bytes);

        ImageFile file = new GetLibraryImageService(library, storage).handle(id);

        assertThat(file.contentType()).isEqualTo("image/gif");
        assertThat(file.filename()).isEqualTo("meme-" + id + ".gif");
        assertThat(file.content()).isEqualTo(bytes);
    }

    @Test
    void aPictureThatIsNotPublishedIsNotHandedOut() {
        UUID id = UUID.randomUUID();
        when(library.findPublishedImage(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetLibraryImageService(library, storage).handle(id))
                .isInstanceOf(TemplateNotFoundException.class);
        verify(storage, never()).get(anyString());
    }

    @Test
    void searchesAreCountedWithoutRegardToCapitalsOrSpacing() {
        var history = new SearchHistoryService(searchLog, Clock.systemUTC());

        history.record(user, "  Black   Guy  ");

        verify(searchLog).record(user, "black guy");
    }

    @Test
    void tooShortAndTooLongPhrasesAreNotRemembered() {
        var history = new SearchHistoryService(searchLog, Clock.systemUTC());

        history.record(user, "a");
        history.record(user, "   ");
        history.record(user, null);
        history.record(user, "x".repeat(SearchHistoryService.MAX_LENGTH + 1));

        verify(searchLog, never()).record(any(), anyString());
    }

    @Test
    void theMostCommonPhrasesOfTheLastWeekAreOffered() {
        Instant now = Instant.parse("2026-10-09T00:00:00Z");
        var history = new SearchHistoryService(searchLog, Clock.fixed(now, ZoneOffset.UTC));
        when(searchLog.hot(any(), anyInt())).thenReturn(List.of(new HotTerm("黑人問號", 3)));

        List<HotTerm> hot = history.hot(8);

        assertThat(hot).containsExactly(new HotTerm("黑人問號", 3));
        verify(searchLog).hot(now.minus(Duration.ofDays(7)), 8);
    }
}
