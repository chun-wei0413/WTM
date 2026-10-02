package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.ReportPort;
import com.wtm.application.report.ReportPolicy.Standing;
import com.wtm.application.report.ReportReason;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReportAdapter implements ReportPort {

    private static final String OPEN_REPORTS = """
            SELECT r.id, r.template_id, r.user_id, u.username, r.reason, r.comment, r.created_at
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
    public int reportedSince(UUID userId, Instant since, UUID except) {
        // Changing a report moves its time forward, so editing one counts as reporting it again today.
        return jdbc.sql("SELECT count(DISTINCT template_id) FROM meme_report WHERE user_id = ? AND created_at >= ? AND template_id <> ?")
                .params(List.of(userId, Timestamp.from(since), except))
                .query(Integer.class)
                .single();
    }

    @Override
    public Standing standingOf(UUID userId) {
        return jdbc.sql("""
                        SELECT (SELECT role = 'ADMIN' FROM app_user WHERE id = :user) AS administrator,
                               count(*) FILTER (WHERE resolution = 'APPLIED') AS adopted,
                               count(*) FILTER (WHERE resolution = 'DISMISSED') AS set_aside
                        FROM meme_report WHERE user_id = :user AND status = 'RESOLVED'""")
                .param("user", userId)
                .query((rs, n) -> new Standing(rs.getBoolean("administrator"), rs.getInt("adopted"),
                        rs.getInt("set_aside")))
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
    public void resolve(UUID templateId, Resolution resolution, boolean automatic, String note) {
        jdbc.sql("""
                        UPDATE meme_report
                        SET status = 'RESOLVED', resolution = ?, resolved_by = ?, resolution_note = ?, resolved_at = now()
                        WHERE template_id = ? AND status = 'OPEN'""")
                .params(java.util.Arrays.asList(resolution.name(), automatic ? "AUTO" : "ADMIN", note, templateId))
                .update();
    }

    @Override
    public int reopenAutomaticallyDismissed(UUID templateId, Instant closedSince) {
        return jdbc.sql("""
                        UPDATE meme_report r
                        SET status = 'OPEN', resolution = NULL, resolved_by = NULL, resolution_note = NULL,
                            resolved_at = NULL
                        WHERE r.template_id = ? AND r.status = 'RESOLVED' AND r.resolution = 'DISMISSED'
                          AND r.resolved_by = 'AUTO' AND r.resolved_at >= ?
                          AND NOT EXISTS (SELECT 1 FROM meme_report o
                                          WHERE o.user_id = r.user_id AND o.template_id = r.template_id
                                            AND o.status = 'OPEN')""")
                .params(List.of(templateId, Timestamp.from(closedSince)))
                .update();
    }

    @Override
    public List<AutomaticAction> automaticSince(Instant since) {
        return jdbc.sql("""
                        SELECT template_id, resolution, count(*) AS reports, max(resolved_at) AS at,
                               max(resolution_note) AS note
                        FROM meme_report
                        WHERE resolved_by = 'AUTO' AND resolved_at >= ?
                        GROUP BY template_id, resolution
                        ORDER BY max(resolved_at) DESC""")
                .param(Timestamp.from(since))
                .query((rs, n) -> new AutomaticAction(rs.getObject("template_id", UUID.class),
                        Resolution.valueOf(rs.getString("resolution")), rs.getInt("reports"), rs.getString("note"),
                        TemplateRows.instant(rs, "at")))
                .list();
    }

    private static OpenReport toReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OpenReport(rs.getObject("id", UUID.class), rs.getObject("template_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("username"),
                ReportReason.valueOf(rs.getString("reason")), rs.getString("comment"),
                TemplateRows.instant(rs, "created_at"));
    }
}
