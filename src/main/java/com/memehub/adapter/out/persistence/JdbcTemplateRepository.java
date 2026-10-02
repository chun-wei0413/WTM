package com.memehub.adapter.out.persistence;

import com.memehub.application.port.out.TemplateRepository;
import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.MemeTemplate;
import com.memehub.domain.template.Slot;
import com.memehub.domain.template.TemplateId;
import com.memehub.domain.template.TemplateStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTemplateRepository implements TemplateRepository {

    private final JdbcClient jdbc;

    JdbcTemplateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Optional<MemeTemplate> findByIdForUpdate(TemplateId id) {
        Optional<Row> row = jdbc.sql(
                        "SELECT " + TemplateRows.PROFILE_AND_META_COLUMNS
                                + " FROM meme_template WHERE id = ? FOR UPDATE")
                .param(id.value())
                .query((rs, n) -> new Row(
                        rs.getString("name"), rs.getString("image_key"),
                        rs.getInt("image_width"), rs.getInt("image_height"),
                        TemplateStatus.valueOf(rs.getString("status")), rs.getInt("version"),
                        TemplateRows.profile(rs)))
                .optional();
        if (row.isEmpty()) {
            return Optional.empty();
        }
        List<Slot> slots = jdbc.sql("""
                        SELECT slot_no, role, max_chars, required, x, y, width, height
                        FROM template_slot WHERE template_id = ? ORDER BY slot_no""")
                .param(id.value())
                .query((rs, n) -> TemplateRows.slot(rs))
                .list();
        Row r = row.get();
        return Optional.of(MemeTemplate.restore(id, r.name, r.imageKey, r.imageWidth, r.imageHeight,
                r.status, r.version, r.profile, slots));
    }

    @Override
    @Transactional
    public void save(MemeTemplate template) {
        MemeProfile profile = template.profile();
        jdbc.sql("""
                        INSERT INTO meme_template
                            (id, name, image_key, image_width, image_height, status, version,
                             meaning, usage_examples, emotions, aliases, image_text, tags)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?, ?::text[])
                        ON CONFLICT (id) DO UPDATE SET
                            status = EXCLUDED.status,
                            version = EXCLUDED.version,
                            meaning = EXCLUDED.meaning,
                            usage_examples = EXCLUDED.usage_examples,
                            emotions = EXCLUDED.emotions,
                            aliases = EXCLUDED.aliases,
                            image_text = EXCLUDED.image_text,
                            tags = EXCLUDED.tags,
                            updated_at = now()""")
                .params(List.of(
                        template.id().value(), template.name(), template.imageKey(),
                        template.imageWidth(), template.imageHeight(),
                        template.status().name(), template.version(), profile.meaning(),
                        profile.usageExamples().toArray(String[]::new),
                        profile.emotions().toArray(String[]::new),
                        profile.aliases().toArray(String[]::new),
                        profile.imageText(),
                        profile.tags().toArray(String[]::new)))
                .update();

        jdbc.sql("DELETE FROM template_slot WHERE template_id = ?")
                .param(template.id().value())
                .update();
        for (Slot slot : template.slots()) {
            jdbc.sql("""
                            INSERT INTO template_slot
                                (template_id, slot_no, role, max_chars, required, x, y, width, height)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(List.of(template.id().value(), slot.slotNo(), slot.role(), slot.maxChars(),
                            slot.required(), slot.x(), slot.y(), slot.width(), slot.height()))
                    .update();
        }
    }

    private record Row(String name, String imageKey, int imageWidth, int imageHeight,
                       TemplateStatus status, int version, MemeProfile profile) {
    }
}
