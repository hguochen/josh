package com.josh.catalog.skill;

/**
 * The result of zipping a skill directory (phase1_design_specifications.md Section 8,
 * SQL vs NoSQL Decision: archives are opaque byte blobs, not queried).
 * `checksum` is the SHA-256 hex digest of archiveBytes, computed at publish time
 * so fetch_skill can later verify it received the exact, unaltered archive.
 */
public record PackagedSkill(SkillManifest manifest, byte[] archiveBytes, String checksum) {}
