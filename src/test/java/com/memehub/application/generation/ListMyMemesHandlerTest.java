package com.memehub.application.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.memehub.application.port.out.MemeReadPort;
import com.memehub.application.port.out.MemeReadPort.MemeRow;
import com.memehub.application.port.out.ObjectStoragePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListMyMemesHandlerTest {

    private final MemeReadPort reads = mock(MemeReadPort.class);
    private final ObjectStoragePort storage = mock(ObjectStoragePort.class);
    private final ListMyMemesHandler handler = new ListMyMemesHandler(reads, storage);
    private final UUID owner = UUID.randomUUID();

    private static MemeRow row(String status) {
        return new MemeRow(UUID.randomUUID(), UUID.randomUUID(), "Drake", status, "memes/a.png",
                Map.of(1, "上班"), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void listsTheKeptMemesByDefaultAndTurnsImageKeysIntoLinks() {
        MemeRow kept = row("KEPT");
        when(reads.listByOwner(owner, "KEPT", 50)).thenReturn(List.of(kept));
        when(storage.presignedGetUrl(eq("memes/a.png"), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn("http://storage/a.png?sig=1");

        List<MemeSummary> result = handler.handle(owner, null, 50);

        assertThat(result).singleElement().satisfies(m -> {
            assertThat(m.id()).isEqualTo(kept.id());
            assertThat(m.imageUrl()).isEqualTo("http://storage/a.png?sig=1");
            assertThat(m.captions()).containsEntry(1, "上班");
            assertThat(m.templateName()).isEqualTo("Drake");
        });
    }

    @Test
    void acceptsAStatusInAnyCase() {
        when(reads.listByOwner(owner, "COMPOSED", 10)).thenReturn(List.of());

        assertThat(handler.handle(owner, " composed ", 10)).isEmpty();
        verify(reads).listByOwner(owner, "COMPOSED", 10);
    }

    @Test
    void rejectsAnUnknownStatusAndLimitsOutsideTheAllowedRange() {
        assertThatThrownBy(() -> handler.handle(owner, "DELETED", 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(owner, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> handler.handle(owner, null, ListMyMemesHandler.MAX_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class);
        verify(reads, never()).listByOwner(org.mockito.ArgumentMatchers.any(), anyString(), anyInt());
    }
}
