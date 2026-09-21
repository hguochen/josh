package com.josh.catalog.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One row of a skill's version history (FR-04) — Section 7 flow's {version, created_at, author}. */
public record VersionSummary(
    int version,
    @JsonProperty("created_at") String createdAt,
    String author
) {}
