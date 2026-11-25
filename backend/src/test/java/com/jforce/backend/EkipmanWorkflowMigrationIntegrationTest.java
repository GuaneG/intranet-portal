package com.jforce.backend;

import com.jforce.backend.model.enums.AuditEylem;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class EkipmanWorkflowMigrationIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @BeforeEach
    void legacySemayiVeOrnekVerileriHazirla() throws SQLException {
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS audit_log");
            statement.execute("DROP TABLE IF EXISTS zimmetleme_bilgileri");
            statement.execute("DROP TABLE IF EXISTS ekipman_talep");
            statement.execute("DROP TABLE IF EXISTS ekipman_bilgileri");
            statement.execute("DROP TABLE IF EXISTS ekipman_tipi");
            statement.execute("CREATE TABLE ekipman_tipi (ekipman_tipi_id INT NOT NULL, tip_adi VARCHAR(50) NOT NULL, PRIMARY KEY (ekipman_tipi_id), UNIQUE KEY uk_ekipman_tipi_adi (tip_adi))");
            statement.execute("CREATE TABLE ekipman_bilgileri (ekipman_id VARCHAR(36) NOT NULL, ekipman_tipi_id INT NOT NULL, marka VARCHAR(50) NOT NULL, model VARCHAR(50) NULL, seri_no VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL, ekipman_durum VARCHAR(50) NOT NULL, PRIMARY KEY (ekipman_id), UNIQUE KEY uk_ekipman_seri_no (seri_no))");
            statement.execute("CREATE TABLE ekipman_talep (talep_id VARCHAR(36) NOT NULL, personel_id VARCHAR(36) NOT NULL, ekipman_tipi_id INT NOT NULL, aciklama TEXT NULL, talep_durum_id INT NOT NULL, talep_tarihi DATETIME NULL, islem_yapan_id VARCHAR(36) NULL, islem_tarihi DATETIME NULL, PRIMARY KEY (talep_id))");
            statement.execute("CREATE TABLE zimmetleme_bilgileri (zimmet_id VARCHAR(36) NOT NULL, ekipman_id VARCHAR(36) NOT NULL, personel_id VARCHAR(36) NOT NULL, teslim_tarihi DATETIME NOT NULL, iade_tarihi DATETIME NULL, PRIMARY KEY (zimmet_id))");
            statement.execute("CREATE TABLE audit_log (log_id VARCHAR(36) NOT NULL, eylem_tipi ENUM(" + legacyAuditEnum() + ") NOT NULL, PRIMARY KEY (log_id))");
            statement.execute("INSERT INTO ekipman_tipi (ekipman_tipi_id, tip_adi) VALUES (41, 'Dizüstü Bilgisayar')");
            statement.execute("INSERT INTO ekipman_bilgileri (ekipman_id, ekipman_tipi_id, marka, model, seri_no, ekipman_durum) VALUES ('11111111-1111-1111-1111-111111111111', 41, 'JForce', 'Legacy', 'SN-LEGACY', 'PERSONELDE')");
            statement.execute("INSERT INTO ekipman_talep (talep_id, personel_id, ekipman_tipi_id, aciklama, talep_durum_id, talep_tarihi) VALUES ('22222222-2222-2222-2222-222222222222', 'personel-legacy', 41, 'Eski talep', 7, NULL)");
            statement.execute("INSERT INTO zimmetleme_bilgileri (zimmet_id, ekipman_id, personel_id, teslim_tarihi, iade_tarihi) VALUES ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'personel-legacy', '2026-08-01 09:00:00', '2026-08-02 09:00:00')");
            statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) VALUES ('legacy-audit', 'LOGIN_BASARILI')");
        }
    }

    @Test
    void mysql8TurkceCaseInsensitiveCollationKulaklikVaryantlariniEsitKabulEder()
            throws Exception {
        try (Connection connection = baglan()) {
            assertEquals(
                    1,
                    tekInt(connection, """
                            SELECT COUNT(*)
                            FROM information_schema.collations
                            WHERE collation_name = 'utf8mb4_tr_0900_ai_ci'
                              AND character_set_name = 'utf8mb4'
                            """)
            );
            assertEquals(
                    1,
                    tekInt(connection, """
                            SELECT CONVERT('Kulaklık' USING utf8mb4)
                                       COLLATE utf8mb4_tr_0900_ai_ci
                                   = CONVERT('KULAKLIK' USING utf8mb4)
                                       COLLATE utf8mb4_tr_0900_ai_ci
                            """)
            );
        }
    }

    @Test
    void mysql8TeknikSeriNoCollationCaseInsensitiveVeAccentSensitiveCalisir()
            throws Exception {
        try (Connection connection = baglan()) {
            assertEquals(
                    1,
                    tekInt(connection, """
                            SELECT COUNT(*)
                            FROM information_schema.collations
                            WHERE collation_name = 'utf8mb4_0900_as_ci'
                              AND character_set_name = 'utf8mb4'
                            """)
            );
            assertEquals(
                    1,
                    tekInt(connection, """
                            SELECT CONVERT('SN-Abc-123' USING utf8mb4)
                                       COLLATE utf8mb4_0900_as_ci
                                   = CONVERT('sn-aBC-123' USING utf8mb4)
                                       COLLATE utf8mb4_0900_as_ci
                            """)
            );
            assertEquals(
                    0,
                    tekInt(connection, """
                            SELECT CONVERT('SÉRI-123' USING utf8mb4)
                                       COLLATE utf8mb4_0900_as_ci
                                   = CONVERT('SERI-123' USING utf8mb4)
                                       COLLATE utf8mb4_0900_as_ci
                            """)
            );
        }
    }

    @Test
    void migration_legacy_veriyi_korur_degismezleri_ekler_ve_idempotenttir() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan()) {
            assertFalse(kolonVarMi(connection, "ekipman_tipi", "aktif_mi"));
            assertFalse(kolonVarMi(connection, "ekipman_bilgileri", "kullanim_disi_nedeni"));
            assertFalse(kolonVarMi(connection, "ekipman_talep", "karar_notu"));
            assertFalse(kolonVarMi(connection, "zimmetleme_bilgileri", "talep_id"));

            migrationCalistir(connection, migration);

            assertBooleanKolon(connection, "ekipman_tipi", "aktif_mi", "NO");
            assertEquals(
                    "utf8mb4_tr_0900_ai_ci",
                    tekMetin(connection, """
                            SELECT collation_name
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'ekipman_tipi'
                              AND column_name = 'tip_adi'
                            """)
            );
            assertEquals(
                    "utf8mb4_0900_as_ci",
                    tekMetin(connection, """
                            SELECT collation_name
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name = 'ekipman_bilgileri'
                              AND column_name = 'seri_no'
                            """)
            );
            assertIndex(
                    connection,
                    "ekipman_bilgileri",
                    "uk_ekipman_seri_no",
                    List.of("seri_no"),
                    0
            );
            assertKolon(connection, "ekipman_bilgileri", "kullanim_disi_nedeni", "varchar", 500L, "YES");
            assertKolon(connection, "ekipman_talep", "karar_notu", "varchar", 500L, "YES");
            assertEquals(1, tekInt(connection, "SELECT aktif_mi FROM ekipman_tipi WHERE ekipman_tipi_id = 41"));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM ekipman_talep WHERE talep_id = '22222222-2222-2222-2222-222222222222' AND talep_tarihi IS NOT NULL"));
            assertEquals("Eski talep", tekMetin(connection, "SELECT aciklama FROM ekipman_talep WHERE talep_id = '22222222-2222-2222-2222-222222222222'"));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333' AND iade_tarihi IS NOT NULL"));
            assertEquals("LOGIN_BASARILI", tekMetin(connection, "SELECT eylem_tipi FROM audit_log WHERE log_id = 'legacy-audit'"));
            assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
            assertEquals("NO", tekMetin(connection, "SELECT is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'ekipman_talep' AND column_name = 'talep_tarihi'"));
            assertIndex(connection, "ekipman_talep", "idx_ekipman_talep_personel_tip_durum_tarih", List.of("personel_id", "ekipman_tipi_id", "talep_durum_id", "talep_tarihi", "talep_id"), 1);
            assertIndex(connection, "ekipman_talep", "idx_ekipman_talep_tip_durum_tarih", List.of("ekipman_tipi_id", "talep_durum_id", "talep_tarihi", "talep_id"), 1);
            assertIndex(connection, "ekipman_bilgileri", "idx_ekipman_tip_durum_id", List.of("ekipman_tipi_id", "ekipman_durum", "ekipman_id"), 1);
            assertIndex(connection, "zimmetleme_bilgileri", "idx_zimmet_personel_teslim_zimmet", List.of("personel_id", "teslim_tarihi", "zimmet_id"), 1);
            assertIndex(connection, "zimmetleme_bilgileri", "idx_zimmet_ekipman_iade_zimmet", List.of("ekipman_id", "iade_tarihi", "zimmet_id"), 1);
            assertIndex(connection, "zimmetleme_bilgileri", "uk_zimmet_talep", List.of("talep_id"), 0);
            assertIndex(connection, "zimmetleme_bilgileri", "uk_zimmet_acik_ekipman", List.of("acik_ekipman_id"), 0);
            assertForeignKey(connection, "zimmetleme_bilgileri", "fk_zimmet_talep", "talep_id", "ekipman_talep", "talep_id");
            assertGeneratedKolon(
                    connection,
                    "zimmetleme_bilgileri",
                    "acik_ekipman_id",
                    "varchar",
                    36L,
                    "YES",
                    "STORED GENERATED",
                    "CASE WHEN iade_tarihi IS NULL THEN ekipman_id ELSE NULL END");
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_schema = DATABASE() AND constraint_name = 'chk_zimmet_iade_tarihi' AND check_clause = '((`iade_tarihi` is null) or (`iade_tarihi` >= `teslim_tarihi`))'"));
            try (Statement statement = connection.createStatement()) {
                statement.execute("UPDATE ekipman_bilgileri SET ekipman_durum = 'KULLANIM_DISI' WHERE ekipman_id = '11111111-1111-1111-1111-111111111111'");
            }
            assertEquals("KULLANIM_DISI", tekMetin(connection, "SELECT ekipman_durum FROM ekipman_bilgileri WHERE ekipman_id = '11111111-1111-1111-1111-111111111111'"));

            try (Statement statement = connection.createStatement()) {
                statement.execute("INSERT INTO audit_log (log_id, eylem_tipi) VALUES ('new-audit', 'ZIMMET_IADE_EDILDI')");
                statement.execute("INSERT INTO zimmetleme_bilgileri (zimmet_id, ekipman_id, personel_id, talep_id, teslim_tarihi) VALUES ('44444444-4444-4444-4444-444444444444', '11111111-1111-1111-1111-111111111111', 'personel-new', '22222222-2222-2222-2222-222222222222', '2026-08-03 09:00:00')");
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO zimmetleme_bilgileri (zimmet_id, ekipman_id, personel_id, teslim_tarihi) VALUES ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', 'personel-other', '2026-08-04 09:00:00')"));
                assertThrows(SQLException.class, () -> statement.execute("INSERT INTO zimmetleme_bilgileri (zimmet_id, ekipman_id, personel_id, teslim_tarihi, iade_tarihi) VALUES ('66666666-6666-6666-6666-666666666666', 'other-equipment', 'personel-other', '2026-08-05 09:00:00', '2026-08-04 09:00:00')"));
            }

            migrationCalistir(connection, migration);

            assertEquals(2, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri"));
            assertEquals("ZIMMET_IADE_EDILDI", tekMetin(connection, "SELECT eylem_tipi FROM audit_log WHERE log_id = 'new-audit'"));
            assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
        }
    }

    @Test
    void migrationUniqueIndeksEksikseTurkceCaseInsensitiveTekilligiKurar()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-28-add-equipment-workflow.sql"
        );
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute(
                    "ALTER TABLE ekipman_tipi DROP INDEX uk_ekipman_tipi_adi"
            );

            migrationCalistir(connection, migration);

            assertIndex(
                    connection,
                    "ekipman_tipi",
                    "uk_ekipman_tipi_adi",
                    List.of("tip_adi"),
                    0
            );
            statement.execute("""
                    INSERT INTO ekipman_tipi
                        (ekipman_tipi_id, tip_adi, aktif_mi)
                    VALUES (42, 'Kulaklık', TRUE)
                    """);
            assertThrows(SQLException.class, () -> statement.execute("""
                    INSERT INTO ekipman_tipi
                        (ekipman_tipi_id, tip_adi, aktif_mi)
                    VALUES (43, 'KULAKLIK', TRUE)
                    """));

            migrationCalistir(connection, migration);
            assertEquals(
                    1,
                    tekInt(connection, """
                            SELECT COUNT(*)
                            FROM ekipman_tipi
                            WHERE tip_adi = 'KULAKLIK'
                            """)
            );
        }
    }

    @Test
    void migrationTurkceCaseCakismasindaVeriyiBirlestirmedenFailFastDurur()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-28-add-equipment-workflow.sql"
        );
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO ekipman_tipi (ekipman_tipi_id, tip_adi)
                    VALUES (42, 'Kulaklık'), (43, 'KULAKLIK')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> migrationCalistir(connection, migration)
            );

            assertTrue(
                    exception.getMessage().contains(
                            "migration_ekipman_tipi_turkce_case_cakismasi"
                    ),
                    () -> "Beklenmeyen migration hatası: "
                            + exception.getMessage()
            );
            assertEquals(
                    2,
                    tekInt(connection, """
                            SELECT COUNT(*)
                            FROM ekipman_tipi
                            WHERE tip_adi IN ('Kulaklık', 'KULAKLIK')
                            """)
            );
            assertFalse(kolonVarMi(connection, "ekipman_tipi", "aktif_mi"));
        }
    }

    @Test
    void migrationSeriNoCaseCakismasindaVeriyiBirlestirmedenFailFastDurur()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-28-add-equipment-workflow.sql"
        );
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ekipman_bilgileri DROP INDEX uk_ekipman_seri_no");
            statement.execute("""
                    INSERT INTO ekipman_bilgileri
                        (ekipman_id, ekipman_tipi_id, marka, model, seri_no, ekipman_durum)
                    VALUES
                        ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 41, 'JForce', NULL, 'CASE-SERI', 'DEPODA'),
                        ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 41, 'JForce', NULL, 'case-seri', 'DEPODA')
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> migrationCalistir(connection, migration)
            );

            assertTrue(
                    exception.getMessage().contains(
                            "migration_ekipman_seri_no_case_cakismasi"
                    ),
                    () -> "Beklenmeyen migration hatası: " + exception.getMessage()
            );
            assertEquals(
                    2,
                    tekInt(connection, """
                            SELECT COUNT(*)
                            FROM ekipman_bilgileri
                            WHERE LOWER(seri_no) = 'case-seri'
                            """)
            );
            assertFalse(kolonVarMi(connection, "ekipman_tipi", "aktif_mi"));
        }
    }

    @Test
    void migrationYanlisAyniAdliSeriNoIndeksiyleFailFastDurur()
            throws Exception {
        Path migration = Path.of(
                System.getProperty("user.dir"),
                "db",
                "manual-migrations",
                "2026-08-28-add-equipment-workflow.sql"
        );
        try (Connection connection = baglan();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE ekipman_bilgileri DROP INDEX uk_ekipman_seri_no");
            statement.execute("""
                    CREATE INDEX uk_ekipman_seri_no
                    ON ekipman_bilgileri (marka, seri_no)
                    """);

            SQLException exception = assertThrows(
                    SQLException.class,
                    () -> migrationCalistir(connection, migration)
            );
            assertTrue(
                    exception.getMessage().contains(
                            "Duplicate key name 'uk_ekipman_seri_no'"
                    ),
                    () -> "Beklenmeyen migration hatası: " + exception.getMessage()
            );
            assertIndex(
                    connection,
                    "ekipman_bilgileri",
                    "uk_ekipman_seri_no",
                    List.of("marka", "seri_no"),
                    1
            );
        }
    }

    @Test
    void migration_ayni_adli_yanlis_indeksle_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX idx_ekipman_talep_personel_tip_durum_tarih ON ekipman_talep (personel_id, talep_id)");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));

            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM ekipman_talep WHERE talep_id = '22222222-2222-2222-2222-222222222222'"));
            assertIndex(
                    connection,
                    "ekipman_talep",
                    "idx_ekipman_talep_personel_tip_durum_tarih",
                    List.of("personel_id", "talep_id"),
                    1);
        }
    }

    @Test
    void migration_ayni_adli_yanlis_generated_kolon_ve_fk_ile_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN acik_ekipman_id VARCHAR(36) GENERATED ALWAYS AS (ekipman_id) STORED");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333'"));
        }
    }

    @Test
    void migration_ayni_adli_yanlis_check_ile_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD CONSTRAINT chk_zimmet_iade_tarihi CHECK (iade_tarihi IS NULL OR iade_tarihi > teslim_tarihi)");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333'"));
        }
    }

    @Test
    void migration_ayni_normalize_metnine_sahip_farkli_gruplu_check_ile_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM zimmetleme_bilgileri");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN talep_id VARCHAR(36) NULL");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN acik_ekipman_id VARCHAR(36) GENERATED ALWAYS AS (CASE WHEN iade_tarihi IS NULL THEN ekipman_id ELSE NULL END) STORED");
            statement.execute("CREATE UNIQUE INDEX uk_zimmet_talep ON zimmetleme_bilgileri (talep_id)");
            statement.execute("CREATE UNIQUE INDEX uk_zimmet_acik_ekipman ON zimmetleme_bilgileri (acik_ekipman_id)");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD CONSTRAINT fk_zimmet_talep FOREIGN KEY (talep_id) REFERENCES ekipman_talep (talep_id) ON UPDATE RESTRICT ON DELETE RESTRICT");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD CONSTRAINT chk_zimmet_iade_tarihi CHECK ((iade_tarihi IS NULL OR iade_tarihi) >= teslim_tarihi)");

            SQLException exception = assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertTrue(
                    exception.getMessage().contains("Duplicate check constraint name 'chk_zimmet_iade_tarihi'"),
                    () -> "Beklenmeyen migration hatası: " + exception.getMessage());
            assertEquals(0, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri"));
        }
    }

    @Test
    void migration_ayni_adli_yanlis_unique_indeksle_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN talep_id VARCHAR(36) NULL");
            statement.execute("CREATE INDEX uk_zimmet_talep ON zimmetleme_bilgileri (talep_id)");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333'"));
        }
    }

    @Test
    void migration_ayni_adli_yanlis_acik_zimmet_unique_indeksiyle_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN acik_ekipman_id VARCHAR(36) GENERATED ALWAYS AS (CASE WHEN iade_tarihi IS NULL THEN ekipman_id ELSE NULL END) STORED");
            statement.execute("CREATE INDEX uk_zimmet_acik_ekipman ON zimmetleme_bilgileri (acik_ekipman_id)");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333'"));
        }
    }

    @Test
    void migration_ayni_adli_yanlis_fk_ile_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN talep_id VARCHAR(36) NULL");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD CONSTRAINT fk_zimmet_talep FOREIGN KEY (talep_id) REFERENCES ekipman_talep(talep_id) ON UPDATE CASCADE ON DELETE CASCADE");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM zimmetleme_bilgileri WHERE zimmet_id = '33333333-3333-3333-3333-333333333333'"));
        }
    }

    @Test
    void migration_ayni_adli_cok_kolonlu_fk_ile_fail_fast_durur() throws Exception {
        Path migration = Path.of(System.getProperty("user.dir"), "db", "manual-migrations", "2026-08-28-add-equipment-workflow.sql");
        try (Connection connection = baglan(); Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD COLUMN talep_id VARCHAR(36) NULL");
            statement.execute("CREATE UNIQUE INDEX uk_ekipman_talep_id_personel ON ekipman_talep (talep_id, personel_id)");
            statement.execute("ALTER TABLE zimmetleme_bilgileri ADD CONSTRAINT fk_zimmet_talep FOREIGN KEY (talep_id, personel_id) REFERENCES ekipman_talep(talep_id, personel_id) ON UPDATE RESTRICT ON DELETE RESTRICT");

            assertThrows(SQLException.class, () -> migrationCalistir(connection, migration));
            assertEquals(2, tekInt(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE() AND table_name = 'zimmetleme_bilgileri' AND constraint_name = 'fk_zimmet_talep'"));
        }
    }

    private Connection baglan() throws SQLException {
        return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
    }

    private String legacyAuditEnum() {
        return Arrays.stream(AuditEylem.values()).map(AuditEylem::name).filter(value -> !value.startsWith("EKIPMAN_") && !value.equals("ZIMMET_IADE_EDILDI")).map(value -> "'" + value + "'").reduce((left, right) -> left + "," + right).orElseThrow();
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

    private boolean kolonVarMi(Connection connection, String tablo, String kolon) throws SQLException {
        return tekInt(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '" + tablo + "' AND column_name = '" + kolon + "'") > 0;
    }

    private void assertKolon(Connection connection, String tablo, String kolon, String veriTipi, Long uzunluk, String nullable) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT data_type, character_maximum_length, is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, tablo);
            statement.setString(2, kolon);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals(veriTipi, resultSet.getString(1));
                assertEquals(uzunluk, resultSet.getObject(2, Long.class));
                assertEquals(nullable, resultSet.getString(3));
            }
        }
    }

    private void assertBooleanKolon(Connection connection, String tablo, String kolon, String nullable) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT data_type, numeric_precision, is_nullable FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, tablo);
            statement.setString(2, kolon);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                assertEquals("tinyint", resultSet.getString(1));
                assertEquals(3, resultSet.getInt(2));
                assertEquals(nullable, resultSet.getString(3));
            }
        }
    }

    private void assertForeignKey(Connection connection, String tablo, String constraint, String kolon, String referansTablo, String referansKolon) throws SQLException {
        assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE() AND table_name = '" + tablo + "' AND constraint_name = '" + constraint + "'"));
        assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE constraint_schema = DATABASE() AND table_name = '" + tablo + "' AND constraint_name = '" + constraint + "' AND ordinal_position = 1 AND column_name = '" + kolon + "' AND referenced_table_schema = DATABASE() AND referenced_table_name = '" + referansTablo + "' AND referenced_column_name = '" + referansKolon + "'"));
        assertEquals(1, tekInt(connection, "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema = DATABASE() AND table_name = '" + tablo + "' AND constraint_name = '" + constraint + "' AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'"));
    }

    private int tekInt(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getInt(1);
        }
    }

    private String tekMetin(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next());
            return resultSet.getString(1);
        }
    }

    private List<String> beklenenAuditEylemleri() {
        return Arrays.stream(AuditEylem.values()).map(AuditEylem::name).toList();
    }

    private List<String> auditEnumTokenlari(Connection connection) throws SQLException {
        String columnType = tekMetin(connection, "SELECT column_type FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'audit_log' AND column_name = 'eylem_tipi'");
        List<String> sonuc = new ArrayList<>();
        Matcher matcher = Pattern.compile("'([^']*)'").matcher(columnType);
        while (matcher.find()) {
            sonuc.add(matcher.group(1));
        }
        return sonuc;
    }

    private GeneratedColumnMetadata generatedKolonMetadata(Connection connection, String tablo, String kolon) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT data_type, character_maximum_length, is_nullable, extra, generation_expression FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, tablo);
            statement.setString(2, kolon);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return new GeneratedColumnMetadata(
                        resultSet.getString(1),
                        resultSet.getObject(2, Long.class),
                        resultSet.getString(3),
                        resultSet.getString(4),
                        resultSet.getString(5));
            }
        }
    }

    private void assertGeneratedKolon(Connection connection, String tablo, String kolon, String veriTipi, Long uzunluk,
                                      String nullable, String extra, String generationExpression) throws SQLException {
        GeneratedColumnMetadata actual = generatedKolonMetadata(connection, tablo, kolon);
        assertEquals(veriTipi, actual.dataType(), () -> "actual generated column metadata=" + actual);
        assertEquals(uzunluk, actual.characterMaximumLength(), () -> "actual generated column metadata=" + actual);
        assertEquals(nullable, actual.nullable(), () -> "actual generated column metadata=" + actual);
        assertEquals(extra, actual.extra(), () -> "actual generated column metadata=" + actual);
        assertEquals(
                normalizeGeneratedExpression(generationExpression),
                normalizeGeneratedExpression(actual.generationExpression()),
                () -> "actual generated column metadata=" + actual);
    }

    private String normalizeGeneratedExpression(String expression) {
        return expression.replace("`", "").replace(" ", "").replace("(", "").replace(")", "").toLowerCase(Locale.ROOT);
    }

    private void assertIndex(Connection connection, String tablo, String indeks, List<String> beklenenKolonlar, int nonUnique) throws SQLException {
        List<String> kolonlar = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT column_name, non_unique, index_type, sub_part FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? ORDER BY seq_in_index")) {
            statement.setString(1, tablo);
            statement.setString(2, indeks);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    kolonlar.add(resultSet.getString(1));
                    assertEquals(nonUnique, resultSet.getInt(2));
                    assertEquals("BTREE", resultSet.getString(3));
                    assertEquals(null, resultSet.getObject(4));
                }
            }
        }
        assertEquals(beklenenKolonlar, kolonlar);
    }

    private record GeneratedColumnMetadata(String dataType, Long characterMaximumLength, String nullable, String extra,
                                           String generationExpression) {
    }

}
