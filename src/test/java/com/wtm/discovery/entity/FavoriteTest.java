package com.wtm.discovery.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FavoriteTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Test
    void aFavoriteRemembersWhoKeptWhichMemeAndWhen() {
        UUID user = UUID.randomUUID();
        UUID meme = UUID.randomUUID();

        Favorite favorite = Favorite.make(user, meme, NOW);

        assertThat(favorite.userId()).isEqualTo(user);
        assertThat(favorite.templateId()).isEqualTo(meme);
        assertThat(favorite.createdAt()).isEqualTo(NOW);
    }

    @Test
    void theSameUserAndMemeAreTheSameFavoriteWheneverItWasMade() {
        UUID user = UUID.randomUUID();
        UUID meme = UUID.randomUUID();

        assertThat(Favorite.make(user, meme, NOW)).isEqualTo(Favorite.restore(user, meme, NOW.minusSeconds(60)));
        assertThat(Favorite.make(user, meme, NOW)).isNotEqualTo(Favorite.make(user, UUID.randomUUID(), NOW));
        assertThat(Favorite.make(user, meme, NOW)).isNotEqualTo(Favorite.make(UUID.randomUUID(), meme, NOW));
    }

    @Test
    void aFavoriteNeedsAUserAMemeAndATime() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> Favorite.make(null, id, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Favorite.make(id, null, NOW)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Favorite.make(id, id, null)).isInstanceOf(NullPointerException.class);
    }
}
