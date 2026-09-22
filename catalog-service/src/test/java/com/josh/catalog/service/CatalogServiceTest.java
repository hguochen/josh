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

    @Test
    void retrieveLatestReturnsExactPublishedArchiveBytes() throws Exception {
        byte[] archiveBytes = sampleArchiveBytes();
        PublishResult published = catalogService.publish(archiveBytes, "Gary Hou");

        RetrieveResult retrieved = catalogService.retrieve("release-note-draft", null);

        assertThat(retrieved.version()).isEqualTo(published.version());
        assertThat(retrieved.checksum()).isEqualTo(published.checksum());
        assertThat(retrieved.archiveBytes()).isEqualTo(archiveBytes);
    }

    @Test
    void retrieveSpecificVersionReturnsThatVersionNotLatest() throws Exception {
        byte[] archiveBytes = sampleArchiveBytes();
        PublishResult first = catalogService.publish(archiveBytes, "Gary Hou");
        catalogService.publish(archiveBytes, "Gary Hou"); // becomes the new latest

        RetrieveResult retrieved = catalogService.retrieve("release-note-draft", first.version());

        assertThat(retrieved.version()).isEqualTo(first.version());
        assertThat(retrieved.checksum()).isEqualTo(first.checksum());
    }

    @Test
    void retrieveUnknownNameThrowsNotFound() {
        assertThatThrownBy(() -> catalogService.retrieve("no-such-skill-xyz", null))
            .isInstanceOf(SkillNotFoundException.class)
            .hasMessageContaining("no-such-skill-xyz");
    }

    @Test
    void retrieveUnknownVersionOfExistingSkillThrowsNotFound() throws Exception {
        catalogService.publish(sampleArchiveBytes(), "Gary Hou");

        assertThatThrownBy(() -> catalogService.retrieve("release-note-draft", 9999))
            .isInstanceOf(SkillNotFoundException.class)
            .hasMessageContaining("9999");
    }

    @Test
    void historyReturnsAllVersionsOldestFirstWithAuthorAndTimestamp() throws Exception {
        byte[] archiveBytes = sampleArchiveBytes();
        catalogService.publish(archiveBytes, "Gary Hou");
        catalogService.publish(archiveBytes, "Gary Hou");

        List<VersionSummary> history = catalogService.history("release-note-draft");

        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history).isSortedAccordingTo(java.util.Comparator.comparingInt(VersionSummary::version));
        assertThat(history).allSatisfy(v -> {
            assertThat(v.author()).isNotBlank();
            assertThat(v.createdAt()).isNotBlank();
        });
    }

    @Test
    void historyOfUnknownNameThrowsNotFound() {
        assertThatThrownBy(() -> catalogService.history("no-such-skill-history-xyz"))
            .isInstanceOf(SkillNotFoundException.class)
            .hasMessageContaining("no-such-skill-history-xyz");
    }

    // -- phase2_design_specification.md Features: personal skill collections --

    @Test
    void privatePublishIsFoundOnlyByItsOwnAuthorsDiscover() throws Exception {
        catalogService.publish(archiveFor("alices-private-skill", "Only Alice can see this"), "alice", "private");

        assertThat(catalogService.discover("alices-private-skill", null))
            .noneMatch(r -> r.name().equals("alices-private-skill"));
        assertThat(catalogService.discover("alices-private-skill", "bob"))
            .noneMatch(r -> r.name().equals("alices-private-skill"));
        assertThat(catalogService.discover("alices-private-skill", "alice"))
            .anyMatch(r -> r.name().equals("alices-private-skill"));
    }

    @Test
    void retrieveResolvesOwnPrivateSkillButNeverSomeoneElses() throws Exception {
        catalogService.publish(archiveFor("alices-retrievable-skill", "desc"), "alice", "private");

        assertThat(catalogService.retrieve("alices-retrievable-skill", null, "alice").archiveBytes()).isNotEmpty();

        assertThatThrownBy(() -> catalogService.retrieve("alices-retrievable-skill", null, "bob"))
            .isInstanceOf(SkillNotFoundException.class);
        assertThatThrownBy(() -> catalogService.retrieve("alices-retrievable-skill", null, null))
            .isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    void historyResolvesOwnPrivateSkillButNeverSomeoneElses() throws Exception {
        catalogService.publish(archiveFor("alices-history-skill", "desc"), "alice", "private");

        assertThat(catalogService.history("alices-history-skill", "alice")).hasSize(1);

        assertThatThrownBy(() -> catalogService.history("alices-history-skill", "bob"))
            .isInstanceOf(SkillNotFoundException.class);
        assertThatThrownBy(() -> catalogService.history("alices-history-skill", null))
            .isInstanceOf(SkillNotFoundException.class);
    }

    @Test
    void promoteMovesLatestPersonalVersionIntoSharedAsFreshVersionOne() throws Exception {
        catalogService.publish(archiveFor("promotable-skill", "v1 text"), "alice", "private");
        catalogService.publish(archiveFor("promotable-skill", "v2 text"), "alice", "private"); // now v2 personally

        PublishResult promoted = catalogService.promote("promotable-skill", "alice");

        assertThat(promoted.name()).isEqualTo("promotable-skill");
        assertThat(promoted.version()).isEqualTo(1); // fresh in shared, not carrying over personal v2

        // Now visible to everyone via the shared-catalog path, no author needed.
        RetrieveResult shared = catalogService.retrieve("promotable-skill", null);
        assertThat(shared.checksum()).isEqualTo(promoted.checksum());
        assertThat(catalogService.discover("promotable-skill", null))
            .anyMatch(r -> r.name().equals("promotable-skill"));

        // Alice's personal copy still exists too — promote copies, doesn't move.
        assertThat(catalogService.retrieve("promotable-skill", null, "alice")).isNotNull();
    }

    @Test
    void promoteRejectsWhenNameAlreadyExistsInShared() throws Exception {
        catalogService.publish(archiveFor("already-shared-skill", "the shared one"), "carol"); // shared, default
        catalogService.publish(archiveFor("already-shared-skill", "bob's private one"), "bob", "private");

        assertThatThrownBy(() -> catalogService.promote("already-shared-skill", "bob"))
            .isInstanceOf(PromoteConflictException.class)
            .hasMessageContaining("already-shared-skill");
    }

    @Test
    void promoteOfNameWithNoPersonalSkillThrowsNotFound() {
        assertThatThrownBy(() -> catalogService.promote("never-published-by-anyone", "dave"))
            .isInstanceOf(SkillNotFoundException.class)
            .hasMessageContaining("dave");
    }

    private byte[] archiveFor(String name, String description) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            zip.putNextEntry(new ZipEntry("SKILL.md"));
            zip.write(("""
                ---
                name: %s
                description: %s
                ---
                Do the thing.
                """.formatted(name, description)).getBytes());
            zip.closeEntry();
        }
        return buffer.toByteArray();
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
