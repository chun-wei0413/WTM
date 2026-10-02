package com.wtm.application;

import com.wtm.domain.template.TemplateId;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateId id) {
        super("Template " + id.value() + " not found");
    }
}
