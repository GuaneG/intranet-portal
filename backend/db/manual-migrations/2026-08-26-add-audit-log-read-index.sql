-- Amaç:
-- Admin audit kayıtları sayfalı olarak en yeni kayıttan eskiye okunurken kullanılan
-- ORDER BY olusma_zamani DESC, log_id DESC sırasını kalıcı bir bileşik indeksle
-- desteklemek.
--
-- Çalıştırmadan önce:
-- 1. Veritabanı yedeği alın.
-- 2. `audit_log` tablosunun mevcut olduğunu doğrulayın.

SELECT COUNT(*) AS audit_log_tablosu_sayisi
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'audit_log';

-- İndeks yoksa ekle; varsa migration tekrarında güvenli biçimde atla.
SET @audit_okuma_indeksi_ekle_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_audit_log_olusma_log_id ON audit_log (olusma_zamani, log_id)',
        'SELECT ''idx_audit_log_olusma_log_id zaten mevcut; indeks ekleme atlandı.'' AS bilgi'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'audit_log'
      AND index_name = 'idx_audit_log_olusma_log_id'
);

PREPARE audit_okuma_indeksi_statement FROM @audit_okuma_indeksi_ekle_sql;
EXECUTE audit_okuma_indeksi_statement;
DEALLOCATE PREPARE audit_okuma_indeksi_statement;

-- Son kontrol: sırasıyla olusma_zamani ve log_id kolonları görünmelidir.
SELECT
    index_name,
    seq_in_index,
    column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'audit_log'
  AND index_name = 'idx_audit_log_olusma_log_id'
ORDER BY seq_in_index;

-- Rollback (gerekirse bakım penceresinde manuel uygulanır):
-- DROP INDEX idx_audit_log_olusma_log_id ON audit_log;
