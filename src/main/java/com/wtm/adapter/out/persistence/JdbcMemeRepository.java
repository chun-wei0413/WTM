package com.wtm.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.port.out.MemeRepository;
import com.wtm.domain.meme.Meme;
import com.wtm.domain.meme.MemeId;
import com.wtm.domain.meme.MemeStatus;
import com.wtm.domain.meme.TemplateRef;
import com.wtm.domain.template.Slot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcMemeRepository implements MemeRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcMemeRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Meme> findById(MemeId id) {
        Optional<Meme> meme = jdbc.sql("""
                        SELECT id, owner_id, template_id, template_version, slot_snapshot::text AS slot_snapshot,
                               status, image_key
                        FROM meme WHERE id = ?""")
                .param(id.value())
                .query((rs, n) -> Meme.restore(new MemeId(rs.getObject("id", UUID.class)),
                        rs.getObject("owner_id", UUID.class),
                        new TemplateRef(rs.getObject("template_id", UUID.class), rs.getInt("template_version"),
                                parseSlots(rs.getString("slot_snapshot"))),
                        Map.of(), MemeStatus.valueOf(rs.getString("status")), rs.getString("image_key")))
                .optional();
        if (meme.isEmpty()) {
            return Optional.empty();
        }
        Map<Integer, String> captions = new TreeMap<>();
        jdbc.sql("SELECT slot_no, text FROM meme_caption WHERE meme_id = ?")
                .param(id.value())
                .query(rs -> {
                    captions.put(rs.getInt("slot_no"), rs.getString("text"));
                });
        Meme m = meme.get();
        return Optional.of(Meme.restore(m.id(), m.ownerId(), m.template(), captions, m.status(), m.imageKey()));
    }

    @Override
    @Transactional
    public void save(Meme meme) {
        jdbc.sql("""
                        INSERT INTO meme (id, owner_id, template_id, template_version, slot_snapshot, status, image_key)
                        VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, image_key = EXCLUDED.image_key""")
                .params(java.util.Arrays.asList(meme.id().value(), meme.ownerId(), meme.template().templateId(),
                        meme.template().version(), toJson(meme.template().slots()), meme.status().name(),
                        meme.imageKey()))
                .update();
        // Captions never change after a meme is composed, so writing them once is enough.
        meme.captions().forEach((slotNo, text) -> jdbc.sql("""
                        INSERT INTO meme_caption (meme_id, slot_no, text) VALUES (?, ?, ?)
                        ON CONFLICT (meme_id, slot_no) DO NOTHING""")
                .params(List.of(meme.id().value(), slotNo, text))
                .update());
    }

    private String toJson(List<Slot> slots) {
        try {
            return json.writeValueAsString(slots);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize slot snapshot", e);
        }
    }

    private List<Slot> parseSlots(String text) {
        try {
            return json.readValue(text, new TypeReference<List<Slot>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored slot snapshot is not valid", e);
        }
    }
}
