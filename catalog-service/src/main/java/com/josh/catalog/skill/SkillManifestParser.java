package com.josh.catalog.skill;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Parses SKILL.md's YAML front matter (name, description) and Markdown body
 * (instructions) — see design_specifications.md Section 7, Skill package format.
 *
 * The front matter here is a flat key: value list, so a small hand-rolled parser
 * is used instead of a full YAML library — SKILL.md never needs nested structures.
 */
@Component
public class SkillManifestParser {

    private static final String DELIMITER = "---";

    public SkillManifest parse(Path skillMdFile) {
        if (!Files.isRegularFile(skillMdFile)) {
            throw new InvalidSkillException("Missing SKILL.md at " + skillMdFile);
        }
        String content;
        try {
            content = Files.readString(skillMdFile);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + skillMdFile, e);
        }
        return parse(content);
    }

    public SkillManifest parse(String content) {
        List<String> lines = new ArrayList<>(List.of(content.split("\\R", -1)));

        if (lines.isEmpty() || !lines.get(0).strip().equals(DELIMITER)) {
            throw new InvalidSkillException(
                "SKILL.md must start with YAML front matter delimited by '---'");
        }

        int closingIndex = -1;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).strip().equals(DELIMITER)) {
                closingIndex = i;
                break;
            }
        }
        if (closingIndex == -1) {
            throw new InvalidSkillException(
                "SKILL.md front matter is missing its closing '---'");
        }

        Map<String, String> frontMatter = parseFrontMatter(lines.subList(1, closingIndex));

        String instructions = String.join("\n", lines.subList(closingIndex + 1, lines.size())).strip();

        String name = frontMatter.get("name");
        String description = frontMatter.get("description");

        if (isBlank(name)) {
            throw new InvalidSkillException("SKILL.md front matter is missing 'name'");
        }
        if (isBlank(description)) {
            throw new InvalidSkillException("SKILL.md front matter is missing 'description'");
        }
        if (isBlank(instructions)) {
            throw new InvalidSkillException("SKILL.md is missing an instruction body after the front matter");
        }

        return new SkillManifest(name, description, instructions);
    }

    private Map<String, String> parseFrontMatter(List<String> frontMatterLines) {
        Map<String, String> result = new HashMap<>();
        for (String line : frontMatterLines) {
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw new InvalidSkillException("Malformed front matter line (expected 'key: value'): " + line);
            }
            String key = line.substring(0, colon).strip();
            String value = stripQuotes(line.substring(colon + 1).strip());
            result.put(key, value);
        }
        return result;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
