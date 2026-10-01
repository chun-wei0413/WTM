package com.memehub.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsCheckTest {

    private static MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("memehub.storage.access-key", "access")
                .withProperty("memehub.storage.secret-key", "storage-secret")
                .withProperty("memehub.security.jwt.secret", "jwt-secret")
                .withProperty("memehub.security.bootstrap-admin.password", "admin-secret");
    }

    @Test
    void acceptsAnEnvironmentWhereEverythingIsSet() {
        assertThat(RequiredSettingsCheck.problems(complete())).isEmpty();
    }

    @Test
    void reportsASettingThatIsMissing() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("spring.datasource.password", "db-secret");

        assertThat(RequiredSettingsCheck.problems(env))
                .anyMatch(p -> p.contains("MEMEHUB_JWT_SECRET"))
                .anyMatch(p -> p.contains("MEMEHUB_ADMIN_PASSWORD"))
                .noneMatch(p -> p.contains("DB_PASSWORD"));
    }

    @Test
    void reportsAPlaceholderThatCouldNotBeResolved() {
        MockEnvironment env = complete()
                .withProperty("memehub.security.bootstrap-admin.password", "${MEMEHUB_ADMIN_PASSWORD}");

        assertThat(RequiredSettingsCheck.problems(env)).containsExactly(
                "MEMEHUB_ADMIN_PASSWORD (memehub.security.bootstrap-admin.password)");
    }

    @Test
    void acceptsAPlaceholderThatDoesResolve() {
        MockEnvironment env = complete()
                .withProperty("memehub.security.bootstrap-admin.password", "${REAL_ADMIN_PASSWORD}")
                .withProperty("REAL_ADMIN_PASSWORD", "from-environment");

        assertThat(RequiredSettingsCheck.problems(env)).isEmpty();
    }

    @Test
    void rejectsBlankValuesAndTheUnchangedExampleValue() {
        MockEnvironment env = complete()
                .withProperty("spring.datasource.password", "   ")
                .withProperty("memehub.security.jwt.secret", RequiredSettingsCheck.EXAMPLE_VALUE);

        assertThat(RequiredSettingsCheck.problems(env)).hasSize(2)
                .anyMatch(p -> p.contains("DB_PASSWORD"))
                .anyMatch(p -> p.contains("MEMEHUB_JWT_SECRET"));
    }
}
