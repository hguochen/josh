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
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * design_specifications.md Section 7/8's "Catalog Service": validates publishes,
 * assigns version + checksum, enforces immutability (append-only, never
 * overwrite). One class, grown incrementally — Step 3 adds publish (FR-01);
 * discover/retrieve/version history follow in later steps.
 */
@Service
public class CatalogService {

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

        return new PublishResult(manifest.name(), version, checksum);
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
