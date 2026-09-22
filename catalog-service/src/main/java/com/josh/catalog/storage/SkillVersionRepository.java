package com.josh.catalog.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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

    // A natural-language question ("is there a skill for writing bug reports?")
    // is mostly filler around one or two real keywords. Dropping these before
    // building the FTS5 query is what makes discovery actually usable for how
    // an assistant phrases things, rather than only for a single bare keyword.
    private static final Set<String> STOP_WORDS = Set.of(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "to", "of", "in", "on", "at", "for", "with", "and", "or", "but",
        "that", "this", "these", "those", "i", "you", "we", "they", "it",
        "there", "do", "does", "did", "can", "could", "will", "would",
        "should", "have", "has", "had", "my", "me", "please", "get", "find",
        "need", "want", "show", "tell", "how", "what", "skill", "skills"
    );

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
        String ftsQuery = toFts5Query(query);
        return jdbcTemplate.query(
            """
            SELECT * FROM skill_versions
            WHERE id IN (SELECT rowid FROM skill_search WHERE skill_search MATCH ?)
            """,
            ROW_MAPPER,
            ftsQuery
        );
    }

    /**
     * FR-02 Discover: match only rows that are BOTH an FTS hit AND the latest
     * version for their name — never surface a stale older version whose text
     * happened to match (Section 7 diagram: "read latest versions; match
     * name/description").
     */
    public List<SkillVersion> searchLatestVersions(String query) {
        String ftsQuery = toFts5Query(query);
        return jdbcTemplate.query(
            """
            SELECT sv.* FROM skill_versions sv
            WHERE sv.id IN (SELECT rowid FROM skill_search WHERE skill_search MATCH ?)
              AND sv.version = (SELECT MAX(v2.version) FROM skill_versions v2 WHERE v2.name = sv.name)
            """,
            ROW_MAPPER,
            ftsQuery
        );
    }

    /**
     * Splits the query into words, drops stop words, and ORs the rest together
     * as individually-quoted literal terms (never a single rigid phrase) —
     * "is there a skill for writing bug reports?" becomes "bug" OR "writing" OR
     * "reports", any of which is enough to surface a relevant skill. Splitting
     * on \\W+ also strips hyphens/colons/quotes before they ever reach FTS5, so
     * they can't be misread as its own query-syntax operators.
     */
    private String toFts5Query(String query) {
        List<String> words = Arrays.stream(query.toLowerCase().split("\\W+"))
            .filter(w -> !w.isBlank())
            .toList();

        List<String> meaningful = words.stream()
            .filter(w -> !STOP_WORDS.contains(w))
            .toList();

        List<String> terms = meaningful.isEmpty() ? words : meaningful;

        if (terms.isEmpty()) {
            return "\"" + query.replace("\"", "\"\"") + "\"";
        }

        return terms.stream()
            .map(t -> "\"" + t.replace("\"", "\"\"") + "\"")
            .collect(Collectors.joining(" OR "));
    }
}
