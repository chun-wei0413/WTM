package com.memehub.adapter.out.persistence;

import com.memehub.application.port.out.GenerationJobStore;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcGenerationJobStore implements GenerationJobStore {

    private final JdbcClient jdbc;

    JdbcGenerationJobStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(UUID jobId, UUID requesterId, String situation) {
        jdbc.sql("INSERT INTO generation_job (id, requester_id, situation, status) VALUES (?, ?, ?, 'PENDING')")
                .params(List.of(jobId, requesterId, situation))
                .update();
    }

    @Override
    public List<ClaimedJob> claim(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        // One statement: the sub-select locks the rows it picks and skips rows other workers hold.
        return jdbc.sql("""
                        UPDATE generation_job
                        SET status = 'RUNNING', started_at = now(), attempts = attempts + 1
                        WHERE id IN (
                            SELECT id FROM generation_job
                            WHERE status = 'PENDING'
                            ORDER BY created_at
                            LIMIT ?
                            FOR UPDATE SKIP LOCKED)
                        RETURNING id, requester_id, situation, attempts""")
                .param(limit)
                .query((rs, n) -> new ClaimedJob(rs.getObject("id", UUID.class),
                        rs.getObject("requester_id", UUID.class), rs.getString("situation"), rs.getInt("attempts")))
                .list();
    }

    @Override
    @Transactional
    public void complete(UUID jobId, List<UUID> memeIds) {
        int position = 1;
        for (UUID memeId : memeIds) {
            jdbc.sql("INSERT INTO generation_job_result (job_id, position, meme_id) VALUES (?, ?, ?)")
                    .params(List.of(jobId, position++, memeId))
                    .update();
        }
        jdbc.sql("UPDATE generation_job SET status = 'COMPLETED', finished_at = now() WHERE id = ?")
                .param(jobId)
                .update();
    }

    @Override
    public void fail(UUID jobId, String reason) {
        jdbc.sql("UPDATE generation_job SET status = 'FAILED', failure_reason = ?, finished_at = now() WHERE id = ?")
                .params(List.of(reason, jobId))
                .update();
    }

    @Override
    public int recoverStale(Duration runningLongerThan, int maxAttempts) {
        return jdbc.sql("""
                        UPDATE generation_job SET
                            status = CASE WHEN attempts >= ? THEN 'FAILED' ELSE 'PENDING' END,
                            failure_reason = CASE WHEN attempts >= ? THEN 'Gave up after repeated interruptions'
                                                  ELSE failure_reason END,
                            finished_at = CASE WHEN attempts >= ? THEN now() ELSE NULL END
                        WHERE status = 'RUNNING'
                          AND started_at < now() - make_interval(secs => ?)""")
                .params(List.of(maxAttempts, maxAttempts, maxAttempts, runningLongerThan.toMillis() / 1000.0))
                .update();
    }
}
