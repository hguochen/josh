package com.josh.catalog.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Step 1 checkpoint: insert/query rows directly against the real Catalog Store
 * (SQLite + FTS5) and confirm the schema matches design_specifications.md Section 5.
 *
 * Uses a fresh @TempDir per test run so the UNIQUE(name, version) constraint
 * (append-only, Section 6) can never collide with leftovers from a previous run.
 */
@SpringBootTest
class CatalogStoreIntegrationTest {

    @TempDir
    static Path storageRoot;

    @DynamicPropertySource
    static void catalogStorageRoot(DynamicPropertyRegistry registry) {
        registry.add("catalog.storage.root", () -> storageRoot.toString());
    }

    @Autowired
    private SkillVersionRepository repository;

    private SkillVersion sampleVersion(String name, int version, String description) {
        return new SkillVersion(
            null,
            name,
            version,
            description,
            "Gary Hou",
            Instant.parse("2026-09-21T00:00:00Z").toString(),
            "checksum-" + name + "-v" + version,
            name + "/" + version + ".zip"
        );
    }

    @Test
    void insertedSkillVersionRoundTripsWithAllSection5Fields() {
        repository.insert(sampleVersion("release-note-draft", 1, "Draft release notes from commit history"));

        Optional<SkillVersion> latest = repository.findLatestVersion("release-note-draft");

        assertThat(latest).isPresent();
        SkillVersion sv = latest.get();
        assertThat(sv.name()).isEqualTo("release-note-draft");
        assertThat(sv.version()).isEqualTo(1);
        assertThat(sv.description()).isEqualTo("Draft release notes from commit history");
        assertThat(sv.author()).isEqualTo("Gary Hou");
        assertThat(sv.checksum()).isEqualTo("checksum-release-note-draft-v1");
        assertThat(sv.archivePath()).isEqualTo("release-note-draft/1.zip");
        assertThat(sv.createdAt()).isNotBlank();
        assertThat(sv.id()).isNotNull();
    }

    @Test
    void republishingUnderSameNameIncrementsVersionAndRetainsHistory() {
        repository.insert(sampleVersion("changelog-writer", 1, "Writes a changelog entry"));
        repository.insert(sampleVersion("changelog-writer", 2, "Writes a changelog entry, now with emoji"));

        assertThat(repository.findLatestVersion("changelog-writer"))
            .get()
            .extracting(SkillVersion::version)
            .isEqualTo(2);

        List<SkillVersion> history = repository.findAllVersions("changelog-writer");
        assertThat(history).hasSize(2);
        assertThat(history).extracting(SkillVersion::version).containsExactly(1, 2);

        assertThat(repository.findVersion("changelog-writer", 1))
            .get()
            .extracting(SkillVersion::description)
            .isEqualTo("Writes a changelog entry");
    }

    @Test
    void ftsSearchMatchesOnNameAndDescription() {
        // Spring caches the test context (and its SQLite file) across test methods in
        // this class, so vocabulary here must not overlap with other tests' descriptions.
        repository.insert(sampleVersion("doc-summary-writer", 1, "Summarizes documentation changes into a written report"));
        repository.insert(sampleVersion("unit-test-helper", 1, "Generates boilerplate JUnit test scaffolding"));

        List<SkillVersion> byDescriptionWord = repository.search("documentation");
        assertThat(byDescriptionWord).extracting(SkillVersion::name).containsExactly("doc-summary-writer");

        List<SkillVersion> byNameWord = repository.search("helper");
        assertThat(byNameWord).extracting(SkillVersion::name).containsExactly("unit-test-helper");

        List<SkillVersion> noMatch = repository.search("nonexistent-topic-xyz");
        assertThat(noMatch).isEmpty();
    }
}
