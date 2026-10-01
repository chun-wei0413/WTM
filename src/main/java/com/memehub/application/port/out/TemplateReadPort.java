package com.memehub.application.port.out;

import com.memehub.application.template.query.TemplateSummary;
import com.memehub.application.template.query.TemplateView;
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
