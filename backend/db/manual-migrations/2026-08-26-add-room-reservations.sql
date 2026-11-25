SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'katilimci_sayisi'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN katilimci_sayisi INT NULL AFTER oda_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'durum'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN durum VARCHAR(30) NULL AFTER katilimci_sayisi',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'olusturma_zamani'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN olusturma_zamani DATETIME(6) NULL AFTER durum',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'iptal_zamani'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN iptal_zamani DATETIME(6) NULL AFTER olusturma_zamani',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'iptal_eden_id'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN iptal_eden_id VARCHAR(36) NULL AFTER iptal_zamani',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND column_name = 'iptal_nedeni'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE rezervasyon ADD COLUMN iptal_nedeni VARCHAR(500) NULL AFTER iptal_eden_id',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE rezervasyon
SET katilimci_sayisi = COALESCE(katilimci_sayisi, 1),
    durum = COALESCE(durum, 'AKTIF'),
    olusturma_zamani = COALESCE(olusturma_zamani, CURRENT_TIMESTAMP(6));

ALTER TABLE rezervasyon
    MODIFY COLUMN katilimci_sayisi INT NOT NULL,
    MODIFY COLUMN durum VARCHAR(30) NOT NULL,
    MODIFY COLUMN olusturma_zamani DATETIME(6) NOT NULL;

SET @legacy_unique_name = (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND non_unique = 0
      AND index_name <> 'PRIMARY'
    GROUP BY index_name
    HAVING GROUP_CONCAT(
        column_name
        ORDER BY seq_in_index
        SEPARATOR ','
    ) = 'tarih,oda_id,baslangic_saat'
    LIMIT 1
);
SET @sql = IF(
    @legacy_unique_name IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE rezervasyon DROP INDEX `',
        REPLACE(@legacy_unique_name, '`', '``'),
        '`'
    )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND constraint_name = 'chk_rezervasyon_durum'
      AND constraint_type = 'CHECK'
);
SET @sql = IF(
    @constraint_exists = 0,
    'ALTER TABLE rezervasyon ADD CONSTRAINT chk_rezervasyon_durum CHECK (durum IN (''AKTIF'', ''IPTAL_EDILDI''))',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'oda'
      AND constraint_name = 'chk_oda_kapasite_pozitif'
      AND constraint_type = 'CHECK'
);
SET @sql = IF(
    @constraint_exists = 0,
    'ALTER TABLE oda ADD CONSTRAINT chk_oda_kapasite_pozitif CHECK (oda_kapasite > 0)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND constraint_name = 'fk_rezervasyon_iptal_eden'
      AND constraint_type = 'FOREIGN KEY'
);
SET @sql = IF(
    @constraint_exists = 0,
    'ALTER TABLE rezervasyon ADD CONSTRAINT fk_rezervasyon_iptal_eden FOREIGN KEY (iptal_eden_id) REFERENCES personel(personel_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND index_name = 'idx_rez_oda_tarih_durum_saat'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_rez_oda_tarih_durum_saat ON rezervasyon (oda_id, tarih, durum, baslangic_saat, bitis_saat)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND index_name = 'idx_rez_owner_tarih_durum_saat'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_rez_owner_tarih_durum_saat ON rezervasyon (olusturan_id, tarih, durum, baslangic_saat, bitis_saat)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'rezervasyon'
      AND index_name = 'idx_rez_admin_tarih_sira'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_rez_admin_tarih_sira ON rezervasyon (tarih, baslangic_saat, rezervasyon_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

INSERT INTO oda (oda_ad, oda_kapasite)
VALUES ('Büyük Salon', 12)
ON DUPLICATE KEY UPDATE oda_kapasite = 12;

INSERT INTO oda (oda_ad, oda_kapasite)
VALUES ('Küçük Oda', 6)
ON DUPLICATE KEY UPDATE oda_kapasite = 6;

ALTER TABLE audit_log
    MODIFY COLUMN eylem_tipi
    ENUM(
        'LOGIN_BASARILI',
        'LOGIN_BASARISIZ',
        'LOGOUT',
        'TOKEN_REUSE_ALARM',
        'ROL_DEGISTIRILDI',
        'PERSONEL_EKLENDI',
        'PAROLA_DEGISTIRILDI',
        'PAROLA_SIFIRLANDI',
        'PERSONEL_GUNCELLENDI',
        'PERSONEL_PASIFE_ALINDI',
        'PERSONEL_AKTIFLESTIRILDI',
        'PROFIL_GUNCELLENDI',
        'PROFIL_FOTOGRAFI_GUNCELLENDI',
        'PROFIL_FOTOGRAFI_SILINDI',
        'YETENEK_EKLENDI',
        'YETENEK_GUNCELLENDI',
        'YETENEK_SILINDI',
        'DEPARTMAN_EKLENDI',
        'DEPARTMAN_GUNCELLENDI',
        'DEPARTMAN_SILINDI',
        'TATIL_EKLENDI',
        'TATIL_GUNCELLENDI',
        'TATIL_SILINDI',
        'IZIN_TALEBI_OLUSTURULDU',
        'IZIN_TALEBI_ONAYLANDI',
        'IZIN_TALEBI_REDDEDILDI',
        'IZIN_TALEBI_IPTAL_EDILDI',
        'REZERVASYON_OLUSTURULDU',
        'REZERVASYON_IPTAL_EDILDI'
    ) NOT NULL;
