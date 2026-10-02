package com.usethatmeme.adapter.out.persistence;

import com.usethatmeme.application.port.out.CollectionRunPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCollectionRunStore implements CollectionRunPort {

    private final JdbcClient jdbc;

    JdbcCollectionRunStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(UUID id, String source, String options) {
        jdbc.sql("INSERT INTO collection_run (id, source, options, status) VALUES (?, ?, ?, 'RUNNING')")
                .params(List.of(id, source, options))
                .update();
    }

    @Override
    public boolean anyRunning() {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM collection_run WHERE status = 'RUNNING')")
                .query(Boolean.class)
                .single();
    }

    @Override
    public void update(UUID id, Counts c) {
        jdbc.sql("UPDATE collection_run SET found = ?, imported = ?, duplicates = ?, rejected = ?, failed = ? WHERE id = ?")
                .params(List.of(c.found(), c.imported(), c.duplicates(), c.rejected(), c.failed(), id))
                .update();
    }

    @Override
    public void finish(UUID id, boolean succeeded, String message) {
        jdbc.sql("UPDATE collection_run SET status = ?, message = ?, finished_at = now() WHERE id = ?")
                .params(List.of(succeeded ? "COMPLETED" : "FAILED", message, id))
                .update();
    }

    @Override
    public int failInterrupted() {
        return jdbc.sql("""
                        UPDATE collection_run
                        SET status = 'FAILED', message = 'Interrupted: the application stopped while this was running',
                            finished_at = now()
                        WHERE status = 'RUNNING'""")
                .update();
    }

    @Override
    public List<RunView> recent(int limit) {
        return jdbc.sql(SELECT + " ORDER BY started_at DESC LIMIT ?")
                .param(limit)
                .query((rs, n) -> map(rs))
                .list();
    }

    @Override
    public Optional<RunView> find(UUID id) {
        return jdbc.sql(SELECT + " WHERE id = ?")
                .param(id)
                .query((rs, n) -> map(rs))
                .optional();
    }

    private static final String SELECT = """
            SELECT id, source, options, status, found, imported, duplicates, rejected, failed,
                   message, started_at, finished_at
            FROM collection_run""";

    private static RunView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        var finished = rs.getObject("finished_at", java.time.OffsetDateTime.class);
        return new RunView(rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("options"),
                rs.getString("status"),
                new Counts(rs.getInt("found"), rs.getInt("imported"), rs.getInt("duplicates"),
                        rs.getInt("rejected"), rs.getInt("failed")),
                rs.getString("message"), TemplateRows.instant(rs, "started_at"),
                finished == null ? null : finished.toInstant());
    }
}
