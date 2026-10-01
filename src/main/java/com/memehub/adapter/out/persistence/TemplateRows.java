package com.memehub.adapter.out.persistence;

import com.memehub.domain.template.MemeProfile;
import com.memehub.domain.template.Slot;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Column-to-value helpers shared by the write and read sides of template persistence.
 */
final class TemplateRows {

    static final String PROFILE_AND_META_COLUMNS = """
            id, name, image_key, image_width, image_height, status, version,
            meaning, usage_examples, emotions, aliases, created_at, updated_at""";

    private TemplateRows() {
    }

    static List<String> strings(ResultSet rs, String column) throws SQLException {
        Array array = rs.getArray(column);
        return array == null ? List.of() : List.of((String[]) array.getArray());
    }

    static MemeProfile profile(ResultSet rs) throws SQLException {
        return new MemeProfile(rs.getString("meaning"), strings(rs, "usage_examples"),
                strings(rs, "emotions"), strings(rs, "aliases"));
    }

    static Slot slot(ResultSet rs) throws SQLException {
        return new Slot(rs.getInt("slot_no"), rs.getString("role"), rs.getInt("max_chars"),
                rs.getBoolean("required"), rs.getInt("x"), rs.getInt("y"),
                rs.getInt("width"), rs.getInt("height"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
