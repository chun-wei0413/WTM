package com.usethatmeme.application;

import com.usethatmeme.domain.template.TemplateId;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateId id) {
        super("Template " + id.value() + " not found");
    }
}
