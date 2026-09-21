package com.josh.catalog.skill;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;

/**
 * Zips a developer-authored skill directory into the archive that gets stored per
 * version (Section 7, Skill package format) and computes its SHA-256 checksum
 * (Section 5's `checksum` field).
 */
@Component
public class SkillPackager {

    private final SkillManifestParser manifestParser;

    public SkillPackager(SkillManifestParser manifestParser) {
        this.manifestParser = manifestParser;
    }

    public PackagedSkill packageDirectory(Path skillDir) {
        Path skillMd = skillDir.resolve("SKILL.md");
        SkillManifest manifest = manifestParser.parse(skillMd);
        byte[] archiveBytes = zip(skillDir);
        String checksum = Checksums.sha256Hex(archiveBytes);
        return new PackagedSkill(manifest, archiveBytes, checksum);
    }

    private byte[] zip(Path skillDir) {
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
