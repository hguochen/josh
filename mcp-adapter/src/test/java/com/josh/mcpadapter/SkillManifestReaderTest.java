package com.josh.mcpadapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class SkillManifestReaderTest {

    private byte[] zipWith(String skillMdContent) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(skillMdContent.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buffer.toByteArray();
    }

    @Test
    void readsNameAndDescriptionFromFrontMatter() throws IOException {
        byte[] zipBytes = zipWith("""
            ---
            name: release-note-draft
            description: Draft release notes
            ---
            Body text here.
            """);

        Map<String, String> frontMatter = SkillManifestReader.readFrontMatter(zipBytes);

        assertThat(frontMatter).containsEntry("name", "release-note-draft");
        assertThat(frontMatter).containsEntry("description", "Draft release notes");
    }

    @Test
    void returnsEmptyMapWhenArchiveHasNoSkillMd() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("nope".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Map<String, String> frontMatter = SkillManifestReader.readFrontMatter(buffer.toByteArray());

        assertThat(frontMatter).isEmpty();
    }
}
