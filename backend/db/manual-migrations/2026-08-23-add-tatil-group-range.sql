-- Amaç:
-- Mevcut tek günlük tatil kayıtlarını veri kaybetmeden dahil başlangıç/bitiş
-- aralığı taşıyan tatil grubu şemasına yükseltmek ve tüm takvim mutation'larını
-- seri hale getirecek tek kilit satırını hazırlamak.
--
-- Production ortamında çalıştırmadan önce:
-- 1. Uygulama trafiğini durdurun veya bakım moduna alın.
-- 2. Veritabanı yedeği alın.
-- 3. Aşağıdaki ön kontrollerde `tatil` tablosunun bulunduğunu doğrulayın.

SELECT COUNT(*) AS tatil_tablosu_sayisi
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'tatil';

SELECT
    column_name,
    column_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'tatil'
  AND column_name IN ('tarih', 'bitis_tarihi');

-- Kolon yoksa önce nullable ekle; böylece mevcut satırlar güvenle taşınabilir.
SET @bitis_ekle_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE tatil ADD COLUMN bitis_tarihi DATE NULL AFTER tarih',
        'SELECT ''tatil.bitis_tarihi zaten mevcut'' AS bilgi'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'tatil'
      AND column_name = 'bitis_tarihi'
);

PREPARE bitis_ekle_statement FROM @bitis_ekle_sql;
EXECUTE bitis_ekle_statement;
DEALLOCATE PREPARE bitis_ekle_statement;

-- Eski tek günlük kayıtların bitişi başlangıç tarihiyle aynıdır.
UPDATE tatil
SET bitis_tarihi = tarih
WHERE bitis_tarihi IS NULL;

ALTER TABLE tatil
    MODIFY COLUMN bitis_tarihi DATE NOT NULL;

-- Şirket genelindeki tatil mutation'larını transaction içinde seri hale
-- getirecek singleton kilit kaynağı.
CREATE TABLE IF NOT EXISTS tatil_takvimi_kilit (
    kilit_id TINYINT NOT NULL,
    PRIMARY KEY (kilit_id)
);

INSERT IGNORE INTO tatil_takvimi_kilit (kilit_id)
VALUES (1);

-- Bitiş başlangıçtan önce olamaz. Constraint varsa adım atlanır.
SET @check_ekle_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE tatil ADD CONSTRAINT chk_tatil_tarih_araligi CHECK (bitis_tarihi >= tarih)',
        'SELECT ''chk_tatil_tarih_araligi zaten mevcut'' AS bilgi'
    )
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'tatil'
      AND constraint_name = 'chk_tatil_tarih_araligi'
);

PREPARE check_ekle_statement FROM @check_ekle_sql;
EXECUTE check_ekle_statement;
DEALLOCATE PREPARE check_ekle_statement;

-- Kesişim sorgusunun başlangıç/bitiş taramasını destekleyen index.
SET @index_ekle_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'CREATE INDEX idx_tatil_tarih_araligi ON tatil (tarih, bitis_tarihi)',
        'SELECT ''idx_tatil_tarih_araligi zaten mevcut'' AS bilgi'
    )
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'tatil'
      AND index_name = 'idx_tatil_tarih_araligi'
);

PREPARE index_ekle_statement FROM @index_ekle_sql;
EXECUTE index_ekle_statement;
DEALLOCATE PREPARE index_ekle_statement;

-- Migration sonrası doğrulama sorguları.
SELECT
    column_name,
    column_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'tatil'
  AND column_name = 'bitis_tarihi';

SELECT COUNT(*) AS tarih_araligi_constraint_sayisi
FROM information_schema.table_constraints
WHERE constraint_schema = DATABASE()
  AND table_name = 'tatil'
  AND constraint_name = 'chk_tatil_tarih_araligi';

SELECT COUNT(DISTINCT index_name) AS tarih_araligi_index_sayisi
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'tatil'
  AND index_name = 'idx_tatil_tarih_araligi';

SELECT COUNT(*) AS null_bitis_tarihi_sayisi
FROM tatil
WHERE bitis_tarihi IS NULL;

SELECT COUNT(*) AS takvim_kilit_satiri_sayisi
FROM tatil_takvimi_kilit
WHERE kilit_id = 1;

-- Otomatik rollback bilinçli olarak eklenmemiştir. Kolonu silmek çok günlük
-- tatil gruplarının bitiş bilgisini geri dönüşsüz kaybettirebilir.
