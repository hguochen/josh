package com.josh.catalog.storage;

import java.io.IOException;
import java.nio.file.Files;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Manual DataSource bean (Section 8, Catalog Store: embedded SQLite, no server process)
 * so we control the file location and can create the storage directory before SQLite
 * opens it. Spring Boot's own DataSourceAutoConfiguration is excluded on the main
 * application class to avoid a conflicting second DataSource bean.
 */
@Configuration
@EnableConfigurationProperties(CatalogStorageProperties.class)
public class CatalogDataSourceConfig {

    /**
     * SQLite's default busy_timeout is 0 — a second concurrent writer fails
     * immediately with SQLITE_BUSY instead of briefly waiting for the lock.
     * Without this, concurrent publishes would fail on lock contention alone,
     * before ever reaching the UNIQUE(name, version) check that
     * ConcurrentPublishException is meant to handle cleanly.
     */
    private static final int BUSY_TIMEOUT_MS = 5000;

    @Bean
    public DataSource dataSource(CatalogStorageProperties properties) throws IOException {
        Files.createDirectories(properties.archivesDir());

        SQLiteConfig config = new SQLiteConfig();
        config.setBusyTimeout(BUSY_TIMEOUT_MS);

        SQLiteDataSource dataSource = new SQLiteDataSource(config);
        dataSource.setUrl("jdbc:sqlite:" + properties.dbFile());
        return dataSource;
    }
}
