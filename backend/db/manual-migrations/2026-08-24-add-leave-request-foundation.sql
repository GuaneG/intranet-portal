ALTER TABLE durum_turu
    MODIFY COLUMN durum_adi
    ENUM('ONAY_BEKLIYOR','ONAYLANDI','REDDEDILDI','IPTAL_EDILDI')
    NOT NULL;

INSERT INTO durum_turu (durum_adi)
SELECT 'ONAY_BEKLIYOR'
WHERE NOT EXISTS (
    SELECT 1 FROM durum_turu WHERE durum_adi = 'ONAY_BEKLIYOR'
);

INSERT INTO durum_turu (durum_adi)
SELECT 'ONAYLANDI'
WHERE NOT EXISTS (
    SELECT 1 FROM durum_turu WHERE durum_adi = 'ONAYLANDI'
);

INSERT INTO durum_turu (durum_adi)
SELECT 'REDDEDILDI'
WHERE NOT EXISTS (
    SELECT 1 FROM durum_turu WHERE durum_adi = 'REDDEDILDI'
);

INSERT INTO durum_turu (durum_adi)
SELECT 'IPTAL_EDILDI'
WHERE NOT EXISTS (
    SELECT 1 FROM durum_turu WHERE durum_adi = 'IPTAL_EDILDI'
);

INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi)
SELECT 'Yıllık İzin', TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM izin_turu WHERE izin_turu_adi = 'Yıllık İzin'
);

INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi)
SELECT 'Hastalık İzni', FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM izin_turu WHERE izin_turu_adi = 'Hastalık İzni'
);

INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi)
SELECT 'Mazeret İzni', FALSE
WHERE NOT EXISTS (
    SELECT 1 FROM izin_turu WHERE izin_turu_adi = 'Mazeret İzni'
);

ALTER TABLE audit_log
    MODIFY COLUMN eylem_tipi
    ENUM('LOGIN_BASARILI','LOGIN_BASARISIZ','LOGOUT','TOKEN_REUSE_ALARM','ROL_DEGISTIRILDI','PERSONEL_EKLENDI','PAROLA_DEGISTIRILDI','PAROLA_SIFIRLANDI','PERSONEL_GUNCELLENDI','PERSONEL_PASIFE_ALINDI','PERSONEL_AKTIFLESTIRILDI','PROFIL_GUNCELLENDI','PROFIL_FOTOGRAFI_GUNCELLENDI','PROFIL_FOTOGRAFI_SILINDI','YETENEK_EKLENDI','YETENEK_GUNCELLENDI','YETENEK_SILINDI','DEPARTMAN_EKLENDI','DEPARTMAN_GUNCELLENDI','DEPARTMAN_SILINDI','TATIL_EKLENDI','TATIL_GUNCELLENDI','TATIL_SILINDI','IZIN_TALEBI_OLUSTURULDU')
    NOT NULL;

SET @index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'izin_bilgileri'
      AND index_name = 'idx_izin_personel_durum_tarih'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_izin_personel_durum_tarih ON izin_bilgileri (personel_id, izin_durum_id, baslangic_tarihi, bitis_tarihi)',
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
      AND index_name = 'idx_izin_personel_talep_sirasi'
);
SET @sql = IF(
    @index_exists = 0,
    'CREATE INDEX idx_izin_personel_talep_sirasi ON izin_bilgileri (personel_id, talep_tarihi, izin_id)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
