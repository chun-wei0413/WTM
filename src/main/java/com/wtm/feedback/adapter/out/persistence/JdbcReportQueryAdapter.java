package com.wtm.feedback.adapter.out.persistence;

import com.wtm.feedback.entity.ReportPolicy.Standing;
import com.wtm.feedback.entity.ReportReason;
import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.in.query.OpenReport;
import com.wtm.feedback.usecase.port.out.report.ReportViewPort;
import com.wtm.feedback.usecase.port.out.report.ReporterPort;
import com.wtm.shared.adapter.persistence.TemplateRows;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The reads that look across reports: what a person has reported so far, and the administrator's list.
 */
@Repository
class JdbcReportQueryAdapter implements ReporterPort, ReportViewPort {

    private final JdbcClient jdbc;

    JdbcReportQueryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        return jdbc.sql("""
                        SELECT r.id, r.template_id, r.user_id, u.username, r.reason, r.comment, r.created_at
                        FROM meme_report r
                        JOIN app_user u ON u.id = r.user_id
                        WHERE r.status = 'OPEN'
                        ORDER BY r.created_at""")
                .query((rs, n) -> new OpenReport(rs.getObject("id", UUID.class),
                        rs.getObject("template_id", UUID.class), rs.getObject("user_id", UUID.class),
                        rs.getString("username"), ReportReason.valueOf(rs.getString("reason")),
                        rs.getString("comment"), TemplateRows.instant(rs, "created_at")))
                .list();
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
}
