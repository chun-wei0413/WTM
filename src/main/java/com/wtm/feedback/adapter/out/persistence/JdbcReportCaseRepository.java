package com.wtm.feedback.adapter.out.persistence;

import com.wtm.feedback.entity.Report;
import com.wtm.feedback.entity.Report.Closing;
import com.wtm.feedback.entity.ReportCase;
import com.wtm.feedback.entity.ReportReason;
import com.wtm.feedback.entity.Resolution;
import com.wtm.feedback.usecase.port.out.report.ReportCaseRepository;
import com.wtm.shared.UserId;
import com.wtm.shared.adapter.persistence.TemplateRows;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReportCaseRepository implements ReportCaseRepository {

    private final JdbcClient jdbc;

    JdbcReportCaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ReportCase get(UUID templateId) {
        List<Report> reports = jdbc.sql("""
                        SELECT id, user_id, reason, comment, created_at, status, resolution, resolved_by,
                               resolution_note, resolved_at
                        FROM meme_report WHERE template_id = ? ORDER BY created_at""")
                .param(templateId)
                .query((rs, n) -> toReport(rs))
                .list();
        return ReportCase.restore(templateId, reports);
    }

    @Override
    public ReportCase getForUpdate(UUID templateId) {
        // Held until the transaction ends; the same number for the same meme, whoever asks.
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")
                .param(templateId.toString())
                .query((rs, n) -> 1)
                .list();
        return get(templateId);
    }

    @Override
    public void save(ReportCase reportCase) {
        for (Report report : reportCase.reports()) {
            Closing closing = report.closing();
            jdbc.sql("""
                            INSERT INTO meme_report (id, template_id, user_id, reason, comment, status, resolution,
                                                     resolved_by, resolution_note, created_at, resolved_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT (id) DO UPDATE SET
                                reason = EXCLUDED.reason, comment = EXCLUDED.comment, status = EXCLUDED.status,
                                resolution = EXCLUDED.resolution, resolved_by = EXCLUDED.resolved_by,
                                resolution_note = EXCLUDED.resolution_note, created_at = EXCLUDED.created_at,
                                resolved_at = EXCLUDED.resolved_at""")
                    .params(Arrays.asList(report.id(), reportCase.templateId(), report.userId().value(),
                            report.reason().name(), report.comment(), closing == null ? "OPEN" : "RESOLVED",
                            closing == null ? null : closing.resolution().name(),
                            closing == null ? null : closing.automatic() ? "AUTO" : "ADMIN",
                            closing == null ? null : closing.note(),
                            Timestamp.from(report.createdAt()),
                            closing == null ? null : Timestamp.from(closing.at())))
                    .update();
        }
    }

    private static Report toReport(java.sql.ResultSet rs) throws java.sql.SQLException {
        Closing closing = null;
        if ("RESOLVED".equals(rs.getString("status"))) {
            closing = new Closing(Resolution.valueOf(rs.getString("resolution")),
                    "AUTO".equals(rs.getString("resolved_by")), rs.getString("resolution_note"),
                    TemplateRows.instant(rs, "resolved_at"));
        }
        return new Report(rs.getObject("id", UUID.class), new UserId(rs.getObject("user_id", UUID.class)),
                ReportReason.valueOf(rs.getString("reason")), rs.getString("comment"),
                TemplateRows.instant(rs, "created_at"), closing);
    }
}
