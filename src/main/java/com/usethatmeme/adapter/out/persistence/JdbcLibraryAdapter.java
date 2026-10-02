package com.usethatmeme.adapter.out.persistence;

import com.usethatmeme.application.collection.DuplicateImageException;
import com.usethatmeme.application.collection.Origin;
import com.usethatmeme.application.port.out.ImageFingerprintPort.Fingerprint;
import com.usethatmeme.application.port.out.LibraryPort;
import com.usethatmeme.application.port.out.TemplateRepository;
import com.usethatmeme.domain.template.MemeTemplate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcLibraryAdapter implements LibraryPort {

    private final JdbcClient jdbc;
    private final TemplateRepository templates;

    JdbcLibraryAdapter(JdbcClient jdbc, TemplateRepository templates) {
        this.jdbc = jdbc;
        this.templates = templates;
    }

    @Override
    public Optional<UUID> findDuplicate(String sha256, long perceptualHash, int maxDistance) {
        // `#` is bitwise XOR; counting the set bits of the result gives the number of differing bits.
        return jdbc.sql("""
                        SELECT id FROM meme_template
                        WHERE content_sha256 = ?
                           OR (phash IS NOT NULL AND bit_count((phash # ?::bigint)::bit(64)) <= ?)
                        LIMIT 1""")
                .params(List.of(sha256, perceptualHash, maxDistance))
                .query(UUID.class)
                .optional();
    }

    @Override
    @Transactional
    public void add(MemeTemplate template, Origin origin, Fingerprint fingerprint) {
        try {
            templates.save(template);
            jdbc.sql("""
                            UPDATE meme_template SET
                                source_type = ?, source_url = ?, source_page_url = ?, attribution = ?,
                                license_note = ?, content_sha256 = ?, phash = ?, collected_at = now()
                            WHERE id = ?""")
                    .params(Arrays.asList(origin.sourceType(), origin.sourceUrl(), origin.pageUrl(),
                            origin.attribution(), origin.licenseNote(), fingerprint.sha256(),
                            fingerprint.perceptualHash(), template.id().value()))
                    .update();
            jdbc.sql("INSERT INTO template_tagging (template_id, status) VALUES (?, 'PENDING')")
                    .param(template.id().value())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new DuplicateImageException();
        }
    }

    @Override
    public LibraryStats stats() {
        return jdbc.sql("""
                        SELECT
                            (SELECT count(*) FROM meme_template WHERE source_type IS NOT NULL) AS total,
                            (SELECT count(*) FROM meme_template WHERE source_type IS NOT NULL AND status = 'APPROVED') AS approved,
                            (SELECT count(*) FROM meme_template WHERE source_type IS NOT NULL AND status = 'RETIRED') AS retired,
                            (SELECT count(*) FROM template_tagging WHERE status = 'PENDING') AS waiting,
                            (SELECT count(*) FROM template_tagging WHERE status = 'RUNNING') AS running,
                            (SELECT count(*) FROM template_tagging WHERE status = 'FAILED') AS failed""")
                .query((rs, n) -> new LibraryStats(rs.getInt("total"), rs.getInt("approved"), rs.getInt("retired"),
                        rs.getInt("waiting"), rs.getInt("running"), rs.getInt("failed")))
                .single();
    }
}
