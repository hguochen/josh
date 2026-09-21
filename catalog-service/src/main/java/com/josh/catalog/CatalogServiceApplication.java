package com.josh.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * DataSourceAutoConfiguration is excluded because catalog.storage.CatalogDataSourceConfig
 * supplies the DataSource bean itself, so it can create the storage directory before
 * SQLite opens the file (see that class's Javadoc).
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
