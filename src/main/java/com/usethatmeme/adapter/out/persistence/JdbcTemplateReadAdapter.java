package com.usethatmeme.adapter.out.persistence;

import com.usethatmeme.application.port.out.TemplateReadPort;
import com.usethatmeme.application.template.query.TemplateSummary;
import com.usethatmeme.application.template.query.TemplateView;
import com.usethatmeme.domain.template.Slot;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTemplateReadAdapter implements TemplateReadPort {

    private final JdbcClient jdbc;

    JdbcTemplateReadAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TemplateView> findById(UUID id) {
        Optional<TemplateView> view = jdbc.sql(
                        "SELECT " + TemplateRows.PROFILE_AND_META_COLUMNS + " FROM meme_template WHERE id = ?")
                .param(id)
                .query((rs, n) -> new TemplateView(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                        rs.getInt("version"), rs.getInt("image_width"), rs.getInt("image_height"),
                        rs.getString("image_key"), null, TemplateRows.profile(rs), List.of(),
                        TemplateRows.instant(rs, "created_at"), TemplateRows.instant(rs, "updated_at")))
                .optional();
        if (view.isEmpty()) {
            return Optional.empty();
        }
        List<Slot> slots = jdbc.sql("""
                        SELECT slot_no, role, max_chars, required, x, y, width, height
                        FROM template_slot WHERE template_id = ? ORDER BY slot_no""")
                .param(id)
                .query((rs, n) -> TemplateRows.slot(rs))
                .list();
        TemplateView v = view.get();
        return Optional.of(new TemplateView(v.id(), v.name(), v.status(), v.version(),
                v.imageWidth(), v.imageHeight(), v.imageKey(), null, v.profile(), slots,
                v.createdAt(), v.updatedAt()));
    }

    @Override
    public List<TemplateSummary> list(String status) {
        return jdbc.sql("""
                        SELECT id, name, status, version, image_key, updated_at
                        FROM meme_template
                        WHERE (?::text IS NULL OR status = ?)
                        ORDER BY updated_at DESC""")
                .params(Arrays.asList(status, status))
                .query((rs, n) -> new TemplateSummary(
                        rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("status"),
                        rs.getInt("version"), rs.getString("image_key"), null,
                        TemplateRows.instant(rs, "updated_at")))
                .list();
    }
}
