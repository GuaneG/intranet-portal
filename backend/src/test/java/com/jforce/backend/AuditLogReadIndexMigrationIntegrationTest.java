package com.jforce.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class AuditLogReadIndexMigrationIntegrationTest {

    private static final String INDEX_ADI = "idx_audit_log_olusma_log_id";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationAuditOkumaIndeksiniEklerVeTekrarCalistirildigindaVeriyiKorur()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-26-add-audit-log-read-index.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE audit_log (
                            log_id VARCHAR(36) PRIMARY KEY,
                            olusma_zamani DATETIME(6) NOT NULL,
                            detay VARCHAR(255)
                        )
                        """);
                statement.execute("""
                        INSERT INTO audit_log (log_id, olusma_zamani, detay)
                        VALUES ('audit-1', '2026-08-26 10:00:00.000000', 'Korunmalı')
                        """);
            }

            migrationCalistir(connection, migration);
            indeksiDogrula(connection);

            migrationCalistir(connection, migration);
            indeksiDogrula(connection);

            try (Statement statement = connection.createStatement()) {
                assertEquals(1, tekInt(statement, "SELECT COUNT(*) FROM audit_log"));
                assertEquals("Korunmalı", tekString(statement, """
                        SELECT detay FROM audit_log WHERE log_id = 'audit-1'
                        """));
            }
        }
    }

    private void indeksiDogrula(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            assertEquals(2, tekInt(statement, """
                    SELECT COUNT(*)
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'audit_log'
                      AND index_name = 'idx_audit_log_olusma_log_id'
                    """));
            assertEquals("olusma_zamani", tekString(statement, """
                    SELECT column_name
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'audit_log'
                      AND index_name = 'idx_audit_log_olusma_log_id'
                      AND seq_in_index = 1
                    """));
            assertEquals("log_id", tekString(statement, """
                    SELECT column_name
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name = 'audit_log'
                      AND index_name = 'idx_audit_log_olusma_log_id'
                      AND seq_in_index = 2
                    """));
        }
    }

    private void migrationCalistir(Connection connection, Path migration) {
        ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(
                        new FileSystemResource(migration),
                        StandardCharsets.UTF_8
                )
        );
    }

    private int tekInt(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private String tekString(Statement statement, String sql) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
