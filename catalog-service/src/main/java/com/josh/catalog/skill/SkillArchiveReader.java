package com.josh.catalog.skill;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Component;

/**
 * Reads SKILL.md back out of an uploaded archive (Section 7 flow: the MCP Adapter
 * zips the directory client-side and POSTs the archive; the Catalog Service still
 * has to open it to validate and extract name/description for the SQL row).
 */
@Component
public class SkillArchiveReader {

    private final SkillManifestParser manifestParser;

    public SkillArchiveReader(SkillManifestParser manifestParser) {
        this.manifestParser = manifestParser;
    }

    public SkillManifest readManifest(byte[] archiveBytes) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals("SKILL.md")) {
                    String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                    return manifestParser.parse(content);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read archive", e);
        }
        throw new InvalidSkillException("Archive is missing SKILL.md at its root");
    }
}
