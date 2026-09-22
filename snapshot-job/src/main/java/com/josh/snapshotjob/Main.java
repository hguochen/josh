package com.josh.snapshotjob;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Standalone entry point invoked by OS cron (Section 8's Snapshot Job decision —
 * not a long-running process, not part of catalog-service).
 */
public class Main {

    public static void main(String[] args) {
        Path storageRoot = Paths.get(System.getenv().getOrDefault("CATALOG_STORAGE_ROOT", "./data"));
        Path snapshotRoot = Paths.get(System.getenv().getOrDefault("SNAPSHOT_DIR", "./snapshots"));

        System.out.println("Snapshotting " + storageRoot + " -> " + snapshotRoot);
        try {
            Path destination = new SnapshotJob(storageRoot, snapshotRoot).run();
            System.out.println("Snapshot complete: " + destination);
        } catch (RuntimeException e) {
            System.err.println("Snapshot FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
