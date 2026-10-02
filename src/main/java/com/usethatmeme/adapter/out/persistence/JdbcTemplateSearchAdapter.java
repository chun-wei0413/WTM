package com.usethatmeme.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usethatmeme.application.port.out.TemplateSearchPort;
import com.usethatmeme.domain.template.Slot;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTemplateSearchAdapter implements TemplateSearchPort {

    /** Minimum trigram word similarity for a keyword hit (0..1). */
    private static final double KEYWORD_THRESHOLD = 0.3;

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcTemplateSearchAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<UUID> byVector(float[] queryEmbedding, int limit) {
        return jdbc.sql("""
                        SELECT s.template_id
                        FROM template_search s
                        JOIN meme_template t ON t.id = s.template_id
                        WHERE t.status = 'APPROVED'
                        ORDER BY s.embedding <=> ?::vector
                        LIMIT ?""")
                .params(List.of(VectorLiteral.of(queryEmbedding), limit))
                .query(UUID.class)
                .list();
    }

    @Override
    public List<UUID> byKeyword(String query, int limit) {
        return jdbc.sql("""
                        SELECT s.template_id
                        FROM template_search s
                        JOIN meme_template t ON t.id = s.template_id
                        WHERE t.status = 'APPROVED'
                          AND word_similarity(?, s.search_text) > ?
                        ORDER BY word_similarity(?, s.search_text) DESC
                        LIMIT ?""")
                .params(List.of(query, KEYWORD_THRESHOLD, query, limit))
                .query(UUID.class)
                .list();
    }

    @Override
    public List<SearchCard> cards(Collection<UUID> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        String[] ids = templateIds.stream().map(UUID::toString).toArray(String[]::new);
        return jdbc.sql("""
                        SELECT t.id, t.name, t.image_key, s.slot_layout::text AS slot_layout,
                               t.meaning, t.tags, t.image_text, t.source_type, t.source_url, t.attribution
                        FROM meme_template t
                        JOIN template_search s ON s.template_id = t.id
                        WHERE t.id = ANY(?::uuid[])""")
                .param(ids)
                .query((rs, n) -> new SearchCard(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("image_key"), parseSlots(rs.getString("slot_layout")),
                        rs.getString("meaning"), TemplateRows.strings(rs, "tags"), rs.getString("image_text"),
                        rs.getString("source_type"), rs.getString("source_url"), rs.getString("attribution")))
                .list();
    }

    private List<Slot> parseSlots(String text) {
        try {
            return json.readValue(text, new TypeReference<List<Slot>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored slot layout is not valid", e);
        }
    }
}
