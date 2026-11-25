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
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IzinTalepKararMigrationIntegrationTest {

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
            'TATIL_SILINDI','IZIN_TALEBI_OLUSTURULDU'
            """.replaceAll("\\s+", "");

    private static final String ONAY_AUDIT = "IZIN_TALEBI_ONAYLANDI";
    private static final String RED_AUDIT = "IZIN_TALEBI_REDDEDILDI";

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void legacySemayiHazirla() throws SQLException {
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audit_log");
            statement.execute("DROP TABLE IF EXISTS izin_bilgileri");
            statement.execute("""
                    CREATE TABLE izin_bilgileri (
                        izin_id VARCHAR(36) NOT NULL,
                        personel_id VARCHAR(36) NOT NULL,
                        izin_turu_id INT NOT NULL,
                        baslangic_tarihi DATE NOT NULL,
                        bitis_tarihi DATE NOT NULL,
                        is_gunu_sayisi DECIMAL(4,1) NOT NULL,
                        yoneticiye_not TEXT NULL,
                        izin_durum_id INT NOT NULL,
                        talep_tarihi DATETIME NOT NULL,
                        islem_yapan_id VARCHAR(36) NULL,
                        islem_tarihi DATETIME NULL,
                        PRIMARY KEY (izin_id)
                    )
                    """);
            statement.execute("CREATE TABLE audit_log ("
                    + "log_id VARCHAR(36) NOT NULL, "
                    + "eylem_tipi ENUM(" + LEGACY_AUDIT_ENUM + ") NOT NULL, "
                    + "PRIMARY KEY (log_id))");
            statement.execute("""
                    INSERT INTO izin_bilgileri (
                        izin_id, personel_id, izin_turu_id,
                        baslangic_tarihi, bitis_tarihi, is_gunu_sayisi,
                        yoneticiye_not, izin_durum_id, talep_tarihi
                    ) VALUES (
                        '11111111-1111-1111-1111-111111111111',
                        'personel-legacy', 29,
                        '2026-08-25', '2026-08-25', 1.0,
                        'eski-not', 17, '2026-08-24 09:00:00'
                    )
                    """);
            statement.execute("""
                    INSERT INTO audit_log (log_id, eylem_tipi)
                    VALUES ('mevcut-audit', 'LOGIN_BASARILI')
                    """);
        }
    }

    @Test
    void kararMigrationiKolonEnumIndeksVeEskiVeriyiKoruyarakIdempotentCalisir()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db", "manual-migrations",
                "2026-08-25-add-leave-request-decisions.sql"
        );

        try (Connection connection = baglan()) {
            assertFalse(kolonVarMi(
                    connection,
                    "izin_bilgileri",
                    "karar_notu"
            ));
            assertFalse(auditEnumTokenlari(connection).contains(ONAY_AUDIT));
            assertFalse(auditEnumTokenlari(connection).contains(RED_AUDIT));

            migrationCalistir(connection, migration);

            assertKararNotuKolonu(connection);
            assertEquals(
                    beklenenAuditEylemleri(),
                    auditEnumTokenlari(connection)
            );
            assertIndexKolonlari(
                    connection,
                    "idx_izin_talep_sirasi",
                    List.of("talep_tarihi", "izin_id")
            );
            assertIndexKolonlari(
                    connection,
                    "idx_izin_durum_talep_sirasi",
                    List.of("izin_durum_id", "talep_tarihi", "izin_id")
            );
            assertEquals("eski-not", eskiIzinNotu(connection));
            assertEquals(
                    "LOGIN_BASARILI",
                    auditEylemi(connection, "mevcut-audit")
            );

            yeniAuditTokenlariniInsertEt(connection);
            migrationCalistir(connection, migration);

            assertEquals(
                    beklenenAuditEylemleri(),
                    auditEnumTokenlari(connection)
            );
            assertEquals(
                    1,
                    kolonSayisi(connection, "izin_bilgileri", "karar_notu")
            );
            assertEquals("eski-not", eskiIzinNotu(connection));
            assertEquals(ONAY_AUDIT, auditEylemi(connection, "onay-audit"));
            assertEquals(RED_AUDIT, auditEylemi(connection, "red-audit"));
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

    private boolean kolonVarMi(
            Connection connection,
            String tablo,
            String kolon
    ) throws SQLException {
        return kolonSayisi(connection, tablo, kolon) > 0;
    }

    private int kolonSayisi(
            Connection connection,
            String tablo,
            String kolon
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """)) {
            statement.setString(1, tablo);
            statement.setString(2, kolon);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private void assertKararNotuKolonu(Connection connection)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, character_maximum_length, is_nullable
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'izin_bilgileri'
                  AND column_name = 'karar_notu'
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("varchar", resultSet.getString(1));
                assertEquals(500L, resultSet.getLong(2));
                assertEquals("YES", resultSet.getString(3));
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
        Set<String> sonuc = enumTokenlariniCoz(
                "enum(" + LEGACY_AUDIT_ENUM + ")"
        );
        sonuc.add(ONAY_AUDIT);
        sonuc.add(RED_AUDIT);
        return sonuc;
    }

    private Set<String> enumTokenlariniCoz(String columnType) {
        Set<String> sonuc = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("'([^']*)'").matcher(columnType);
        while (matcher.find()) {
            sonuc.add(matcher.group(1));
        }
        return sonuc;
    }

    private void assertIndexKolonlari(
            Connection connection,
            String indexAdi,
            List<String> beklenenKolonlar
    ) throws SQLException {
        List<String> gercekKolonlar = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT column_name
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'izin_bilgileri'
                  AND index_name = ?
                ORDER BY seq_in_index
                """)) {
            statement.setString(1, indexAdi);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    gercekKolonlar.add(resultSet.getString(1));
                }
            }
        }
        assertEquals(beklenenKolonlar, gercekKolonlar);
    }

    private String eskiIzinNotu(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT yoneticiye_not
                FROM izin_bilgileri
                WHERE izin_id = '11111111-1111-1111-1111-111111111111'
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private void yeniAuditTokenlariniInsertEt(Connection connection)
            throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) "
                    + "VALUES ('onay-audit', '" + ONAY_AUDIT + "')");
            statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) "
                    + "VALUES ('red-audit', '" + RED_AUDIT + "')");
        }
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
