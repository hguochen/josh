package com.josh.catalog.service;

/**
 * phase2_design_specification.md Features: promote is rejected, not merged or
 * auto-renamed, when the target name already exists in the shared catalog.
 * Distinct from ConcurrentPublishException — this is a business rule, not a
 * race (the check happens before any write is attempted).
 */
public class PromoteConflictException extends RuntimeException {

    public PromoteConflictException(String message) {
        super(message);
    }
}
