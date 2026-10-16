package com.wtm.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserIdTest {

    @Test
    void aUserIdWrapsTheUuidItWasMadeFrom() {
        UUID uuid = UUID.randomUUID();

        assertThat(new UserId(uuid).value()).isEqualTo(uuid);
        assertThat(new UserId(uuid)).isEqualTo(new UserId(uuid));
    }

    @Test
    void newIdsDiffer() {
        assertThat(UserId.newId()).isNotEqualTo(UserId.newId());
    }

    @Test
    void aUserIdNeedsAValue() {
        assertThatThrownBy(() -> new UserId(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aUserIsNotATemplateEvenWithTheSameUuid() {
        UUID uuid = UUID.randomUUID();

        assertThat((Object) new UserId(uuid)).isNotEqualTo(new TemplateId(uuid));
    }
}
