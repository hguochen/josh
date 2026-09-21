package com.josh.catalog.service;

/**
 * FR-03 exception: "The skill doesn't exist: the assistant returns a clear
 * 'not found.'" Distinct from InvalidSkillException (400, bad input) — this is
 * 404, a valid request for something that isn't there.
 */
public class SkillNotFoundException extends RuntimeException {

    public SkillNotFoundException(String message) {
        super(message);
    }
}
