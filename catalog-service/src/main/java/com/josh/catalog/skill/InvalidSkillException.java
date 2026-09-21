package com.josh.catalog.skill;

/**
 * A skill directory or SKILL.md failed validation. Maps to FR-01's "reject with an
 * explanation; nothing partial is stored" behavior once wired into the publish
 * endpoint (Step 3).
 */
public class InvalidSkillException extends RuntimeException {

    public InvalidSkillException(String message) {
        super(message);
    }
}
