package com.josh.catalog.skill;

/**
 * Parsed from SKILL.md (phase1_design_specifications.md Section 7, Skill package format):
 * YAML front matter carries name/description, the Markdown body is the instructions.
 */
public record SkillManifest(String name, String description, String instructions) {}
