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
class PersonelVersionMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationVersionKolonunuEklerVeTekrarCalistirildigindaDegerleriKorur() throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-15-add-personel-version.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE personel (personel_id VARCHAR(36) PRIMARY KEY)");
                statement.execute("INSERT INTO personel (personel_id) VALUES ('personel-1'), ('personel-2')");
            }

            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertEquals(1, tekInt(statement, """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'personel'
                          AND column_name = 'version'
                          AND data_type = 'bigint'
                          AND is_nullable = 'NO'
                          AND column_default = '0'
                        """));
                assertEquals(0, tekInt(statement, "SELECT COUNT(*) FROM personel WHERE version IS NULL"));
                assertEquals(2, tekInt(statement, "SELECT COUNT(*) FROM personel WHERE version = 0"));
                statement.executeUpdate("UPDATE personel SET version = 7 WHERE personel_id = 'personel-1'");
            }

            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertEquals(1, tekInt(statement, """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'personel'
                          AND column_name = 'version'
                        """));
                assertEquals(7, tekInt(statement, """
                        SELECT version FROM personel WHERE personel_id = 'personel-1'
                        """));
                assertEquals(0, tekInt(statement, """
                        SELECT version FROM personel WHERE personel_id = 'personel-2'
                        """));
            }
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
}
