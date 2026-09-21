package com.josh.catalog.storage;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Data access for the Catalog Store's SQL metadata (Section 5 fields, Section 8
 * SQL vs NoSQL Decision). Append-only: there is intentionally no update/delete —
 * see Section 6, Retention.
 *
 * `search` here is a minimal FTS5 lookup to prove the index works end to end;
 * filtering to only latest versions per name is FR-02 business logic, added in
 * Step 4.
 */
@Repository
public class SkillVersionRepository {

    private static final RowMapper<SkillVersion> ROW_MAPPER = (rs, rowNum) -> new SkillVersion(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getInt("version"),
        rs.getString("description"),
        rs.getString("author"),
        rs.getString("created_at"),
        rs.getString("checksum"),
        rs.getString("archive_path")
    );

    private final JdbcTemplate jdbcTemplate;

    public SkillVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(SkillVersion skillVersion) {
        jdbcTemplate.update(
            """
            INSERT INTO skill_versions (name, version, description, author, created_at, checksum, archive_path)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            skillVersion.name(),
            skillVersion.version(),
            skillVersion.description(),
            skillVersion.author(),
            skillVersion.createdAt(),
            skillVersion.checksum(),
            skillVersion.archivePath()
        );
    }

    public Optional<SkillVersion> findLatestVersion(String name) {
        List<SkillVersion> rows = jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE name = ? ORDER BY version DESC LIMIT 1",
            ROW_MAPPER,
            name
        );
        return rows.stream().findFirst();
    }

    public Optional<SkillVersion> findVersion(String name, int version) {
        List<SkillVersion> rows = jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE name = ? AND version = ?",
            ROW_MAPPER,
            name,
            version
        );
        return rows.stream().findFirst();
    }

    public List<SkillVersion> findAllVersions(String name) {
        return jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE name = ? ORDER BY version ASC",
            ROW_MAPPER,
            name
        );
    }

    public List<SkillVersion> search(String query) {
        // Quoted as a literal FTS5 phrase so arbitrary natural-language input (hyphens,
        // colons, "NOT"/"OR", ...) is never parsed as FTS5's own query syntax.
        String phraseQuery = "\"" + query.replace("\"", "\"\"") + "\"";
        return jdbcTemplate.query(
            """
            SELECT * FROM skill_versions
            WHERE id IN (SELECT rowid FROM skill_search WHERE skill_search MATCH ?)
            """,
            ROW_MAPPER,
            phraseQuery
        );
    }
}
