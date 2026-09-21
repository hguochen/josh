package com.josh.catalog.storage;

import java.io.IOException;
import java.nio.file.Files;
import javax.sql.DataSource;
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

    @Bean
    public DataSource dataSource(CatalogStorageProperties properties) throws IOException {
        Files.createDirectories(properties.archivesDir());

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + properties.dbFile());
        return dataSource;
    }
}
