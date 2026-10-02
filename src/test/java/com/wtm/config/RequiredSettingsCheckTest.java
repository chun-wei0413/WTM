package com.wtm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RequiredSettingsCheckTest {

    private static MockEnvironment complete() {
        return new MockEnvironment()
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("wtm.storage.access-key", "access")
                .withProperty("wtm.storage.secret-key", "storage-secret")
                .withProperty("wtm.security.jwt.secret", "jwt-secret")
                .withProperty("wtm.security.bootstrap-admin.password", "admin-secret");
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
                .anyMatch(p -> p.contains("WTM_JWT_SECRET"))
                .anyMatch(p -> p.contains("WTM_ADMIN_PASSWORD"))
                .noneMatch(p -> p.contains("DB_PASSWORD"));
    }

    @Test
    void reportsAPlaceholderThatCouldNotBeResolved() {
        MockEnvironment env = complete()
                .withProperty("wtm.security.bootstrap-admin.password", "${WTM_ADMIN_PASSWORD}");

        assertThat(RequiredSettingsCheck.problems(env)).containsExactly(
                "WTM_ADMIN_PASSWORD (wtm.security.bootstrap-admin.password)");
    }

    @Test
    void acceptsAPlaceholderThatDoesResolve() {
        MockEnvironment env = complete()
                .withProperty("wtm.security.bootstrap-admin.password", "${REAL_ADMIN_PASSWORD}")
                .withProperty("REAL_ADMIN_PASSWORD", "from-environment");

        assertThat(RequiredSettingsCheck.problems(env)).isEmpty();
    }

    @Test
    void rejectsBlankValuesAndTheUnchangedExampleValue() {
        MockEnvironment env = complete()
                .withProperty("spring.datasource.password", "   ")
                .withProperty("wtm.security.jwt.secret", RequiredSettingsCheck.EXAMPLE_VALUE);

        assertThat(RequiredSettingsCheck.problems(env)).hasSize(2)
                .anyMatch(p -> p.contains("DB_PASSWORD"))
                .anyMatch(p -> p.contains("WTM_JWT_SECRET"));
    }
}
