package com.josh.catalog.storage;

/**
 * One row of the Catalog Store's SQL metadata (design_specifications.md Section 5).
 * `description` here is extracted from SKILL.md's front matter at publish time
 * (Section 7: Skill package format) — the archive on the filesystem, addressed by
 * archivePath, remains the source of truth for `instructions` and `files`.
 */
public record SkillVersion(
    Long id,
    String name,
    int version,
    String description,
    String author,
    String createdAt,
    String checksum,
    String archivePath
) {}
