package com.jforce.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IzinTalepIptalMigrationIntegrationTest {

    private static final String LEGACY_AUDIT_ENUM = """
            'LOGIN_BASARILI','LOGIN_BASARISIZ','LOGOUT',
            'TOKEN_REUSE_ALARM','ROL_DEGISTIRILDI','PERSONEL_EKLENDI',
            'PAROLA_DEGISTIRILDI','PAROLA_SIFIRLANDI',
            'PERSONEL_GUNCELLENDI','PERSONEL_PASIFE_ALINDI',
            'PERSONEL_AKTIFLESTIRILDI','PROFIL_GUNCELLENDI',
            'PROFIL_FOTOGRAFI_GUNCELLENDI','PROFIL_FOTOGRAFI_SILINDI',
            'YETENEK_EKLENDI','YETENEK_GUNCELLENDI','YETENEK_SILINDI',
            'DEPARTMAN_EKLENDI','DEPARTMAN_GUNCELLENDI',
            'DEPARTMAN_SILINDI','TATIL_EKLENDI','TATIL_GUNCELLENDI',
            'TATIL_SILINDI','IZIN_TALEBI_OLUSTURULDU',
            'IZIN_TALEBI_ONAYLANDI','IZIN_TALEBI_REDDEDILDI'
            """.replaceAll("\\s+", "");
    private static final String IPTAL_AUDIT =
            "IZIN_TALEBI_IPTAL_EDILDI";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void kararAsamasiSemasiniHazirla() throws SQLException {
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audit_log");
            statement.execute("CREATE TABLE audit_log ("
                    + "log_id VARCHAR(36) NOT NULL, "
                    + "eylem_tipi ENUM(" + LEGACY_AUDIT_ENUM + ") NOT NULL, "
                    + "PRIMARY KEY (log_id))");
            statement.execute("""
                    INSERT INTO audit_log (log_id, eylem_tipi)
                    VALUES ('mevcut-karar-audit', 'IZIN_TALEBI_ONAYLANDI')
                    """);
        }
    }

    @Test
    void iptalMigrationiEnumuGenisletirEskiVeriyiKorurVeIdempotentCalisir()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db", "manual-migrations",
                "2026-08-25-add-leave-request-cancellation.sql"
        );

        try (Connection connection = baglan()) {
            assertFalse(auditEnumTokenlari(connection).contains(IPTAL_AUDIT));

            migrationCalistir(connection, migration);

            assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
            assertEquals(
                    "IZIN_TALEBI_ONAYLANDI",
                    auditEylemi(connection, "mevcut-karar-audit")
            );
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        INSERT INTO audit_log (log_id, eylem_tipi)
                        VALUES ('iptal-audit', 'IZIN_TALEBI_IPTAL_EDILDI')
                        """);
            }

            migrationCalistir(connection, migration);

            assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
            assertEquals(IPTAL_AUDIT, auditEylemi(connection, "iptal-audit"));
        }
    }

    private Connection baglan() throws SQLException {
        return DriverManager.getConnection(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword()
        );
    }

    private void migrationCalistir(Connection connection, Path migration)
            throws SQLException {
        String script;
        try {
            script = Files.readString(migration, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "CannotReadScript: " + migration,
                    exception
            );
        }

        try (Statement statement = connection.createStatement()) {
            for (String komut : script.split(";")) {
                if (!komut.isBlank()) {
                    statement.execute(komut);
                }
            }
        }
    }

    private Set<String> auditEnumTokenlari(Connection connection)
            throws SQLException {
        String columnType;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'audit_log'
                  AND column_name = 'eylem_tipi'
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                columnType = resultSet.getString(1);
            }
        }

        Set<String> sonuc = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("'([^']*)'").matcher(columnType);
        while (matcher.find()) {
            sonuc.add(matcher.group(1));
        }
        return sonuc;
    }

    private Set<String> beklenenAuditEylemleri() {
        Set<String> sonuc = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("'([^']*)'")
                .matcher(LEGACY_AUDIT_ENUM);
        while (matcher.find()) {
            sonuc.add(matcher.group(1));
        }
        sonuc.add(IPTAL_AUDIT);
        return sonuc;
    }

    private String auditEylemi(Connection connection, String logId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT eylem_tipi
                FROM audit_log
                WHERE log_id = ?
                """)) {
            statement.setString(1, logId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }
}
