package com.wtm.shared;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(TemplateId id) {
        super("Template " + id.value() + " not found");
    }
}
