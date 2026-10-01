package com.memehub.adapter.out.persistence;

import com.memehub.application.port.out.GenerationReadPort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcGenerationReadAdapter implements GenerationReadPort {

    private final JdbcClient jdbc;

    JdbcGenerationReadAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<JobSnapshot> find(UUID jobId) {
        Optional<JobSnapshot> job = jdbc.sql("""
                        SELECT id, requester_id, situation, status, failure_reason, created_at
                        FROM generation_job WHERE id = ?""")
                .param(jobId)
                .query((rs, n) -> new JobSnapshot(rs.getObject("id", UUID.class),
                        rs.getObject("requester_id", UUID.class), rs.getString("situation"),
                        rs.getString("status"), rs.getString("failure_reason"),
                        TemplateRows.instant(rs, "created_at"), List.of()))
                .optional();
        if (job.isEmpty()) {
            return Optional.empty();
        }

        Map<UUID, Map<Integer, String>> captions = new HashMap<>();
        jdbc.sql("""
                        SELECT c.meme_id, c.slot_no, c.text
                        FROM generation_job_result r
                        JOIN meme_caption c ON c.meme_id = r.meme_id
                        WHERE r.job_id = ?""")
                .param(jobId)
                .query(rs -> {
                    captions.computeIfAbsent(rs.getObject("meme_id", UUID.class), k -> new TreeMap<>())
                            .put(rs.getInt("slot_no"), rs.getString("text"));
                });

        List<CandidateRow> candidates = jdbc.sql("""
                        SELECT m.id, m.template_id, t.name, m.status, m.image_key
                        FROM generation_job_result r
                        JOIN meme m ON m.id = r.meme_id
                        JOIN meme_template t ON t.id = m.template_id
                        WHERE r.job_id = ?
                        ORDER BY r.position""")
                .param(jobId)
                .query((rs, n) -> {
                    UUID memeId = rs.getObject("id", UUID.class);
                    return new CandidateRow(memeId, rs.getObject("template_id", UUID.class), rs.getString("name"),
                            rs.getString("status"), rs.getString("image_key"),
                            captions.getOrDefault(memeId, Map.of()));
                })
                .list();

        JobSnapshot j = job.get();
        return Optional.of(new JobSnapshot(j.id(), j.requesterId(), j.situation(), j.status(),
                j.failureReason(), j.createdAt(), candidates));
    }
}
