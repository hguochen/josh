package com.josh.mcpadapter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads just the front-matter (name/description) out of a downloaded archive's
 * SKILL.md, for fetch_skill's "returns ... plus its manifest" (Section 8).
 * Full validation is the Catalog Service's job; this is a display-only summary.
 */
final class SkillManifestReader {

    private SkillManifestReader() {}

    static Map<String, String> readFrontMatter(byte[] archiveBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("SKILL.md")) {
                    String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return parseFrontMatter(content);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.of();
    }

    private static Map<String, String> parseFrontMatter(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        String[] lines = content.split("\\R", -1);
        if (lines.length == 0 || !lines[0].strip().equals("---")) {
            return result;
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.equals("---")) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                result.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
            }
        }
        return result;
    }
}
