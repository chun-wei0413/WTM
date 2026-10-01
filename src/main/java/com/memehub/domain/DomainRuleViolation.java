package com.memehub.domain;

/**
 * Thrown when an operation would break an invariant of an aggregate.
 */
public class DomainRuleViolation extends RuntimeException {

    public DomainRuleViolation(String message) {
        super(message);
    }
}
