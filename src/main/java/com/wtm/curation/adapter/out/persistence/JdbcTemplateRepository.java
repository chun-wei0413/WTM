package com.wtm.curation.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.curation.entity.ImageInfo;
import com.wtm.curation.entity.MemeTemplate;
import com.wtm.curation.entity.ProfileChange;
import com.wtm.curation.entity.TemplateStatus;
import com.wtm.curation.usecase.port.out.template.TemplateRepository;
import com.wtm.shared.MemeProfile;
import com.wtm.shared.Slot;
import com.wtm.shared.TemplateId;
import com.wtm.shared.adapter.persistence.TemplateRows;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcTemplateRepository implements TemplateRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcTemplateRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    @Transactional
    public Optional<MemeTemplate> findById(TemplateId id) {
        return load(id, "");
    }

    @Override
    @Transactional
    public Optional<MemeTemplate> findByIdForUpdate(TemplateId id) {
        return load(id, " FOR UPDATE");
    }

    private Optional<MemeTemplate> load(TemplateId id, String lock) {
        Optional<Row> row = jdbc.sql(
                        "SELECT " + TemplateRows.PROFILE_AND_META_COLUMNS
                                + ", content_sha256, phash FROM meme_template WHERE id = ?" + lock)
                .param(id.value())
                .query((rs, n) -> new Row(
                        rs.getString("name"), new ImageInfo(rs.getString("image_key"),
                                rs.getInt("image_width"), rs.getInt("image_height"),
                                rs.getString("content_sha256"), rs.getObject("phash", Long.class)),
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
        List<ProfileChange> changes = jdbc.sql("""
                        SELECT id, before::text AS before, after::text AS after, source, created_at
                        FROM profile_change
                        WHERE template_id = ? AND undone_at IS NULL
                        ORDER BY created_at""")
                .param(id.value())
                .query((rs, n) -> new ProfileChange(rs.getObject("id", UUID.class), read(rs.getString("before")),
                        read(rs.getString("after")), ProfileChange.Source.valueOf(rs.getString("source")),
                        TemplateRows.instant(rs, "created_at"), null))
                .list();
        Row r = row.get();
        return Optional.of(MemeTemplate.restore(id, r.name, r.image,
                r.status, r.version, r.profile, slots, changes));
    }

    @Override
    @Transactional
    public void save(MemeTemplate template) {
        MemeProfile profile = template.profile();
        jdbc.sql("""
                        INSERT INTO meme_template
                            (id, name, image_key, image_width, image_height, status, version,
                             meaning, usage_examples, emotions, aliases, image_text, tags, content_sha256, phash)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[], ?, ?::text[], ?, ?)
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
                .params(Arrays.asList(
                        template.id().value(), template.name(), template.imageKey(),
                        template.imageWidth(), template.imageHeight(),
                        template.status().name(), template.version(), profile.meaning(),
                        profile.usageExamples().toArray(String[]::new),
                        profile.emotions().toArray(String[]::new),
                        profile.aliases().toArray(String[]::new),
                        profile.imageText(),
                        profile.tags().toArray(String[]::new),
                        template.image().sha256(), template.image().perceptualHash()))
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
        saveChanges(template);
    }

    private void saveChanges(MemeTemplate template) {
        for (ProfileChange change : template.profileChanges()) {
            jdbc.sql("""
                            INSERT INTO profile_change (id, template_id, before, after, source, created_at, undone_at)
                            VALUES (?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                            ON CONFLICT (id) DO UPDATE SET undone_at = EXCLUDED.undone_at""")
                    .params(Arrays.asList(change.id(), template.id().value(), write(change.before()),
                            write(change.after()), change.source().name(), Timestamp.from(change.createdAt()),
                            change.undoneAt() == null ? null : Timestamp.from(change.undoneAt())))
                    .update();
        }
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

    private record Row(String name, ImageInfo image,
                       TemplateStatus status, int version, MemeProfile profile) {
    }
}
