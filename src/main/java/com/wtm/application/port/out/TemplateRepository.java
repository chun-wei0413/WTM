package com.wtm.application.port.out;

import com.wtm.application.TemplateNotFoundException;
import com.wtm.domain.template.MemeTemplate;
import com.wtm.domain.template.TemplateId;
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
