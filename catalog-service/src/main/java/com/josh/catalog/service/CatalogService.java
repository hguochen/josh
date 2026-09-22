package com.josh.catalog.service;

import com.josh.catalog.skill.Checksums;
import com.josh.catalog.skill.InvalidSkillException;
import com.josh.catalog.skill.SkillArchiveReader;
import com.josh.catalog.skill.SkillManifest;
import com.josh.catalog.storage.CatalogStorageProperties;
import com.josh.catalog.storage.SkillVersion;
import com.josh.catalog.storage.SkillVersionRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Service;

/**
 * phase1_design_specifications.md Section 7/8's "Catalog Service": validates publishes,
 * assigns version + checksum, enforces immutability (append-only, never
 * overwrite), matches discovery queries, resolves version lookups. One class,
 * grown incrementally — Step 3 added publish (FR-01), Step 4 discover (FR-02),
 * Step 5 retrieve (FR-03), Step 6 version history (FR-04).
 */
@Service
public class CatalogService {

    /**
     * Spring has no built-in error-code mapping for SQLite, so a constraint
     * violation surfaces as UncategorizedSQLException rather than the usual
     * DataIntegrityViolationException — checked via SQLite's own error code
     * (19 = SQLITE_CONSTRAINT) rather than string-matching the message.
     */
    private static final int SQLITE_CONSTRAINT_ERROR_CODE = 19;

    private final SkillArchiveReader archiveReader;
    private final SkillVersionRepository repository;
    private final CatalogStorageProperties storageProperties;

    public CatalogService(
        SkillArchiveReader archiveReader,
        SkillVersionRepository repository,
        CatalogStorageProperties storageProperties
    ) {
        this.archiveReader = archiveReader;
        this.repository = repository;
        this.storageProperties = storageProperties;
    }

    /**
     * FR-01: validate, assign version (increment if name exists, else v1), then
     * write the archive to the filesystem and the metadata row to SQL. Validation
     * happens before either write, so a rejection never leaves anything partial
     * stored (PRD's FR-01 exception: "nothing partial is stored").
     */
    public PublishResult publish(byte[] archiveBytes, String author) {
        if (author == null || author.isBlank()) {
            throw new InvalidSkillException("author is required");
        }

        SkillManifest manifest = archiveReader.readManifest(archiveBytes);

        int version = repository.findLatestVersion(manifest.name())
            .map(latest -> latest.version() + 1)
            .orElse(1);

        String checksum = Checksums.sha256Hex(archiveBytes);
        String relativeArchivePath = manifest.name() + "/" + version + ".zip";

        writeArchive(relativeArchivePath, archiveBytes);

        try {
            repository.insert(new SkillVersion(
                null,
                manifest.name(),
                version,
                manifest.description(),
                author,
                Instant.now().toString(),
                checksum,
                relativeArchivePath
            ));
        } catch (UncategorizedSQLException e) {
            if (!isUniqueConstraintViolation(e)) {
                throw e;
            }
            throw new ConcurrentPublishException(
                "'" + manifest.name() + "' version " + version + " was just published by someone else — please retry",
                e
            );
        }

        return new PublishResult(manifest.name(), version, checksum);
    }

    /**
     * FR-02: natural-language search against latest versions only (never a stale
     * older version's text). A blank query always yields no results rather than
     * hitting FTS5 with an invalid empty MATCH expression.
     */
    public List<DiscoverResult> discover(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return repository.searchLatestVersions(query).stream()
            .map(sv -> new DiscoverResult(sv.name(), sv.description(), sv.version()))
            .toList();
    }

    /**
     * FR-03: fetch the exact, unaltered archive for a name — latest version if
     * `version` is absent, otherwise that specific version. Reading the bytes back
     * from disk (rather than trusting anything cached) means what's returned is
     * genuinely what's on the filesystem right now.
     */
    public RetrieveResult retrieve(String name, Integer version) {
        SkillVersion skillVersion = (version == null
                ? repository.findLatestVersion(name)
                : repository.findVersion(name, version))
            .orElseThrow(() -> new SkillNotFoundException(notFoundMessage(name, version)));

        byte[] archiveBytes = readArchive(skillVersion.archivePath());

        return new RetrieveResult(skillVersion.name(), skillVersion.version(), skillVersion.checksum(), archiveBytes);
    }

    /**
     * FR-04: full version history for a name, oldest first, per Section 7's
     * skill_history(name) flow. An unknown name is a 404, same as retrieve —
     * "show me the history of a skill" implies the skill should exist.
     */
    public List<VersionSummary> history(String name) {
        List<SkillVersion> versions = repository.findAllVersions(name);
        if (versions.isEmpty()) {
            throw new SkillNotFoundException("No skill named '" + name + "'");
        }
        return versions.stream()
            .map(sv -> new VersionSummary(sv.version(), sv.createdAt(), sv.author()))
            .toList();
    }

    private boolean isUniqueConstraintViolation(UncategorizedSQLException e) {
        SQLException sqlException = e.getSQLException();
        return sqlException != null && sqlException.getErrorCode() == SQLITE_CONSTRAINT_ERROR_CODE;
    }

    private String notFoundMessage(String name, Integer version) {
        return version == null
            ? "No skill named '" + name + "'"
            : "No version " + version + " of skill '" + name + "'";
    }

    private byte[] readArchive(String relativePath) {
        Path source = storageProperties.archivesDir().resolve(relativePath);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read archive " + source, e);
        }
    }

    private void writeArchive(String relativePath, byte[] archiveBytes) {
        Path target = storageProperties.archivesDir().resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, archiveBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write archive " + target, e);
        }
    }
}
