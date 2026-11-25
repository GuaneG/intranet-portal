SET @column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'izin_bilgileri'
      AND column_name = 'karar_notu'
);
SET @sql = IF(
    @column_exists = 0,
    'ALTER TABLE izin_bilgileri ADD COLUMN karar_notu VARCHAR(500) NULL AFTER islem_tarihi',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE audit_log
    MODIFY COLUMN eylem_tipi
    ENUM('LOGIN_BASARILI','LOGIN_BASARISIZ','LOGOUT','TOKEN_REUSE_ALARM','ROL_DEGISTIRILDI','PERSONEL_EKLENDI','PAROLA_DEGISTIRILDI','PAROLA_SIFIRLANDI','PERSONEL_GUNCELLENDI','PERSONEL_PASIFE_ALINDI','PERSONEL_AKTIFLESTIRILDI','PROFIL_GUNCELLENDI','PROFIL_FOTOGRAFI_GUNCELLENDI','PROFIL_FOTOGRAFI_SILINDI','YETENEK_EKLENDI','YETENEK_GUNCELLENDI','YETENEK_SILINDI','DEPARTMAN_EKLENDI','DEPARTMAN_GUNCELLENDI','DEPARTMAN_SILINDI','TATIL_EKLENDI','TATIL_GUNCELLENDI','TATIL_SILINDI','IZIN_TALEBI_OLUSTURULDU','IZIN_TALEBI_ONAYLANDI','IZIN_TALEBI_REDDEDILDI')
    NOT NULL;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'izin_bilgileri'
      AND index_name = 'idx_izin_talep_sirasi'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_izin_talep_sirasi ON izin_bilgileri (talep_tarihi, izin_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'izin_bilgileri'
      AND index_name = 'idx_izin_durum_talep_sirasi'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_izin_durum_talep_sirasi ON izin_bilgileri (izin_durum_id, talep_tarihi, izin_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
