package com.wtm.discovery.adapter.out.persistence;

import com.wtm.discovery.entity.Favorite;
import com.wtm.discovery.usecase.port.out.favorite.FavoriteRepository;
import com.wtm.shared.UserId;
import com.wtm.shared.adapter.persistence.TemplateRows;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFavoriteRepository implements FavoriteRepository {

    private final JdbcClient jdbc;

    JdbcFavoriteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Favorite> find(UserId userId, UUID templateId) {
        return jdbc.sql("SELECT created_at FROM favorite WHERE user_id = ? AND template_id = ?")
                .params(List.of(userId.value(), templateId))
                .query((rs, n) -> Favorite.restore(userId, templateId, TemplateRows.instant(rs, "created_at")))
                .optional();
    }

    @Override
    public void save(Favorite favorite) {
        jdbc.sql("""
                        INSERT INTO favorite (user_id, template_id, created_at) VALUES (?, ?, ?)
                        ON CONFLICT DO NOTHING""")
                .params(List.of(favorite.userId().value(), favorite.templateId(), Timestamp.from(favorite.createdAt())))
                .update();
    }

    @Override
    public void remove(UserId userId, UUID templateId) {
        jdbc.sql("DELETE FROM favorite WHERE user_id = ? AND template_id = ?")
                .params(List.of(userId.value(), templateId))
                .update();
    }
}
