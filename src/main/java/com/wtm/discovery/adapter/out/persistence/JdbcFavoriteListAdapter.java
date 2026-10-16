package com.wtm.discovery.adapter.out.persistence;

import com.wtm.discovery.usecase.port.out.favorite.FavoriteListPort;
import com.wtm.discovery.usecase.port.out.library.LibraryBrowsePort.LibraryCard;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcFavoriteListAdapter implements FavoriteListPort {

    private final JdbcClient jdbc;

    JdbcFavoriteListAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
