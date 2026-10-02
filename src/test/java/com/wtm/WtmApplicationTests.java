package com.wtm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class WtmApplicationTests extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void migrationsCreateSchemaAndEnableVector() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);
        assertThat(tables).contains("meme_template", "template_slot", "meme", "meme_caption",
                "template_search", "app_user");

        Integer vectorExt = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        assertThat(vectorExt).isEqualTo(1);
    }

    @Test
    void bootstrapAdminIsCreatedOnStartup() {
        Integer admins = jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE role = 'ADMIN'", Integer.class);
        assertThat(admins).isEqualTo(1);
    }
}
