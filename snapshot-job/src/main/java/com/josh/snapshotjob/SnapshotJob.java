package com.josh.snapshotjob;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

/**
 * phase1_design_specifications.md Section 6 (Retention: durability design) and Section 8
 * (Snapshot Job): a full, timestamped copy of the Catalog Store — decoupled from
 * catalog-service so a snapshot never depends on that process being up, and
 * durability-only (never a read/query path for discover/retrieve).
 */
final class SnapshotJob {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final Path storageRoot;
    private final Path snapshotRoot;

    SnapshotJob(Path storageRoot, Path snapshotRoot) {
        this.storageRoot = storageRoot;
        this.snapshotRoot = snapshotRoot;
    }

    Path run() {
        Path dbFile = storageRoot.resolve("catalog.db");
        Path archivesDir = storageRoot.resolve("archives");
        Path destination = snapshotRoot.resolve(TIMESTAMP_FORMAT.format(Instant.now()));

        try {
            Files.createDirectories(destination);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create snapshot destination " + destination, e);
        }

        if (Files.exists(dbFile)) {
            backupDatabase(dbFile, destination.resolve("catalog.db"));
        }
        if (Files.exists(archivesDir)) {
            copyDirectory(archivesDir, destination.resolve("archives"));
        }

        return destination;
    }

    /**
     * Uses SQLite's own VACUUM INTO rather than a raw file copy — it produces a
     * complete, consistent snapshot even while catalog-service has the database
     * open, so this job never needs the service stopped or coordinated with.
     */
    private void backupDatabase(Path source, Path destination) {
        String url = "jdbc:sqlite:" + source;
        String escapedDestination = destination.toString().replace("'", "''");
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escapedDestination + "'");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to snapshot database " + source, e);
        }
    }

    /**
     * Archive files are immutable once written (Section 6), so a plain
     * recursive copy is safe — no version's content ever changes underneath it.
     */
    private void copyDirectory(Path source, Path destination) {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.sorted().toList()) {
                Path target = destination.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy archives from " + source, e);
        }
    }
}
