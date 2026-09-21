package com.josh.catalog.service;

/** Confirmation returned after a successful publish (FR-01). */
public record PublishResult(String name, int version, String checksum) {}
