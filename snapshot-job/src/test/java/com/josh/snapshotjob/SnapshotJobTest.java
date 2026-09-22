package com.josh.snapshotjob;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotJobTest {

    @Test
    void snapshotsAQueryableDatabaseCopyAndByteIdenticalArchives(@TempDir Path root) throws Exception {
        Path storageRoot = root.resolve("data");
        Path snapshotRoot = root.resolve("snapshots");
        Files.createDirectories(storageRoot.resolve("archives/release-note-draft"));

        seedDatabase(storageRoot.resolve("catalog.db"));
        byte[] archiveContent = "pretend zip bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(storageRoot.resolve("archives/release-note-draft/1.zip"), archiveContent);

        Path destination = new SnapshotJob(storageRoot, snapshotRoot).run();

        assertThat(destination).exists();
        assertThat(destination.getParent()).isEqualTo(snapshotRoot);

        Path snapshottedDb = destination.resolve("catalog.db");
        assertThat(snapshottedDb).exists();
        assertThat(rowCount(snapshottedDb)).isEqualTo(1);

        Path snapshottedArchive = destination.resolve("archives/release-note-draft/1.zip");
        assertThat(snapshottedArchive).exists();
        assertThat(Files.readAllBytes(snapshottedArchive)).isEqualTo(archiveContent);
    }

    @Test
    void producesADistinctTimestampedDirectoryEachRun(@TempDir Path root)
        throws IOException, InterruptedException, SQLException {
        Path storageRoot = root.resolve("data");
        Path snapshotRoot = root.resolve("snapshots");
        Files.createDirectories(storageRoot);
        seedDatabase(storageRoot.resolve("catalog.db"));

        Path first = new SnapshotJob(storageRoot, snapshotRoot).run();
        Thread.sleep(1100); // the timestamp format's resolution is whole seconds
        Path second = new SnapshotJob(storageRoot, snapshotRoot).run();

        assertThat(first).isNotEqualTo(second);
        assertThat(first).exists();
        assertThat(second).exists();
    }

    private void seedDatabase(Path dbFile) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE skill_versions (id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("INSERT INTO skill_versions (name) VALUES ('release-note-draft')");
        }
    }

    private int rowCount(Path dbFile) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM skill_versions")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
