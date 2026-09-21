package com.josh.catalog.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

/**
 * Step 2 checkpoint: package the sample release-note-draft/ directory and inspect
 * the resulting zip + checksum + parsed fields.
 */
class SkillPackagerTest {

    private final SkillPackager packager = new SkillPackager(new SkillManifestParser());

    private Path sampleSkillDir() throws URISyntaxException {
        return Path.of(getClass().getClassLoader()
            .getResource("sample-skills/release-note-draft")
            .toURI());
    }

    @Test
    void packagesTheSampleSkillWithExpectedManifestArchiveAndChecksum() throws Exception {
        PackagedSkill packaged = packager.packageDirectory(sampleSkillDir());

        assertThat(packaged.manifest().name()).isEqualTo("release-note-draft");
        assertThat(packaged.manifest().description()).isEqualTo("Draft release notes from commit history");
        assertThat(packaged.manifest().instructions()).contains("Summarize the commits since the last release");

        assertThat(packaged.archiveBytes()).isNotEmpty();
        assertThat(entryNames(packaged.archiveBytes()))
            .containsExactlyInAnyOrder("SKILL.md", "templates/release.md");

        assertThat(packaged.checksum()).hasSize(64).matches("[0-9a-f]{64}");

        // Deterministic: packaging the same directory twice gives the same checksum.
        PackagedSkill packagedAgain = packager.packageDirectory(sampleSkillDir());
        assertThat(packagedAgain.checksum()).isEqualTo(packaged.checksum());

        // Write it out so it can also be inspected manually, e.g.:
        //   unzip -l target/step2-sample-output/release-note-draft-v1.zip
        //   shasum -a 256 target/step2-sample-output/release-note-draft-v1.zip
        Path outDir = Path.of("target/step2-sample-output");
        Files.createDirectories(outDir);
        Files.write(outDir.resolve("release-note-draft-v1.zip"), packaged.archiveBytes());
        Files.writeString(outDir.resolve("release-note-draft-v1.zip.sha256"), packaged.checksum() + "\n");
    }

    @Test
    void rejectsADirectoryMissingSkillMd() throws Exception {
        Path emptyDir = Files.createTempDirectory("no-skill-md");

        assertThatThrownBy(() -> packager.packageDirectory(emptyDir))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("Missing SKILL.md");
    }

    private List<String> entryNames(byte[] zipBytes) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return names;
    }
}
