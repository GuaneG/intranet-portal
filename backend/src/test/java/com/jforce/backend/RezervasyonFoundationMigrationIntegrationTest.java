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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class RezervasyonFoundationMigrationIntegrationTest {

    private static final String REZERVASYON_OLUSTURULDU = "REZERVASYON_OLUSTURULDU";
    private static final String REZERVASYON_IPTAL_EDILDI = "REZERVASYON_IPTAL_EDILDI";
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
            'IZIN_TALEBI_ONAYLANDI','IZIN_TALEBI_REDDEDILDI',
            'IZIN_TALEBI_IPTAL_EDILDI'
            """.replaceAll("\\s+", "");

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @Test
    void migrationLegacyRezervasyonuKorurVeYeniSozlesmeyiIdempotentKurar()
            throws Exception {

        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-26-add-room-reservations.sql"
        );

        try (Connection connection = DriverManager.getConnection(
                MYSQL.getJdbcUrl(),
                MYSQL.getUsername(),
                MYSQL.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                legacySemayiKur(statement);
            }

            migrationCalistir(connection, migration);

            int buyukSalonId;
            try (Statement statement = connection.createStatement()) {
                assertLegacyRezervasyonKorundu(statement);
                assertYeniKolonlar(statement);
                assertConstraintVeIndeksler(statement);
                buyukSalonId = assertOdaSeedleri(statement);
                assertAuditEnumVeInsert(statement);
            }

            migrationCalistir(connection, migration);

            try (Statement statement = connection.createStatement()) {
                assertLegacyRezervasyonKorundu(statement);
                assertEquals(3, tekInt(statement, "SELECT COUNT(*) FROM oda"));
                assertEquals(1, tekInt(statement, "SELECT COUNT(*) FROM rezervasyon"));
                assertEquals(3, tekInt(statement, "SELECT COUNT(*) FROM audit_log"));
                assertEquals(
                        buyukSalonId,
                        tekInt(statement, "SELECT oda_id FROM oda WHERE oda_ad = 'Büyük Salon'")
                );
                assertEquals(
                        12,
                        tekInt(statement, "SELECT oda_kapasite FROM oda WHERE oda_ad = 'Büyük Salon'")
                );
            }
        }
    }

    private void legacySemayiKur(Statement statement) throws Exception {
        statement.executeUpdate("DROP TABLE IF EXISTS rezervasyon");
        statement.executeUpdate("DROP TABLE IF EXISTS oda");
        statement.executeUpdate("DROP TABLE IF EXISTS audit_log");
        statement.executeUpdate("DROP TABLE IF EXISTS personel");

        statement.executeUpdate("""
                CREATE TABLE personel (
                    personel_id VARCHAR(36) PRIMARY KEY
                ) ENGINE=InnoDB
                """);
        statement.executeUpdate("""
                CREATE TABLE oda (
                    oda_id INT AUTO_INCREMENT PRIMARY KEY,
                    oda_ad VARCHAR(50) NOT NULL,
                    oda_kapasite INT NOT NULL,
                    CONSTRAINT uk_oda_ad UNIQUE (oda_ad)
                ) ENGINE=InnoDB
                """);
        statement.executeUpdate("""
                CREATE TABLE rezervasyon (
                    rezervasyon_id VARCHAR(36) PRIMARY KEY,
                    olusturan_id VARCHAR(36) NOT NULL,
                    baslik VARCHAR(50) NOT NULL,
                    tarih DATE NOT NULL,
                    baslangic_saat INT NOT NULL,
                    bitis_saat INT NOT NULL,
                    oda_id INT NOT NULL,
                    CONSTRAINT uk_rezervasyon_tarih_oda_baslangic
                        UNIQUE (tarih, oda_id, baslangic_saat),
                    CONSTRAINT fk_rezervasyon_olusturan
                        FOREIGN KEY (olusturan_id) REFERENCES personel(personel_id),
                    CONSTRAINT fk_rezervasyon_oda
                        FOREIGN KEY (oda_id) REFERENCES oda(oda_id)
                ) ENGINE=InnoDB
                """);

        statement.executeUpdate("""
                CREATE TABLE audit_log (
                    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    eylem_tipi ENUM(%s) NOT NULL
                ) ENGINE=InnoDB
                """.formatted(LEGACY_AUDIT_ENUM));

        statement.executeUpdate("""
                INSERT INTO personel (personel_id)
                VALUES ('owner-1'), ('admin-1')
                """);
        statement.executeUpdate("""
                INSERT INTO oda (oda_ad, oda_kapasite)
                VALUES ('Eski Oda', 4), ('Büyük Salon', 99)
                """);
        statement.executeUpdate("""
                INSERT INTO rezervasyon (
                    rezervasyon_id,
                    olusturan_id,
                    baslik,
                    tarih,
                    baslangic_saat,
                    bitis_saat,
                    oda_id
                )
                SELECT
                    'legacy-rezervasyon',
                    'owner-1',
                    'Eski toplantı',
                    '2026-09-15',
                    10,
                    12,
                    oda_id
                FROM oda
                WHERE oda_ad = 'Eski Oda'
                """);
        statement.executeUpdate("""
                INSERT INTO audit_log (eylem_tipi)
                VALUES ('LOGIN_BASARILI')
                """);
    }

    private void assertLegacyRezervasyonKorundu(Statement statement) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT
                    r.rezervasyon_id,
                    r.olusturan_id,
                    r.baslik,
                    r.tarih,
                    r.baslangic_saat,
                    r.bitis_saat,
                    o.oda_ad,
                    r.katilimci_sayisi,
                    r.durum,
                    r.olusturma_zamani
                FROM rezervasyon r
                JOIN oda o ON o.oda_id = r.oda_id
                WHERE r.rezervasyon_id = 'legacy-rezervasyon'
                """)) {
            resultSet.next();
            assertEquals("owner-1", resultSet.getString("olusturan_id"));
            assertEquals("Eski toplantı", resultSet.getString("baslik"));
            assertEquals("2026-09-15", resultSet.getDate("tarih").toString());
            assertEquals(10, resultSet.getInt("baslangic_saat"));
            assertEquals(12, resultSet.getInt("bitis_saat"));
            assertEquals("Eski Oda", resultSet.getString("oda_ad"));
            assertEquals(1, resultSet.getInt("katilimci_sayisi"));
            assertEquals("AKTIF", resultSet.getString("durum"));
            assertNotNull(resultSet.getTimestamp("olusturma_zamani"));
        }
    }

    private void assertYeniKolonlar(Statement statement) throws Exception {
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'katilimci_sayisi'
                AND data_type = 'int'
                AND is_nullable = 'NO'
                """));
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'durum'
                AND data_type = 'varchar'
                AND character_maximum_length = 30
                AND is_nullable = 'NO'
                """));
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'olusturma_zamani'
                AND data_type = 'datetime'
                AND is_nullable = 'NO'
                """));
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'iptal_zamani'
                AND data_type = 'datetime'
                AND is_nullable = 'YES'
                """));
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'iptal_eden_id'
                AND data_type = 'varchar'
                AND character_maximum_length = 36
                AND is_nullable = 'YES'
                """));
        assertEquals(1, kolonSayisi(statement, """
                column_name = 'iptal_nedeni'
                AND data_type = 'varchar'
                AND character_maximum_length = 500
                AND is_nullable = 'YES'
                """));
    }

    private void assertConstraintVeIndeksler(Statement statement) throws Exception {
        assertEquals(
                0,
                tekInt(statement, """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_schema = tc.constraint_schema
                         AND kcu.table_name = tc.table_name
                         AND kcu.constraint_name = tc.constraint_name
                        WHERE tc.constraint_schema = DATABASE()
                          AND tc.table_name = 'rezervasyon'
                          AND tc.constraint_type = 'UNIQUE'
                        GROUP BY tc.constraint_name
                        HAVING GROUP_CONCAT(
                            kcu.column_name
                            ORDER BY kcu.ordinal_position
                            SEPARATOR ','
                        ) = 'tarih,oda_id,baslangic_saat'
                        """, true)
        );

        assertEquals(
                "oda_id,tarih,durum,baslangic_saat,bitis_saat",
                indeksKolonlari(statement, "idx_rez_oda_tarih_durum_saat")
        );
        assertEquals(
                "olusturan_id,tarih,durum,baslangic_saat,bitis_saat",
                indeksKolonlari(statement, "idx_rez_owner_tarih_durum_saat")
        );
        assertEquals(
                "tarih,baslangic_saat,rezervasyon_id",
                indeksKolonlari(statement, "idx_rez_admin_tarih_sira")
        );

        SQLException durumHatasi = assertThrows(
                SQLException.class,
                () -> statement.executeUpdate("""
                        UPDATE rezervasyon
                        SET durum = 'BOZUK'
                        WHERE rezervasyon_id = 'legacy-rezervasyon'
                        """)
        );
        assertEquals(3819, durumHatasi.getErrorCode());

        SQLException kapasiteHatasi = assertThrows(
                SQLException.class,
                () -> statement.executeUpdate("""
                        INSERT INTO oda (oda_ad, oda_kapasite)
                        VALUES ('Geçersiz Oda', 0)
                        """)
        );
        assertEquals(3819, kapasiteHatasi.getErrorCode());
    }

    private int assertOdaSeedleri(Statement statement) throws Exception {
        assertEquals(1, tekInt(statement, "SELECT COUNT(*) FROM oda WHERE oda_ad = 'Büyük Salon'"));
        assertEquals(12, tekInt(statement, "SELECT oda_kapasite FROM oda WHERE oda_ad = 'Büyük Salon'"));
        assertEquals(1, tekInt(statement, "SELECT COUNT(*) FROM oda WHERE oda_ad = 'Küçük Oda'"));
        assertEquals(6, tekInt(statement, "SELECT oda_kapasite FROM oda WHERE oda_ad = 'Küçük Oda'"));
        assertEquals(1, tekInt(statement, "SELECT COUNT(*) FROM oda WHERE oda_ad = 'Eski Oda'"));
        return tekInt(statement, "SELECT oda_id FROM oda WHERE oda_ad = 'Büyük Salon'");
    }

    private void assertAuditEnumVeInsert(Statement statement) throws Exception {
        String columnType;
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT column_type
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'audit_log'
                  AND column_name = 'eylem_tipi'
                """)) {
            resultSet.next();
            columnType = resultSet.getString(1);
        }

        Set<String> beklenen = enumTokenlariniCoz(
                "enum(" + LEGACY_AUDIT_ENUM + ")"
        );
        beklenen.add(REZERVASYON_OLUSTURULDU);
        beklenen.add(REZERVASYON_IPTAL_EDILDI);
        assertEquals(beklenen, enumTokenlariniCoz(columnType));

        statement.executeUpdate("""
                INSERT INTO audit_log (eylem_tipi)
                VALUES
                    ('REZERVASYON_OLUSTURULDU'),
                    ('REZERVASYON_IPTAL_EDILDI')
                """);
    }

    private Set<String> enumTokenlariniCoz(String columnType) {
        String govde = columnType.substring("enum(".length(), columnType.length() - 1);
        return Arrays.stream(govde.split(","))
                .map(String::trim)
                .map(token -> token.substring(1, token.length() - 1))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int kolonSayisi(Statement statement, String kosul) throws Exception {
        return tekInt(statement, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'rezervasyon'
                  AND %s
                """.formatted(kosul));
    }

    private String indeksKolonlari(Statement statement, String indeksAdi) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'rezervasyon'
                  AND index_name = '%s'
                """.formatted(indeksAdi))) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private int tekInt(Statement statement, String sql) throws Exception {
        return tekInt(statement, sql, false);
    }

    private int tekInt(Statement statement, String sql, boolean bosSonucSifir) throws Exception {
        try (ResultSet resultSet = statement.executeQuery(sql)) {
            if (!resultSet.next()) {
                if (bosSonucSifir) {
                    return 0;
                }
                throw new SQLException("Sorgu sonuç döndürmedi.");
            }
            return resultSet.getInt(1);
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
}
