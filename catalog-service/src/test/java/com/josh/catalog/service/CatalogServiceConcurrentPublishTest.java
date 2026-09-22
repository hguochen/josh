package com.josh.catalog.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.josh.catalog.skill.SkillArchiveReader;
import com.josh.catalog.skill.SkillManifest;
import com.josh.catalog.storage.CatalogStorageProperties;
import com.josh.catalog.storage.SkillVersionRepository;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * phase2_design_specification.md Immediate Fixes ("Concurrent publish of a new
 * skill name fails ungracefully"): deterministic unit test for
 * CatalogService's own translation logic, complementing
 * SkillVersionRepositoryConcurrencyTest (which proves the real SQLite/Spring
 * behavior this stubs). A real thread race isn't reliably reproducible on
 * demand — it depends on which thread's read happens to land first — so this
 * drives the exact condition directly: the repository's insert throws
 * exactly what real SQLite/Spring is confirmed to throw on the constraint
 * this fix targets.
 */
class CatalogServiceConcurrentPublishTest {

    @Test
    void translatesAUniqueConstraintViolationIntoACleanConflict(@TempDir Path storageRoot) throws Exception {
        SkillArchiveReader archiveReader = mock(SkillArchiveReader.class);
        SkillVersionRepository repository = mock(SkillVersionRepository.class);
        CatalogStorageProperties storageProperties = new CatalogStorageProperties();
        storageProperties.setRoot(storageRoot.toString());

        when(archiveReader.readManifest(any())).thenReturn(
            new SkillManifest("racy-skill", "a skill two publishers race to create", "body")
        );
        when(repository.findLatestVersion("shared", "racy-skill")).thenReturn(Optional.empty());

        SQLException uniqueConstraintFailure = new SQLException(
            "A UNIQUE constraint failed (UNIQUE constraint failed: skill_versions.name, skill_versions.version)",
            null,
            19
        );
        doThrow(new UncategorizedSQLException("insert", "INSERT INTO skill_versions ...", uniqueConstraintFailure))
            .when(repository).insert(any());

        CatalogService catalogService = new CatalogService(archiveReader, repository, storageProperties);

        assertThatThrownBy(() -> catalogService.publish("archive-bytes".getBytes(), "tester"))
            .isInstanceOf(ConcurrentPublishException.class)
            .hasMessageContaining("racy-skill")
            .hasMessageContaining("please retry");
    }

    @Test
    void doesNotMisclassifyAnUnrelatedSqlFailureAsAConflict(@TempDir Path storageRoot) throws Exception {
        SkillArchiveReader archiveReader = mock(SkillArchiveReader.class);
        SkillVersionRepository repository = mock(SkillVersionRepository.class);
        CatalogStorageProperties storageProperties = new CatalogStorageProperties();
        storageProperties.setRoot(storageRoot.toString());

        when(archiveReader.readManifest(any())).thenReturn(
            new SkillManifest("other-skill", "unrelated failure", "body")
        );
        when(repository.findLatestVersion("shared", "other-skill")).thenReturn(Optional.empty());

        SQLException unrelatedFailure = new SQLException("disk I/O error", null, 10);
        UncategorizedSQLException unrelatedException =
            new UncategorizedSQLException("insert", "INSERT INTO skill_versions ...", unrelatedFailure);
        doThrow(unrelatedException).when(repository).insert(any());

        CatalogService catalogService = new CatalogService(archiveReader, repository, storageProperties);

        assertThatThrownBy(() -> catalogService.publish("archive-bytes".getBytes(), "tester"))
            .isSameAs(unrelatedException);
    }
}
