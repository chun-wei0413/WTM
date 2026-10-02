package com.wtm.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.port.out.ProfileHistoryPort;
import com.wtm.domain.template.MemeProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProfileHistoryAdapter implements ProfileHistoryPort {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcProfileHistoryAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void record(UUID templateId, MemeProfile before, MemeProfile after, Source source) {
        jdbc.sql("INSERT INTO profile_change (id, template_id, before, after, source) VALUES (?, ?, ?::jsonb, ?::jsonb, ?)")
                .params(List.of(UUID.randomUUID(), templateId, write(before), write(after), source.name()))
                .update();
    }

    @Override
    public Optional<Change> latestAutomaticChange(UUID templateId) {
        return jdbc.sql("""
                        SELECT id, before::text AS before, after::text AS after
                        FROM profile_change
                        WHERE template_id = ? AND source = 'AUTO' AND undone_at IS NULL
                        ORDER BY created_at DESC
                        LIMIT 1""")
                .param(templateId)
                .query((rs, n) -> new Change(rs.getObject("id", UUID.class), read(rs.getString("before")),
                        read(rs.getString("after"))))
                .optional();
    }

    @Override
    public void markUndone(UUID changeId) {
        jdbc.sql("UPDATE profile_change SET undone_at = now() WHERE id = ?").param(changeId).update();
    }

    private String write(MemeProfile profile) {
        try {
            return json.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The description could not be stored", e);
        }
    }

    private MemeProfile read(String text) {
        try {
            return json.readValue(text, MemeProfile.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The stored description is not valid", e);
        }
    }
}
