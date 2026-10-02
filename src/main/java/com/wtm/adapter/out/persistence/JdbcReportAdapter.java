package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.ReportPort;
import com.wtm.application.report.ReportReason;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReportAdapter implements ReportPort {

    private static final String OPEN_REPORTS = """
            SELECT r.id, r.template_id, u.username, r.reason, r.comment, r.created_at
            FROM meme_report r
            JOIN app_user u ON u.id = r.user_id
            WHERE r.status = 'OPEN'""";

    private final JdbcClient jdbc;

    JdbcReportAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean submit(UUID userId, UUID templateId, ReportReason reason, String comment) {
        // One statement: only a published meme can be reported, and a second report from the same person
        // replaces the first (the partial unique index names the open ones).
        return jdbc.sql("""
                        WITH published AS (
                            SELECT id FROM meme_template WHERE id = ? AND status = 'APPROVED'
                        ), saved AS (
                            INSERT INTO meme_report (id, template_id, user_id, reason, comment)
                            SELECT ?, id, ?, ?, ? FROM published
                            ON CONFLICT (user_id, template_id) WHERE status = 'OPEN'
                            DO UPDATE SET reason = EXCLUDED.reason, comment = EXCLUDED.comment, created_at = now()
                        )
                        SELECT EXISTS (SELECT 1 FROM published)""")
                .params(List.of(templateId, UUID.randomUUID(), userId, reason.name(), comment))
                .query(Boolean.class)
                .single();
    }

    @Override
    public int openCountOf(UUID userId) {
        return jdbc.sql("SELECT count(*) FROM meme_report WHERE user_id = ? AND status = 'OPEN'")
                .param(userId)
                .query(Integer.class)
                .single();
    }

    @Override
    public List<OpenReport> open() {
        return jdbc.sql(OPEN_REPORTS + " ORDER BY r.created_at")
                .query((rs, n) -> toReport(rs))
                .list();
    }

    @Override
    public List<OpenReport> openAbout(UUID templateId) {
        return jdbc.sql(OPEN_REPORTS + " AND r.template_id = ? ORDER BY r.created_at")
                .param(templateId)
                .query((rs, n) -> toReport(rs))
                .list();
    }

    @Override
    public void resolve(UUID templateId, Resolution resolution) {
        jdbc.sql("""
                        UPDATE meme_report SET status = 'RESOLVED', resolution = ?, resolved_at = now()
                        WHERE template_id = ? AND status = 'OPEN'""")
                .params(List.of(resolution.name(), templateId))
                .update();
    }

    private static OpenReport toReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OpenReport(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getString("username"), ReportReason.valueOf(rs.getString("reason")), rs.getString("comment"),
                TemplateRows.instant(rs, "created_at"));
    }
}
