package com.wtm.adapter.out.persistence;

import com.wtm.application.port.out.LibraryBrowsePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLibraryBrowseAdapter implements LibraryBrowsePort {

    /** The columns of a card; shared with the favorites query so both read an entry the same way. */
    static final String CARD_COLUMNS = """
            t.id, t.name, t.image_key, t.image_width, t.image_height, t.meaning, t.tags, t.image_text, t.source_type, t.source_url, t.attribution""";

    private final JdbcClient jdbc;

    JdbcLibraryBrowseAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<LibraryCard> random(int limit) {
        // A full scan with a random sort is fine for a library of up to tens of thousands of entries.
        return jdbc.sql("SELECT " + CARD_COLUMNS + " FROM meme_template t WHERE t.status = 'APPROVED' "
                        + "ORDER BY random() LIMIT ?")
                .param(limit)
                .query((rs, n) -> card(rs))
                .list();
    }

    @Override
    public Optional<LibraryImage> findPublishedImage(UUID templateId) {
        return jdbc.sql("SELECT image_key, name FROM meme_template WHERE id = ? AND status = 'APPROVED'")
                .param(templateId)
                .query((rs, n) -> new LibraryImage(rs.getString("image_key"), rs.getString("name")))
                .optional();
    }

    static LibraryCard card(ResultSet rs) throws SQLException {
        return new LibraryCard(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("image_key"),
                rs.getInt("image_width"), rs.getInt("image_height"), rs.getString("meaning"), TemplateRows.strings(rs, "tags"), rs.getString("image_text"),
                rs.getString("source_type"), rs.getString("source_url"), rs.getString("attribution"));
    }
}
