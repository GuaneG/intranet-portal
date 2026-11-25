package com.jforce.backend;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class IzinIsGunuMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationIsGunuKolonunuDecimalYaparVeMevcutDegerleriKorur()
            throws Exception {

        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-22-change-izin-is-gunu-to-decimal.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE izin_bilgileri (
                            izin_id VARCHAR(36) PRIMARY KEY,
                            is_gunu_sayisi INT NOT NULL
                        )
                        """);

                statement.execute("""
                        INSERT INTO izin_bilgileri (
                            izin_id,
                            is_gunu_sayisi
                        )
                        VALUES ('izin-1', 14)
                        """);
            }

            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertEquals(
                        "decimal",
                        kolonMetni(
                                statement,
                                "data_type"
                        )
                );

                assertEquals(
                        4,
                        kolonSayisi(
                                statement,
                                "numeric_precision"
                        )
                );

                assertEquals(
                        1,
                        kolonSayisi(
                                statement,
                                "numeric_scale"
                        )
                );

                assertDecimalEquals(
                        "14.0",
                        izinGunuGetir(statement)
                );

                statement.executeUpdate("""
                        UPDATE izin_bilgileri
                        SET is_gunu_sayisi = 3.5
                        WHERE izin_id = 'izin-1'
                        """);
            }

            // Migration tekrar çalıştırıldığında veri bozulmamalı.
            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertDecimalEquals(
                        "3.5",
                        izinGunuGetir(statement)
                );
            }
        }
    }

    private void migrationCalistir(
            Connection connection,
            Path migration
    ) {
        ScriptUtils.executeSqlScript(
                connection,
                new EncodedResource(
                        new FileSystemResource(migration),
                        StandardCharsets.UTF_8
                )
        );
    }

    private String kolonMetni(
            Statement statement,
            String kolon
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT %s
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'izin_bilgileri'
                  AND column_name = 'is_gunu_sayisi'
                """.formatted(kolon))) {

            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private int kolonSayisi(
            Statement statement,
            String kolon
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT %s
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'izin_bilgileri'
                  AND column_name = 'is_gunu_sayisi'
                """.formatted(kolon))) {

            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private BigDecimal izinGunuGetir(
            Statement statement
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT is_gunu_sayisi
                FROM izin_bilgileri
                WHERE izin_id = 'izin-1'
                """)) {

            resultSet.next();
            return resultSet.getBigDecimal(1);
        }
    }

    private void assertDecimalEquals(
            String beklenen,
            BigDecimal gercek
    ) {
        assertEquals(
                0,
                new BigDecimal(beklenen).compareTo(gercek)
        );
    }
}