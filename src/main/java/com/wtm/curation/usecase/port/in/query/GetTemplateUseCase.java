package com.wtm.curation.usecase.port.in.query;

import java.util.UUID;

public interface GetTemplateUseCase {

    TemplateView handle(UUID id);
}
