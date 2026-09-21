package com.josh.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.josh.catalog.skill.Checksums;
import com.josh.catalog.skill.InvalidSkillException;
import com.josh.catalog.skill.SkillPackager;
import com.josh.catalog.storage.CatalogStorageProperties;
import com.josh.catalog.storage.SkillVersion;
import com.josh.catalog.storage.SkillVersionRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Step 3 checkpoint: publish the sample release-note-draft/ skill through the real
 * CatalogService and confirm both the DB row and the archive on disk.
 *
 * Assertions are written relative to "before this test's own actions" rather than
 * absolute counts/versions, because @SpringBootTest caches one context (and its
 * SQLite file) across all test methods in this class — see CatalogStoreIntegrationTest.
 */
@SpringBootTest
class CatalogServiceTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void catalogStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("catalog.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private SkillVersionRepository repository;

    @Autowired
    private SkillPackager packager;

    @Autowired
    private CatalogStorageProperties storageProperties;

    private byte[] sampleArchiveBytes() throws Exception {
        Path skillDir = Path.of(getClass().getClassLoader()
            .getResource("sample-skills/release-note-draft")
            .toURI());
        return packager.packageDirectory(skillDir).archiveBytes();
    }

    @Test
    void publishingNewSkillWritesConsistentDbRowAndArchiveFile() throws Exception {
        PublishResult result = catalogService.publish(sampleArchiveBytes(), "Gary Hou");

        Optional<SkillVersion> stored = repository.findVersion(result.name(), result.version());
        assertThat(stored).isPresent();
        assertThat(stored.get().name()).isEqualTo("release-note-draft");
        assertThat(stored.get().description()).isEqualTo("Draft release notes from commit history");
        assertThat(stored.get().author()).isEqualTo("Gary Hou");
        assertThat(stored.get().checksum()).isEqualTo(result.checksum());

        Path archiveFile = storageProperties.archivesDir().resolve(stored.get().archivePath());
        assertThat(archiveFile).exists();
        assertThat(Checksums.sha256Hex(Files.readAllBytes(archiveFile))).isEqualTo(result.checksum());
    }

    @Test
    void republishingUnderSameNameIncrementsVersionByOneEachTime() throws Exception {
        int before = repository.findLatestVersion("release-note-draft")
            .map(SkillVersion::version)
            .orElse(0);

        PublishResult first = catalogService.publish(sampleArchiveBytes(), "Gary Hou");
        PublishResult second = catalogService.publish(sampleArchiveBytes(), "Gary Hou");

        assertThat(first.version()).isEqualTo(before + 1);
        assertThat(second.version()).isEqualTo(before + 2);
        assertThat(repository.findLatestVersion("release-note-draft"))
            .get()
            .extracting(SkillVersion::version)
            .isEqualTo(before + 2);
    }

    @Test
    void rejectsArchiveMissingSkillMdWithoutStoringAnything() throws IOException {
        byte[] badArchive = zipWithoutSkillMd();
        Set<Path> archiveFilesBefore = listArchiveFiles();

        assertThatThrownBy(() -> catalogService.publish(badArchive, "Gary Hou"))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("SKILL.md");

        assertThat(listArchiveFiles()).isEqualTo(archiveFilesBefore);
    }

    @Test
    void rejectsBlankAuthor() throws Exception {
        assertThatThrownBy(() -> catalogService.publish(sampleArchiveBytes(), " "))
            .isInstanceOf(InvalidSkillException.class)
            .hasMessageContaining("author");
    }

    @Test
    void discoverFindsPublishedSkillByDescriptionWord() throws Exception {
        catalogService.publish(sampleArchiveBytes(), "Gary Hou");

        List<DiscoverResult> results = catalogService.discover("release");

        assertThat(results).anySatisfy(r -> {
            assertThat(r.name()).isEqualTo("release-note-draft");
            assertThat(r.description()).isEqualTo("Draft release notes from commit history");
            assertThat(r.latestVersion()).isGreaterThanOrEqualTo(1);
        });
    }

    @Test
    void discoverReturnsEmptyForNoMatch() {
        assertThat(catalogService.discover("nonexistent-topic-abcxyz")).isEmpty();
    }

    @Test
    void discoverReturnsEmptyForBlankOrNullQuery() {
        assertThat(catalogService.discover("   ")).isEmpty();
        assertThat(catalogService.discover(null)).isEmpty();
    }

    private byte[] zipWithoutSkillMd() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("not a skill".getBytes());
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return buffer.toByteArray();
    }

    private Set<Path> listArchiveFiles() throws IOException {
        Path dir = storageProperties.archivesDir();
        if (!Files.exists(dir)) {
            return Set.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).collect(Collectors.toSet());
        }
    }
}
