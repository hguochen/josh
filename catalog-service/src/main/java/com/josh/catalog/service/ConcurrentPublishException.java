package com.josh.catalog.service;

/**
 * phase2_design_specification.md Immediate Fixes: two publishes racing to
 * create the same name+version both compute the same next version number;
 * whichever commits second hits the UNIQUE(name, version) constraint. That's
 * correct — no corrupt data — but the caller deserves a clean "retry" signal
 * (409), not a raw SQL exception (500).
 */
public class ConcurrentPublishException extends RuntimeException {

    public ConcurrentPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
