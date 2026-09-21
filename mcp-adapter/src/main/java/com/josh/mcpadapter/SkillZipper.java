package com.josh.mcpadapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Zips a local skill directory for publish_skill. Only checks SKILL.md exists
 * (fail fast locally, Section 8's MCP Adapter implementation details) — parsing
 * the manifest is the Catalog Service's job and stays authoritative there.
 */
final class SkillZipper {

    private SkillZipper() {}

    static byte[] zip(Path skillDir) {
        if (!Files.isRegularFile(skillDir.resolve("SKILL.md"))) {
            throw new IllegalArgumentException("Missing SKILL.md in " + skillDir);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer);
             Stream<Path> paths = Files.walk(skillDir)) {
            for (Path path : paths.sorted().toList()) {
                if (Files.isDirectory(path)) {
                    continue;
                }
                String entryName = skillDir.relativize(path).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(path, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to zip skill directory " + skillDir, e);
        }
        return buffer.toByteArray();
    }
}
