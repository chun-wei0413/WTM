package com.wtm.curation.usecase.port.out.template;

import com.wtm.curation.entity.MemeTemplate;
import com.wtm.shared.TemplateId;
import com.wtm.shared.TemplateNotFoundException;
import java.util.Optional;

/**
 * Write-side persistence of the MemeTemplate aggregate.
 */
public interface TemplateRepository {

    /** Loads the template and locks it until the surrounding transaction ends. */
    Optional<MemeTemplate> findByIdForUpdate(TemplateId id);

    /** Inserts a new template or updates an existing one, including its slots. */
    void save(MemeTemplate template);

    default MemeTemplate getForUpdate(TemplateId id) {
        return findByIdForUpdate(id).orElseThrow(() -> new TemplateNotFoundException(id));
    }
}
