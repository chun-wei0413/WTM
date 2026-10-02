package com.wtm.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wtm.application.port.out.ReviewPort;
import com.wtm.application.report.Suggestion;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReviewAdapter implements ReviewPort {

    private final JdbcClient jdbc;
    private final ObjectMapper json;

    JdbcReviewAdapter(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void request(UUID templateId, boolean forced) {
        // A review that is waiting or running is left alone (except that an administrator's request upgrades
        // a waiting one so it skips the budget); a finished or failed one starts over, and the earlier
        // proposal stays until the new one replaces it.
        jdbc.sql("""
                        INSERT INTO report_review (template_id, status, forced) VALUES (?, 'PENDING', ?)
                        ON CONFLICT (template_id) DO UPDATE
                            SET status = 'PENDING', attempts = 0, last_error = NULL, forced = EXCLUDED.forced,
                                created_at = now(), started_at = NULL, finished_at = NULL
                            WHERE report_review.status IN ('DONE', 'FAILED')""")
                .params(List.of(templateId, forced))
                .update();
        if (forced) {
            jdbc.sql("UPDATE report_review SET forced = TRUE WHERE template_id = ? AND status = 'PENDING'")
                    .param(templateId)
                    .update();
        }
    }

    @Override
    public Optional<Instant> lastRunAt(UUID templateId) {
        return jdbc.sql("SELECT max(ran_at) FROM review_run WHERE template_id = ?")
                .param(templateId)
                .query((rs, n) -> rs.getTimestamp(1) == null ? null : rs.getTimestamp(1).toInstant())
                .optional();
    }

    @Override
    public List<UUID> claim(int limit, int dailyBudget) {
        if (limit <= 0) {
            return List.of();
        }
        // One statement picks the waiting memes (only while the day's budget lasts, unless an administrator
        // asked), marks them running and writes down that the model is about to be used.
        return jdbc.sql("""
                        WITH picked AS (
                            SELECT template_id FROM report_review
                            WHERE status = 'PENDING'
                              AND (forced OR (SELECT count(*) FROM review_run
                                              WHERE ran_at > now() - interval '24 hours') < ?)
                            ORDER BY forced DESC, created_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED
                        ), started AS (
                            UPDATE report_review
                            SET status = 'RUNNING', started_at = now(), attempts = attempts + 1
                            WHERE template_id IN (SELECT template_id FROM picked)
                            RETURNING template_id
                        ), logged AS (
                            INSERT INTO review_run (template_id) SELECT template_id FROM started
                        )
                        SELECT template_id FROM started""")
                .params(List.of(dailyBudget, limit))
                .query(UUID.class)
                .list();
    }

    @Override
    public void complete(UUID templateId, Suggestion suggestion) {
        jdbc.sql("""
                        UPDATE report_review
                        SET status = 'DONE', proposal = ?::jsonb, last_error = NULL, finished_at = now()
                        WHERE template_id = ?""")
                .params(List.of(write(suggestion), templateId))
                .update();
    }

    @Override
    public void fail(UUID templateId, String error, int maxAttempts) {
        jdbc.sql("""
                        UPDATE report_review SET
                            status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            last_error = ?,
                            finished_at = CASE WHEN attempts >= ? THEN now() ELSE NULL END
                        WHERE template_id = ?""")
                .params(List.of(maxAttempts, error, maxAttempts, templateId))
                .update();
    }

    @Override
    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return jdbc.sql("""
                        UPDATE report_review SET
                            status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            last_error = CASE WHEN attempts >= ? THEN 'Gave up after repeated interruptions'
                                              ELSE last_error END,
                            finished_at = CASE WHEN attempts >= ? THEN now() ELSE NULL END
                        WHERE status = 'RUNNING'
                          AND started_at < now() - make_interval(secs => ?)""")
                .params(List.of(maxAttempts, maxAttempts, maxAttempts, runningLongerThan.toMillis() / 1000.0))
                .update();
    }

    @Override
    public Optional<ReviewState> find(UUID templateId) {
        return jdbc.sql("SELECT status, proposal::text AS proposal, last_error, forced FROM report_review WHERE template_id = ?")
                .param(templateId)
                .query((rs, n) -> new ReviewState(rs.getString("status"), read(rs.getString("proposal")),
                        rs.getString("last_error"), rs.getBoolean("forced")))
                .optional();
    }

    @Override
    public void delete(UUID templateId) {
        jdbc.sql("DELETE FROM report_review WHERE template_id = ?").param(templateId).update();
    }

    private String write(Suggestion suggestion) {
        try {
            return json.writeValueAsString(suggestion);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The proposal could not be stored", e);
        }
    }

    private Suggestion read(String text) {
        if (text == null) {
            return null;
        }
        try {
            return json.readValue(text, Suggestion.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The stored proposal is not valid", e);
        }
    }
}
