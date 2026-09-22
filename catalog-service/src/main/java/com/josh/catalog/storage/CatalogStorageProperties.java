package com.josh.catalog.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root directory for the Catalog Store PoC (phase1_design_specifications.md Section 8:
 * embedded SQLite metadata + filesystem archive blobs, both under one root).
 */
@ConfigurationProperties(prefix = "catalog.storage")
public class CatalogStorageProperties {

    private String root = "./data";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public Path rootPath() {
        return Path.of(root);
    }

    public Path dbFile() {
        return rootPath().resolve("catalog.db");
    }

    public Path archivesDir() {
        return rootPath().resolve("archives");
    }
}
