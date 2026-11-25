-- Amaç:
-- izin_bilgileri.is_gunu_sayisi kolonunu yarım günlük izinleri
-- kayıpsız saklayabilecek DECIMAL(4,1) tipine dönüştürmek.
--
-- Örnekler:
-- 14   -> 14.0
-- 3.5  -> 3.5
-- 366  -> 366.0
--
-- Production ortamında çalıştırmadan önce:
-- 1. Uygulama trafiğini durdurun veya bakım moduna alın.
-- 2. Veritabanı yedeği alın.
-- 3. Aşağıdaki ön kontrolleri inceleyin.

SELECT COUNT(*) AS izin_bilgileri_tablosu_sayisi
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'izin_bilgileri';

SELECT
    column_name,
    column_type,
    data_type,
    numeric_precision,
    numeric_scale,
    is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'izin_bilgileri'
  AND column_name = 'is_gunu_sayisi';

-- INT değerler DECIMAL(4,1) biçimine kayıpsız dönüşür.
-- Bu komut tekrar çalıştırılabilir; kolon zaten DECIMAL(4,1)
-- olduğunda mevcut ondalıklı değerleri değiştirmez.
ALTER TABLE izin_bilgileri
    MODIFY COLUMN is_gunu_sayisi DECIMAL(4,1) NOT NULL;

-- Dönüşüm sonrası kontrol.
SELECT
    column_name,
    column_type,
    data_type,
    numeric_precision,
    numeric_scale,
    is_nullable
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'izin_bilgileri'
  AND column_name = 'is_gunu_sayisi';

SELECT COUNT(*) AS null_is_gunu_sayisi
FROM izin_bilgileri
WHERE is_gunu_sayisi IS NULL;


select * from izin_bilgileri;
-- Beklenen:
-- data_type        = decimal
-- numeric_precision = 4
-- numeric_scale     = 1
-- is_nullable       = NO
-- null_is_gunu_sayisi = 0
--
-- UYARI:
-- INT tipine geri dönüş, 3.5 gibi ondalıklı değerlerde veri kaybına
-- yol açacağından otomatik rollback komutu bilinçli olarak verilmemiştir.