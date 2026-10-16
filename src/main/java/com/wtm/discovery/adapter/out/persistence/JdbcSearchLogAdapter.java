package com.wtm.discovery.adapter.out.persistence;

import com.wtm.discovery.usecase.port.in.query.HotTerm;
import com.wtm.discovery.usecase.port.out.searchlog.SearchLogPort;
import com.wtm.shared.UserId;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSearchLogAdapter implements SearchLogPort {

    private final JdbcClient jdbc;

    JdbcSearchLogAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(UserId userId, String term) {
        jdbc.sql("INSERT INTO search_log (user_id, term) VALUES (?, ?)")
                .params(List.of(userId.value(), term))
                .update();
    }

    @Override
    public List<HotTerm> hot(Instant since, int limit) {
        return jdbc.sql("""
                        SELECT term, count(*) AS searches
                        FROM search_log
                        WHERE searched_at >= ?
                        GROUP BY term
                        ORDER BY searches DESC, max(searched_at) DESC
                        LIMIT ?""")
                .params(List.of(Timestamp.from(since), limit))
                .query((rs, n) -> new HotTerm(rs.getString("term"), rs.getInt("searches")))
                .list();
    }
}
