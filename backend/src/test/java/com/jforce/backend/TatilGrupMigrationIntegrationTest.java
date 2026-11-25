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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class TatilGrupMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.0");

    @Test
    void eskiTekGunKayitlariAralikSemasinaVeriKaybetmedenTasinir()
            throws Exception {

        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-23-add-tatil-group-range.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            eskiSemayiVeVerileriOlustur(connection);

            migrationCalistir(connection, migration);
            semaVeVerileriDogrula(connection);

            migrationCalistir(connection, migration);
            semaVeVerileriDogrula(connection);

            try (Statement statement = connection.createStatement()) {
                SQLException tersAralikHatasi = assertThrows(
                        SQLException.class,
                        () -> statement.executeUpdate("""
                                INSERT INTO tatil (
                                    ad,
                                    tarih,
                                    bitis_tarihi,
                                    kategori,
                                    gun_suresi,
                                    version
                                )
                                VALUES (
                                    'Ters Aralık',
                                    '2026-08-15',
                                    '2026-08-11',
                                    'SIRKET',
                                    'TAM_GUN',
                                    0
                                )
                                """)
                );

                assertTrue(
                        tersAralikHatasi.getMessage()
                                .contains("chk_tatil_tarih_araligi")
                );
            }
        }
    }

    private void eskiSemayiVeVerileriOlustur(Connection connection)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE tatil (
                        tatil_id INT NOT NULL AUTO_INCREMENT,
                        ad VARCHAR(100) NOT NULL,
                        tarih DATE NOT NULL,
                        kategori VARCHAR(30) NOT NULL,
                        gun_suresi VARCHAR(20) NOT NULL,
                        version BIGINT NOT NULL DEFAULT 0,
                        PRIMARY KEY (tatil_id),
                        CONSTRAINT uk_tatil_tarih UNIQUE (tarih)
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO tatil (
                        tatil_id,
                        ad,
                        tarih,
                        kategori,
                        gun_suresi,
                        version
                    )
                    VALUES
                        (1, 'Birinci Tatil', '2026-08-30', 'RESMI_SABIT', 'TAM_GUN', 7),
                        (2, 'İkinci Tatil', '2026-10-29', 'RESMI_SABIT', 'YARIM_GUN', 3)
                    """);
        }
    }

    private void semaVeVerileriDogrula(Connection connection)
            throws Exception {
        try (Statement statement = connection.createStatement()) {
            assertEquals(
                    1,
                    tekInt(statement, """
                            SELECT COUNT(*)
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'tatil'
                              AND column_name = 'bitis_tarihi'
                              AND data_type = 'date'
                              AND is_nullable = 'NO'
                            """)
            );
            assertEquals(
                    1,
                    tekInt(statement, """
                            SELECT COUNT(*)
                            FROM information_schema.table_constraints
                            WHERE constraint_schema = DATABASE()
                              AND table_name = 'tatil'
                              AND constraint_name = 'chk_tatil_tarih_araligi'
                              AND constraint_type = 'CHECK'
                            """)
            );
            assertEquals(
                    1,
                    tekInt(statement, """
                            SELECT COUNT(DISTINCT index_name)
                            FROM information_schema.statistics
                            WHERE table_schema = DATABASE()
                              AND table_name = 'tatil'
                              AND index_name = 'idx_tatil_tarih_araligi'
                            """)
            );
            assertEquals(
                    2,
                    tekInt(statement, "SELECT COUNT(*) FROM tatil")
            );
            assertEquals(
                    0,
                    tekInt(statement, "SELECT COUNT(*) FROM tatil WHERE bitis_tarihi IS NULL")
            );
            assertEquals(
                    2,
                    tekInt(statement, "SELECT COUNT(*) FROM tatil WHERE bitis_tarihi = tarih")
            );
            assertEquals(
                    7,
                    tekInt(statement, "SELECT version FROM tatil WHERE tatil_id = 1")
            );
            assertEquals(
                    3,
                    tekInt(statement, "SELECT version FROM tatil WHERE tatil_id = 2")
            );
            assertEquals(
                    1,
                    tekInt(statement, "SELECT COUNT(*) FROM tatil_takvimi_kilit WHERE kilit_id = 1")
            );
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

    private int tekInt(Statement statement, String sql)
            throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
