-- Amaç:
-- Personel optimistic locking için gereken `version` kolonunu mevcut MySQL
-- veritabanına kontrollü ve tekrar çalıştırılabilir biçimde eklemek.
--
-- Çalıştırmadan önce:
-- 1. Uygulama trafiğini durdurun veya bakım moduna alın.
-- 2. Veritabanı yedeği alın.
-- 3. Aşağıdaki ön kontrol sonucunda `personel` tablosunun bulunduğunu doğrulayın.

SELECT COUNT(*) AS personel_tablosu_sayisi
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name = 'personel';

SELECT COUNT(*) AS version_kolonu_sayisi
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'personel'
  AND column_name = 'version';

-- Kolon yoksa ekle; varsa bu adımı güvenli biçimde atla.
SET @version_kolonu_ekle_sql = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE personel ADD COLUMN version BIGINT NOT NULL DEFAULT 0',
        'SELECT ''personel.version zaten mevcut; kolon ekleme atlandı.'' AS bilgi'
    )
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'personel'
      AND column_name = 'version'
);

PREPARE version_kolonu_ekle_statement FROM @version_kolonu_ekle_sql;
EXECUTE version_kolonu_ekle_statement;
DEALLOCATE PREPARE version_kolonu_ekle_statement;

-- Eski satırlarda null kalmışsa güvenli başlangıç değerine getir.
UPDATE personel
SET version = 0
WHERE version IS NULL;

-- Kolon sözleşmesini kesinleştir.
ALTER TABLE personel
    MODIFY COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Son kontrol: kolon BIGINT, nullable=NO ve default=0 olmalıdır.
SELECT
    column_name,
    column_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'personel'
  AND column_name = 'version';

SELECT COUNT(*) AS null_version_satiri
FROM personel
WHERE version IS NULL;

-- Beklenen:
-- - version_kolonu_sayisi migration sonrasında 1
-- - is_nullable = NO
-- - column_default = 0
-- - null_version_satiri = 0
--
-- Rollback yalnız uygulama @Version kullanmayan eski sürüme geri alındıktan sonra
-- ve yeni sürümün yazdığı version değerlerine artık ihtiyaç olmadığı onaylandıktan
-- sonra manuel olarak uygulanmalıdır:
-- ALTER TABLE personel DROP COLUMN version;
