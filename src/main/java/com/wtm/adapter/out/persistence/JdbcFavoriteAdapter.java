package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.FavoritePort;
import com.wtm.application.port.out.LibraryBrowsePort.LibraryCard;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFavoriteAdapter implements FavoritePort {

    private final JdbcClient jdbc;

    JdbcFavoriteAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean add(UUID userId, UUID templateId) {
        // Only a published entry can be favorited. When it already is, nothing is inserted but it still counts.
        return jdbc.sql("""
                        WITH published AS (
                            SELECT id FROM meme_template WHERE id = ? AND status = 'APPROVED'
                        ), added AS (
                            INSERT INTO favorite (user_id, template_id)
                            SELECT ?, id FROM published
                            ON CONFLICT DO NOTHING
                        )
                        SELECT EXISTS (SELECT 1 FROM published)""")
                .params(List.of(templateId, userId))
                .query(Boolean.class)
                .single();
    }

    @Override
    public void remove(UUID userId, UUID templateId) {
        jdbc.sql("DELETE FROM favorite WHERE user_id = ? AND template_id = ?")
                .params(List.of(userId, templateId))
                .update();
    }

    @Override
    public List<LibraryCard> list(UUID userId) {
        return jdbc.sql("SELECT " + JdbcLibraryBrowseAdapter.CARD_COLUMNS + " FROM favorite f "
                        + "JOIN meme_template t ON t.id = f.template_id "
                        + "WHERE f.user_id = ? AND t.status = 'APPROVED' "
                        + "ORDER BY f.created_at DESC")
                .param(userId)
                .query((rs, n) -> JdbcLibraryBrowseAdapter.card(rs))
                .list();
    }
}
