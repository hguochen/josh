package com.josh.mcpadapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillZipperTest {

    @Test
    void zipsAllFilesUnderTheSkillDirectory(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("SKILL.md"), "---\nname: x\ndescription: y\n---\nbody\n");
        Files.createDirectory(dir.resolve("templates"));
        Files.writeString(dir.resolve("templates/t.md"), "template content");

        byte[] zipBytes = SkillZipper.zip(dir);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        assertThat(names).containsExactlyInAnyOrder("SKILL.md", "templates/t.md");
    }

    @Test
    void rejectsADirectoryMissingSkillMd(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("README.md"), "not a skill");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> SkillZipper.zip(dir));
        assertThat(ex.getMessage()).contains("SKILL.md");
    }
}
