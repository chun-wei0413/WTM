package com.usethatmeme.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.application.port.out.SearchIndexPort;
import com.usethatmeme.domain.template.MemeProfile;
import com.usethatmeme.domain.template.Slot;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSearchIndexAdapter implements SearchIndexPort {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcSearchIndexAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<UUID> findTemplatesNeedingIndex(int limit) {
        return jdbc.sql("""
                        SELECT t.id
                        FROM meme_template t
                        LEFT JOIN template_search s ON s.template_id = t.id
                        WHERE t.status = 'APPROVED'
                          AND (s.template_id IS NULL OR s.source_updated_at <> t.updated_at)
                        ORDER BY t.updated_at
                        LIMIT ?""")
                .param(limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public List<UUID> findIndexEntriesToRemove(int limit) {
        return jdbc.sql("""
                        SELECT s.template_id
                        FROM template_search s
                        LEFT JOIN meme_template t ON t.id = s.template_id
                        WHERE t.id IS NULL OR t.status <> 'APPROVED'
                        LIMIT ?""")
                .param(limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public Optional<IndexableTemplate> load(UUID templateId) {
        Optional<IndexableTemplate> header = jdbc.sql(
                        "SELECT " + TemplateRows.PROFILE_AND_META_COLUMNS + " FROM meme_template WHERE id = ?")
                .param(templateId)
                .query((rs, n) -> new IndexableTemplate(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                        TemplateRows.profile(rs), List.of(), TemplateRows.instant(rs, "updated_at")))
                .optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<Slot> slots = jdbc.sql("""
                        SELECT slot_no, role, max_chars, required, x, y, width, height
                        FROM template_slot WHERE template_id = ? ORDER BY slot_no""")
                .param(templateId)
                .query((rs, n) -> TemplateRows.slot(rs))
                .list();
        IndexableTemplate h = header.get();
        MemeProfile profile = h.profile();
        return Optional.of(new IndexableTemplate(h.id(), h.name(), h.status(), profile, slots, h.updatedAt()));
    }

    @Override
    public void upsert(UUID templateId, String searchText, float[] embedding,
                       List<Slot> slotLayout, Instant sourceUpdatedAt) {
        jdbc.sql("""
                        INSERT INTO template_search
                            (template_id, search_text, embedding, slot_layout, source_updated_at, indexed_at)
                        VALUES (?, ?, ?::vector, ?::jsonb, ?, now())
                        ON CONFLICT (template_id) DO UPDATE SET
                            search_text = EXCLUDED.search_text,
                            embedding = EXCLUDED.embedding,
                            slot_layout = EXCLUDED.slot_layout,
                            source_updated_at = EXCLUDED.source_updated_at,
                            indexed_at = now()""")
                .params(List.of(templateId, searchText, VectorLiteral.of(embedding), toJson(slotLayout),
                        OffsetDateTime.ofInstant(sourceUpdatedAt, ZoneOffset.UTC)))
                .update();
    }

    @Override
    public void remove(UUID templateId) {
        jdbc.sql("DELETE FROM template_search WHERE template_id = ?").param(templateId).update();
    }

    private String toJson(List<Slot> slots) {
        try {
            return json.writeValueAsString(slots);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize slot layout", e);
        }
    }
}
