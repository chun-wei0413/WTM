package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.TaggingQueuePort;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTaggingQueue implements TaggingQueuePort {

    private final JdbcClient jdbc;

    JdbcTaggingQueue(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> claim(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbc.sql("""
                        UPDATE template_tagging
                        SET status = 'RUNNING', started_at = now(), attempts = attempts + 1
                        WHERE template_id IN (
                            SELECT template_id FROM template_tagging
                            WHERE status = 'PENDING'
                            ORDER BY created_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED)
                        RETURNING template_id""")
                .param(limit)
                .query(UUID.class)
                .list();
    }

    @Override
    public void done(UUID templateId) {
        jdbc.sql("UPDATE template_tagging SET status = 'DONE', last_error = NULL, finished_at = now() WHERE template_id = ?")
                .param(templateId)
                .update();
    }

    @Override
    public void fail(UUID templateId, String error, int maxAttempts) {
        jdbc.sql("""
                        UPDATE template_tagging SET
                            status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            last_error = ?,
                            finished_at = CASE WHEN attempts >= ? THEN now() ELSE NULL END
                        WHERE template_id = ?""")
                .params(List.of(maxAttempts, error, maxAttempts, templateId))
                .update();
    }

    @Override
    public int recoverStale(Duration beingTaggedLongerThan, int maxAttempts) {
        return jdbc.sql("""
                        UPDATE template_tagging SET
                            status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            last_error = CASE WHEN attempts >= ? THEN 'Gave up after repeated interruptions'
                                              ELSE last_error END,
                            finished_at = CASE WHEN attempts >= ? THEN now() ELSE NULL END
                        WHERE status = 'RUNNING'
                          AND started_at < now() - make_interval(secs => ?)""")
                .params(List.of(maxAttempts, maxAttempts, maxAttempts, beingTaggedLongerThan.toMillis() / 1000.0))
                .update();
    }
}
