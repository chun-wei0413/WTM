package com.memehub.application;

import com.memehub.domain.template.TemplateId;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateId id) {
        super("Template " + id.value() + " not found");
    }
}
