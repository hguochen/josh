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

    private static final String SHARED_SCOPE = "shared";

    private static final RowMapper<SkillVersion> ROW_MAPPER = (rs, rowNum) -> new SkillVersion(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getInt("version"),
        rs.getString("description"),
        rs.getString("author"),
        rs.getString("created_at"),
        rs.getString("checksum"),
        rs.getString("archive_path"),
        rs.getString("scope")
    );

    private final JdbcTemplate jdbcTemplate;

    public SkillVersionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(SkillVersion skillVersion) {
        jdbcTemplate.update(
            """
            INSERT INTO skill_versions (name, version, description, author, created_at, checksum, archive_path, scope)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            skillVersion.name(),
            skillVersion.version(),
            skillVersion.description(),
            skillVersion.author(),
            skillVersion.createdAt(),
            skillVersion.checksum(),
            skillVersion.archivePath(),
            skillVersion.scope()
        );
    }

    /** Shared-scope convenience overload — kept for existing (pre-personal-collections) callers. */
    public Optional<SkillVersion> findLatestVersion(String name) {
        return findLatestVersion(SHARED_SCOPE, name);
    }

    public Optional<SkillVersion> findLatestVersion(String scope, String name) {
        List<SkillVersion> rows = jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE scope = ? AND name = ? ORDER BY version DESC LIMIT 1",
            ROW_MAPPER,
            scope,
            name
        );
        return rows.stream().findFirst();
    }

    /** Shared-scope convenience overload — kept for existing (pre-personal-collections) callers. */
    public Optional<SkillVersion> findVersion(String name, int version) {
        return findVersion(SHARED_SCOPE, name, version);
    }

    public Optional<SkillVersion> findVersion(String scope, String name, int version) {
        List<SkillVersion> rows = jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE scope = ? AND name = ? AND version = ?",
            ROW_MAPPER,
            scope,
            name,
            version
        );
        return rows.stream().findFirst();
    }

    /** Shared-scope convenience overload — kept for existing (pre-personal-collections) callers. */
    public List<SkillVersion> findAllVersions(String name) {
        return findAllVersions(SHARED_SCOPE, name);
    }

    public List<SkillVersion> findAllVersions(String scope, String name) {
        return jdbcTemplate.query(
            "SELECT * FROM skill_versions WHERE scope = ? AND name = ? ORDER BY version ASC",
            ROW_MAPPER,
            scope,
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
     * name/description"). Shared-scope only — kept for existing (pre-personal-
     * collections) callers.
     */
    public List<SkillVersion> searchLatestVersions(String query) {
        return searchLatestVersions(query, null);
    }

    /**
     * Same as above, plus (when {@code callerAuthor} is given) that caller's own
     * personal-scope skills — never another developer's personal skills
     * (phase2_design_specification.md, Features). "Latest" is computed per
     * (scope, name), so a personal and a shared skill of the same name never
     * interfere with each other's version count.
     */
    public List<SkillVersion> searchLatestVersions(String query, String callerAuthor) {
        String ftsQuery = toFts5Query(query);
        String secondScope = (callerAuthor == null || callerAuthor.isBlank()) ? SHARED_SCOPE : callerAuthor;
        return jdbcTemplate.query(
            """
            SELECT sv.* FROM skill_versions sv
            WHERE sv.id IN (SELECT rowid FROM skill_search WHERE skill_search MATCH ?)
              AND sv.scope IN (?, ?)
              AND sv.version = (SELECT MAX(v2.version) FROM skill_versions v2
                                 WHERE v2.name = sv.name AND v2.scope = sv.scope)
            """,
            ROW_MAPPER,
            ftsQuery,
            SHARED_SCOPE,
            secondScope
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
