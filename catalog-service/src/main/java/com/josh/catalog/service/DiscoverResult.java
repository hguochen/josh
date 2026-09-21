package com.josh.catalog.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One search hit (FR-02) — design_specifications.md Section 8 API Design response shape. */
public record DiscoverResult(
    String name,
    String description,
    @JsonProperty("latest_version") int latestVersion
) {}
