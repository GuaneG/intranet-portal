package com.jforce.backend;

import com.jforce.backend.model.enums.TalepDurum;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class IzinTalepFoundationMigrationIntegrationTest {

    private static final String YENI_AUDIT_EYLEMI = "IZIN_TALEBI_OLUSTURULDU";
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
            'TATIL_SILINDI'
            """.replaceAll("\\s+", "");

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void eskiSemayiVeSeedVerileriniHazirla() throws SQLException {
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audit_log");
            statement.execute("DROP TABLE IF EXISTS izin_bilgileri");
            statement.execute("DROP TABLE IF EXISTS izin_turu");
            statement.execute("DROP TABLE IF EXISTS durum_turu");
            statement.execute("CREATE TABLE durum_turu (durum_id INT NOT NULL AUTO_INCREMENT, durum_adi ENUM('ONAYLANDI') NOT NULL, PRIMARY KEY (durum_id), UNIQUE KEY uk_durum_adi (durum_adi))");
            statement.execute("CREATE TABLE izin_turu (izin_turu_id INT NOT NULL AUTO_INCREMENT, izin_turu_adi VARCHAR(50) NOT NULL, yillik_haktan_duser_mi BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY (izin_turu_id), UNIQUE KEY uk_izin_turu_adi (izin_turu_adi))");
            statement.execute("CREATE TABLE izin_bilgileri (izin_id VARCHAR(36) NOT NULL, personel_id INT NOT NULL, izin_turu_id INT NOT NULL, baslangic_tarihi DATE NOT NULL, bitis_tarihi DATE NOT NULL, izin_durum_id INT NOT NULL, talep_tarihi DATETIME NOT NULL, PRIMARY KEY (izin_id))");
            statement.execute("CREATE TABLE audit_log (log_id VARCHAR(36) NOT NULL, eylem_tipi ENUM(" + legacyAuditEylemleriIcinEnum() + ") NOT NULL, PRIMARY KEY (log_id))");
            statement.execute("INSERT INTO durum_turu (durum_id, durum_adi) VALUES (17, 'ONAYLANDI')");
            statement.execute("INSERT INTO izin_turu (izin_turu_id, izin_turu_adi, yillik_haktan_duser_mi) VALUES (29, 'Yıllık İzin', TRUE)");
            statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) VALUES ('mevcut-audit', 'LOGIN_BASARILI')");
        }
    }

    @Test
    void migration_eski_seed_verilerini_koruyarak_izin_talebi_temel_verilerini_ekler_ve_idempotenttir() throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db", "manual-migrations",
                "2026-08-24-add-leave-request-foundation.sql"
        );

        try (Connection connection = baglan()) {
            assertFalse(auditEnumTokenlariniGetir(connection).contains(YENI_AUDIT_EYLEMI));
            migrationCalistir(connection, migration);

            assertEquals(beklenenDurumSayilari(), durumSayilari(connection));
            assertEquals(17, durumId(connection, "ONAYLANDI"));

            assertEquals(Map.of(
                    "Yıllık İzin", true,
                    "Hastalık İzni", false,
                    "Mazeret İzni", false
            ), izinTurleri(connection));
            assertEquals(29, izinTuruId(connection, "Yıllık İzin"));

            assertAuditEnumDegerleri(connection);
            assertEquals("LOGIN_BASARILI", auditEylemi(connection, "mevcut-audit"));
            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) VALUES ('yeni-audit', '" + YENI_AUDIT_EYLEMI + "')");
            }
            assertEquals(YENI_AUDIT_EYLEMI, auditEylemi(connection, "yeni-audit"));

            assertIndexKolonlari(connection, "idx_izin_personel_durum_tarih", List.of(
                    "personel_id", "izin_durum_id", "baslangic_tarihi", "bitis_tarihi"
            ));
            assertIndexKolonlari(connection, "idx_izin_personel_talep_sirasi", List.of(
                    "personel_id", "talep_tarihi", "izin_id"
            ));

            Map<String, Long> ilkDurumSayilari = durumSayilari(connection);
            Map<String, Boolean> ilkIzinTurleri = izinTurleri(connection);
            migrationCalistir(connection, migration);

            assertEquals(ilkDurumSayilari, durumSayilari(connection));
            assertEquals(ilkIzinTurleri, izinTurleri(connection));
            assertEquals(17, durumId(connection, "ONAYLANDI"));
            assertEquals(29, izinTuruId(connection, "Yıllık İzin"));
            assertEquals("LOGIN_BASARILI", auditEylemi(connection, "mevcut-audit"));
            assertEquals(YENI_AUDIT_EYLEMI, auditEylemi(connection, "yeni-audit"));
        }
    }

    private Connection baglan() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private void migrationCalistir(Connection connection, Path migration) throws SQLException {
        String script;
        try {
            script = Files.readString(migration, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("CannotReadScript: " + migration, exception);
        }

        try (Statement statement = connection.createStatement()) {
            for (String komut : script.split(";")) {
                if (!komut.isBlank()) {
                    statement.execute(komut);
                }
            }
        }
    }

    private Map<String, Long> durumSayilari(Connection connection) throws SQLException {
        Map<String, Long> sonuc = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT durum_adi, COUNT(*) FROM durum_turu GROUP BY durum_adi")) {
            while (resultSet.next()) {
                sonuc.put(resultSet.getString(1), resultSet.getLong(2));
            }
        }
        return sonuc;
    }

    private Map<String, Long> beklenenDurumSayilari() {
        Map<String, Long> sonuc = new LinkedHashMap<>();
        for (TalepDurum durum : TalepDurum.values()) {
            sonuc.put(durum.name(), 1L);
        }
        return sonuc;
    }

    private int durumId(Connection connection, String durumAdi) throws SQLException {
        return idGetir(connection, "SELECT durum_id FROM durum_turu WHERE durum_adi = ?", durumAdi);
    }

    private Map<String, Boolean> izinTurleri(Connection connection) throws SQLException {
        Map<String, Boolean> sonuc = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT izin_turu_adi, yillik_haktan_duser_mi FROM izin_turu ORDER BY izin_turu_id")) {
            while (resultSet.next()) {
                sonuc.put(resultSet.getString(1), resultSet.getBoolean(2));
            }
        }
        return sonuc;
    }

    private int izinTuruId(Connection connection, String izinTuruAdi) throws SQLException {
        return idGetir(connection, "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = ?", izinTuruAdi);
    }

    private int idGetir(Connection connection, String sql, String deger) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, deger);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        }
    }

    private void assertAuditEnumDegerleri(Connection connection) throws SQLException {
        assertEquals(beklenenAuditEylemleri(), auditEnumTokenlariniGetir(connection));
    }

    private Set<String> auditEnumTokenlariniGetir(Connection connection) throws SQLException {
        String columnType;
        try (PreparedStatement statement = connection.prepareStatement("SELECT column_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'eylem_tipi'")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                columnType = resultSet.getString(1);
            }
        }

        assertTrue(columnType.startsWith("enum("));
        assertTrue(columnType.endsWith(")"));
        return enumTokenlariniCoz(columnType);
    }

    private String legacyAuditEylemleriIcinEnum() {
        return LEGACY_AUDIT_ENUM;
    }

    private Set<String> beklenenAuditEylemleri() {
        Set<String> sonuc = enumTokenlariniCoz(
                "enum(" + LEGACY_AUDIT_ENUM + ")"
        );
        sonuc.add(YENI_AUDIT_EYLEMI);
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

    private String auditEylemi(Connection connection, String logId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT eylem_tipi FROM audit_log WHERE log_id = ?")) {
            statement.setString(1, logId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getString(1);
            }
        }
    }

    private void assertIndexKolonlari(Connection connection, String indexAdi, List<String> beklenenKolonlar) throws SQLException {
        List<String> gercekKolonlar = new java.util.ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT column_name FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'izin_bilgileri' AND index_name = ? ORDER BY seq_in_index")) {
            statement.setString(1, indexAdi);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    gercekKolonlar.add(resultSet.getString(1));
                }
            }
        }
        assertEquals(beklenenKolonlar, gercekKolonlar);
    }
}
