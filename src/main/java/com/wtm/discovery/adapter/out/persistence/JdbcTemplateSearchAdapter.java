package com.wtm.discovery.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.curation.adapter.out.persistence.TemplateRows;
import com.wtm.curation.entity.Slot;
import com.wtm.discovery.entity.Hit;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTemplateSearchAdapter implements TemplateSearchPort {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcTemplateSearchAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public List<Hit> byVector(float[] queryEmbedding, int limit) {
        String vector = VectorLiteral.of(queryEmbedding);
        return jdbc.sql("""
                        SELECT s.template_id, 1 - (s.embedding <=> ?::vector) AS similarity
                        FROM template_search s
                        JOIN meme_template t ON t.id = s.template_id
                        WHERE t.status = 'APPROVED'
                        ORDER BY s.embedding <=> ?::vector
                        LIMIT ?""")
                .params(List.of(vector, vector, limit))
                .query((rs, n) -> new Hit(rs.getObject("template_id", UUID.class), rs.getDouble("similarity")))
                .list();
    }

    @Override
    public List<IndexedText> searchTexts() {
        return jdbc.sql("""
                        SELECT s.template_id, s.search_text
                        FROM template_search s
                        JOIN meme_template t ON t.id = s.template_id
                        WHERE t.status = 'APPROVED'
                        ORDER BY s.template_id""")
                .query((rs, n) -> new IndexedText(rs.getObject("template_id", UUID.class), rs.getString("search_text")))
                .list();
    }

    @Override
    public String searchTextsVersion() {
        return jdbc.sql("""
                        SELECT count(*) || ':' || coalesce(max(s.indexed_at)::text, '') || ':' || coalesce(max(t.updated_at)::text, '')
                        FROM template_search s
                        JOIN meme_template t ON t.id = s.template_id
                        WHERE t.status = 'APPROVED'""")
                .query(String.class)
                .single();
    }

    @Override
    public List<SearchCard> cards(Collection<UUID> templateIds) {
        if (templateIds.isEmpty()) {
            return List.of();
        }
        String[] ids = templateIds.stream().map(UUID::toString).toArray(String[]::new);
        return jdbc.sql("""
                        SELECT t.id, t.name, t.image_key, t.image_width, t.image_height, s.slot_layout::text AS slot_layout,
                               t.meaning, t.usage_examples, t.emotions, t.tags, t.image_text, t.source_type, t.source_url,
                               t.attribution, t.reference_text, t.reference_source, t.reference_url, t.reference_license
                        FROM meme_template t
                        JOIN template_search s ON s.template_id = t.id
                        WHERE t.id = ANY(?::uuid[])""")
                .param(ids)
                .query((rs, n) -> new SearchCard(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("image_key"), rs.getInt("image_width"), rs.getInt("image_height"),
                        parseSlots(rs.getString("slot_layout")),
                        rs.getString("meaning"), TemplateRows.strings(rs, "usage_examples"),
                        TemplateRows.strings(rs, "emotions"), TemplateRows.strings(rs, "tags"), rs.getString("image_text"),
                        rs.getString("source_type"), rs.getString("source_url"), rs.getString("attribution"),
                        TemplateRows.reference(rs)))
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
