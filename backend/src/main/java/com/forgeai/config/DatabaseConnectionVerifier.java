package com.forgeai.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Opens a connection at startup and logs the database product and version.
 * If PostgreSQL is unreachable the application fails fast with a clear message
 * instead of starting in a half-working state.
 */
@Component
public class DatabaseConnectionVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionVerifier.class);

    private final DataSource dataSource;

    public DatabaseConnectionVerifier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            log.info("Connected to {} {} at {}",
                    metaData.getDatabaseProductName(),
                    metaData.getDatabaseProductVersion(),
                    metaData.getURL());
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Unable to connect to the database. Check DATABASE_URL, DATABASE_USERNAME and DATABASE_PASSWORD.", ex);
        }
    }
}
