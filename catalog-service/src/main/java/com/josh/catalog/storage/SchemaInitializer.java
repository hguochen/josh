package com.josh.catalog.storage;

import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates the Catalog Store schema (design_specifications.md Section 5 fields +
 * Section 8 SQL vs NoSQL Decision) on startup. All statements are idempotent
 * (IF NOT EXISTS) so this is safe to run every boot.
 *
 * Only an AFTER INSERT trigger keeps the FTS5 index in sync — no UPDATE/DELETE
 * trigger is needed because the catalog is append-only (Section 6: Retention).
 */
@Component
@Order(0)
public class SchemaInitializer implements ApplicationRunner {

    private static final List<String> DDL = List.of(
        """
        CREATE TABLE IF NOT EXISTS skill_versions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            version INTEGER NOT NULL,
            description TEXT NOT NULL,
            author TEXT NOT NULL,
            created_at TEXT NOT NULL,
            checksum TEXT NOT NULL,
            archive_path TEXT NOT NULL,
            UNIQUE(name, version)
        )
        """,
        "CREATE INDEX IF NOT EXISTS idx_skill_versions_name ON skill_versions(name)",
        """
        CREATE VIRTUAL TABLE IF NOT EXISTS skill_search USING fts5(
            name,
            description,
            content='skill_versions',
            content_rowid='id',
            tokenize='porter unicode61'
        )
        """,
        """
        CREATE TRIGGER IF NOT EXISTS skill_versions_after_insert
        AFTER INSERT ON skill_versions
        BEGIN
            INSERT INTO skill_search(rowid, name, description)
            VALUES (new.id, new.name, new.description);
        END
        """
    );

    private final JdbcTemplate jdbcTemplate;

    public SchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        DDL.forEach(jdbcTemplate::execute);
    }
}
