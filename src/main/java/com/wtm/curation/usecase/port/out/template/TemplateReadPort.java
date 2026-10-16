package com.wtm.curation.usecase.port.out.template;

import com.wtm.curation.usecase.port.in.query.TemplateSummary;
import com.wtm.curation.usecase.port.in.query.TemplateView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side access to templates. Bypasses the domain model on purpose (CQRS).
 */
public interface TemplateReadPort {

    Optional<TemplateView> findById(UUID id);

    /** @param status optional filter; {@code null} returns every status */
    List<TemplateSummary> list(String status);
}
