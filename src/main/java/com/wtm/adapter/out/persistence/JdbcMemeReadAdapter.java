package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.MemeReadPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMemeReadAdapter implements MemeReadPort {

    private final JdbcClient jdbc;

    JdbcMemeReadAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<MemeRow> listByOwner(UUID ownerId, String status, int limit) {
        List<MemeRow> rows = jdbc.sql("""
                        SELECT m.id, m.template_id, t.name, m.status, m.image_key, m.created_at
                        FROM meme m
                        JOIN meme_template t ON t.id = m.template_id
                        WHERE m.owner_id = ? AND m.status = ? AND m.image_key IS NOT NULL
                        ORDER BY m.created_at DESC
                        LIMIT ?""")
                .params(List.of(ownerId, status, limit))
                .query((rs, n) -> new MemeRow(rs.getObject("id", UUID.class),
                        rs.getObject("template_id", UUID.class), rs.getString("name"), rs.getString("status"),
                        rs.getString("image_key"), Map.of(), TemplateRows.instant(rs, "created_at")))
                .list();
        if (rows.isEmpty()) {
            return rows;
        }

        Map<UUID, Map<Integer, String>> captions = new HashMap<>();
        String[] ids = rows.stream().map(r -> r.id().toString()).toArray(String[]::new);
        jdbc.sql("SELECT meme_id, slot_no, text FROM meme_caption WHERE meme_id = ANY(?::uuid[])")
                .param(ids)
                .query(rs -> {
                    captions.computeIfAbsent(rs.getObject("meme_id", UUID.class), k -> new TreeMap<>())
                            .put(rs.getInt("slot_no"), rs.getString("text"));
                });
        return rows.stream()
                .map(r -> new MemeRow(r.id(), r.templateId(), r.templateName(), r.status(), r.imageKey(),
                        captions.getOrDefault(r.id(), Map.of()), r.createdAt()))
                .toList();
    }
}
