package com.wtm.curation.usecase.port.in.query;

import java.util.List;

public interface ListTemplatesUseCase {

    List<TemplateSummary> handle(String status);
}
