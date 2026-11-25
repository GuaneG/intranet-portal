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
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class TatilTableMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationTatilTablosunuConstraintlerleOlustururVeTekrarCalistirildigindaVeriyiKorur()
            throws Exception {

        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-22-create-tatil-table.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertEquals(
                        1,
                        tekInt(statement, """
                                SELECT COUNT(*)
                                FROM information_schema.tables
                                WHERE table_schema = DATABASE()
                                  AND table_name = 'tatil'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'tatil_id'
                                AND data_type = 'int'
                                AND is_nullable = 'NO'
                                AND extra = 'auto_increment'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'ad'
                                AND data_type = 'varchar'
                                AND character_maximum_length = 100
                                AND is_nullable = 'NO'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'tarih'
                                AND data_type = 'date'
                                AND is_nullable = 'NO'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'kategori'
                                AND data_type = 'varchar'
                                AND character_maximum_length = 30
                                AND is_nullable = 'NO'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'gun_suresi'
                                AND data_type = 'varchar'
                                AND character_maximum_length = 20
                                AND is_nullable = 'NO'
                                """)
                );

                assertEquals(
                        1,
                        kolonSayisi(statement, """
                                column_name = 'version'
                                AND data_type = 'bigint'
                                AND is_nullable = 'NO'
                                AND column_default = '0'
                                """)
                );

                statement.executeUpdate("""
                        INSERT INTO tatil (
                            ad,
                            tarih,
                            kategori,
                            gun_suresi
                        )
                        VALUES (
                            'Zafer Bayrami',
                            '2026-08-30',
                            'RESMI_SABIT',
                            'TAM_GUN'
                        )
                        """);

                assertEquals(
                        0,
                        tekInt(statement, """
                                SELECT version
                                FROM tatil
                                WHERE tarih = '2026-08-30'
                                """)
                );

                SQLException duplicateHatasi = assertThrows(
                        SQLException.class,
                        () -> statement.executeUpdate("""
                                INSERT INTO tatil (
                                    ad,
                                    tarih,
                                    kategori,
                                    gun_suresi
                                )
                                VALUES (
                                    'Ayni Tarihli Tatil',
                                    '2026-08-30',
                                    'SIRKET',
                                    'YARIM_GUN'
                                )
                                """)
                );

                assertEquals("23000", duplicateHatasi.getSQLState());

                statement.executeUpdate("""
                        UPDATE tatil
                        SET version = 7
                        WHERE tarih = '2026-08-30'
                        """);
            }

            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertEquals(
                        1,
                        tekInt(statement, """
                                SELECT COUNT(*)
                                FROM tatil
                                WHERE tarih = '2026-08-30'
                                """)
                );

                assertEquals(
                        7,
                        tekInt(statement, """
                                SELECT version
                                FROM tatil
                                WHERE tarih = '2026-08-30'
                                """)
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

    private int kolonSayisi(
            Statement statement,
            String kosul
    ) throws Exception {
        return tekInt(
                statement,
                """
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = 'tatil'
                          AND %s
                        """.formatted(kosul)
        );
    }

    private int tekInt(
            Statement statement,
            String sql
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}