# Oda Rezervasyonu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bütün aktif rollerin çalışma takvimi ve kapasite kuralları içinde güvenli oda rezervasyonu oluşturabilmesini, kendi kayıtlarını iptal edip geçmişini görebilmesini; Admin'in şirket geneli geçmişi görüp başlamamış rezervasyonları iptal edebilmesini ve dashboard'un gerçek oda doluluğunu göstermesini sağlamak.

**Architecture:** `RezervasyonController` yalnız HTTP sözleşmesini taşır. `RezervasyonService` principal, ownership/Admin, zaman, kapasite, çakışma, transaction ve audit kararlarını yönetir; `RezervasyonZamanPolitikasi` enjekte edilen `Clock`, yapılandırma ve gerçek tatil kayıtlarıyla ortak saat uygunluğunu hesaplar. Repository sorguları aktif interval, owner kapsamı, sayfalama ve pessimistic lock ihtiyaçlarına özeldir. Personel ve Oda kilitleri aynı owner veya aynı oda yarışlarını seri hale getirir; response'lar entity değil güvenli DTO'dur.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Spring Security Method Security, Jakarta Validation, MySQL 8, Testcontainers, JUnit 5, Mockito, Maven, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-08-26-oda-rezervasyonu-design.md`

## Global Constraints

- Yalnız backend oda rezervasyonu, dashboard oda özeti, personel pasifleştirme entegrasyonu ve bunların belgeleri kapsam içindedir; frontend dosyalarına dokunma.
- Yeni dependency ekleme.
- Git komutu çalıştırma; commit adımları bilinçli olarak bu planda yoktur ve Git işlemlerini yalnız kullanıcı yapar.
- Rezervasyon authenticated principal adına oluşturulur; request'ten owner, rol, durum, audit, oluşturma/iptal zamanı veya hesaplanmış alan alınmaz.
- Entity'leri response olarak döndürme; kullanıcı adı, e-posta, telefon, personel UUID, token veya başka hesap alanı sızdırma.
- Başlık ve katılımcı sayısı yalnız owner veya DB rolü gerçekten `ADMIN` olan aktöre gönderilir.
- Bütün mutation kuralları service katmanında uygulanır; frontend görünürlüğü ownership/Admin kontrolünün yerine geçmez.
- Saat politikası enjekte edilmiş `Clock` ve `Europe/Istanbul` semantiğini kullanır.
- Create kilit sırası `Personel owner -> Oda`; iptal kilit sırası `owner -> Rezervasyon`; pasifleştirmede hedef Personel kilidi önce gelir.
- Rezervasyon ve audit aynı transaction'da tamamlanır veya birlikte rollback olur.
- İptal fiziksel silme değildir; kayıt `IPTAL_EDILDI` durumuyla geçmişte kalır.
- Hibernate `ddl-auto=update`, mevcut MySQL audit ENUM'unu veya legacy rezervasyon şemasını yükselten migration yerine kabul edilmez.
- Ürün belgeleri yalnız davranış ve testler doğrulandıktan sonra güncellenir.
- Docker/Testcontainers erişimi engellenirse ortam hatası davranış RED/GREEN kanıtı sayılmaz.

---

## File Map

**Create**

- `backend/db/manual-migrations/2026-08-26-add-room-reservations.sql` — legacy backfill, constraint/index, oda seed ve audit ENUM migration'ı.
- `backend/src/main/java/com/jforce/backend/model/enums/RezervasyonDurum.java` — `AKTIF`, `IPTAL_EDILDI`.
- `backend/src/main/java/com/jforce/backend/config/RezervasyonAyarlari.java` — doğrulanan çalışma saati/süre/ileri gün ayarları.
- `backend/src/main/java/com/jforce/backend/repository/OdaRepository.java` — oda lookup ve room lock.
- `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java` — takvim, geçmiş, overlap, iptal ve dashboard sorguları.
- `backend/src/main/java/com/jforce/backend/service/RezervasyonZamanPolitikasi.java` — bugün, hafta sonu, tam/yarım tatil ve açık saat hesabı.
- `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java` — read/create/history/cancel use-case'leri.
- `backend/src/main/java/com/jforce/backend/controller/RezervasyonController.java` — `/api/rooms` ve `/api/reservations` HTTP sınırı.
- `backend/src/main/java/com/jforce/backend/model/dto/request/RezervasyonIptalRequest.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonGecmisResponse.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonOlusturanResponse.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/OdaDoluAralikResponse.java`.
- `backend/src/main/java/com/jforce/backend/model/enums/OdaAnlikDurum.java`.
- `backend/src/test/java/com/jforce/backend/RezervasyonFoundationMigrationIntegrationTest.java`.
- `backend/src/test/java/com/jforce/backend/RezervasyonZamanPolitikasiTest.java`.
- `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`.
- `backend/src/test/java/com/jforce/backend/RezervasyonConcurrencyIntegrationTest.java`.
- `backend/src/test/java/com/jforce/backend/RezervasyonServiceTest.java`.

**Modify**

- `backend/src/main/java/com/jforce/backend/model/entity/Oda.java`.
- `backend/src/main/java/com/jforce/backend/model/entity/Rezervasyon.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/request/RezervasyonRequest.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonResponse.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/OdaResponse.java`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/OdaDolulukItem.java`.
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`.
- `backend/src/main/java/com/jforce/backend/service/PersonelService.java`.
- `backend/src/main/java/com/jforce/backend/service/DashboardService.java`.
- `backend/src/main/resources/application.properties`.
- `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`.
- `backend/src/test/java/com/jforce/backend/DashboardControllerIntegrationTest.java`.
- `backend/src/test/java/com/jforce/backend/GlobalExceptionHandlerTest.java`.
- `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md`, `README.md`.

---

### Task 1: Legacy MySQL şemasını ve domain temelini yükselt

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/RezervasyonFoundationMigrationIntegrationTest.java`
- Create: `backend/db/manual-migrations/2026-08-26-add-room-reservations.sql`
- Create: `backend/src/main/java/com/jforce/backend/model/enums/RezervasyonDurum.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/Oda.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/Rezervasyon.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Interfaces:**

```java
public enum RezervasyonDurum { AKTIF, IPTAL_EDILDI }
```

Yeni entity alanları: non-null `katilimciSayisi`, non-null string enum `durum`, non-null `olusturmaZamani`; nullable `iptalZamani`, `iptalEden`, `iptalNedeni(500)`.

- [ ] **Step 1: Eksik migration için gerçek MySQL RED testi yaz**

Test legacy `oda`, `rezervasyon`, `personel`, `audit_log` fixture'ı kurup `db/manual-migrations/2026-08-26-add-room-reservations.sql` dosyasını çalıştırmalıdır. Şunları doğrulamalıdır:

```text
legacy rezervasyon ID/başlık/tarih/saat/owner/oda korunur
durum=AKTIF, katilimci_sayisi=1, olusturma_zamani backfill edilir
yeni kolon type/nullability/length sözleşmesi doğrudur
eski (tarih, oda_id, baslangic_saat) unique constraint kaldırılır
durum constraint'i yalnız AKTIF/IPTAL_EDILDI kabul eder
oda/tarih/durum/saat, owner/tarih/durum ve Admin sıra indeksleri doğru sıradadır
oda_kapasite pozitif constraint'i vardır
Büyük Salon=12, Küçük Oda=6 tek satırdır; başka oda korunur
audit DB ENUM kümesi Java AuditEylem kümesiyle exact eşittir
yeni audit değerleriyle insert başarılıdır
ikinci çalışma veri/ID/satır sayısını değiştirmez
```

- [ ] **Step 2: RED'i doğrula**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd "-Dtest=RezervasyonFoundationMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `CannotReadScript`.

- [ ] **Step 3: İdempotent migration'ı yaz**

Kolonları nullable ekle, legacy satırları backfill et, sonra non-null yap. Eski unique constraint'i `information_schema` ile bulup kaldır. İndeksleri yalnız yoksa ekle. Seed'leri `oda_ad` unique anahtarıyla upsert et. Audit ENUM listesinde mevcut bütün `AuditEylem` değerlerini koruyup `REZERVASYON_OLUSTURULDU` ve `REZERVASYON_IPTAL_EDILDI` ekle.

- [ ] **Step 4: Entity ve enumları migration ile hizala**

Eski `@UniqueConstraint` kaldırılmalı; ilişkiler `LAZY` kalmalı. `durum` `EnumType.STRING`, length 30 olmalıdır. Nesne alanları `olusturan`, `oda`, `iptalEden` adlarını kullanmalıdır. Migration ve `Oda` mapping'i kapasitenin pozitif olmasını DB seviyesinde de korumalıdır.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonFoundationMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: migration iki kez çalışır, legacy veri ve exact audit ENUM korunur.

---

### Task 2: Deterministic rezervasyon zaman politikasını kur

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/config/RezervasyonAyarlari.java`
- Create: `backend/src/main/java/com/jforce/backend/service/RezervasyonZamanPolitikasi.java`
- Create: `backend/src/test/java/com/jforce/backend/RezervasyonZamanPolitikasiTest.java`
- Modify: `backend/src/main/resources/application.properties`

**Interfaces:**

```java
public void olusturmaZamaniniDogrula(LocalDate tarih, int baslangic, int bitis);
public AcikGunBilgisi acikGunBilgisi(LocalDate tarih);
public boolean baslamisMi(Rezervasyon rezervasyon);
public boolean gecmisMi(Rezervasyon rezervasyon);

public record AcikGunBilgisi(int baslangicSaat, int bitisSaat) {
    public boolean kapaliMi() { return baslangicSaat == bitisSaat; }
    public int acikSaatSayisi() { return bitisSaat - baslangicSaat; }
}
```

- [ ] **Step 1: Sabit Clock ile RED unit testlerini yaz**

`2026-09-10T10:20+03:00` için: bugün 10 reddedilir/11 kabul; 1 ve 4 saat kabul/0 ve 5 saat ret; 08-18 sınırı; bugün+90 kabul/+91 ret; hafta sonu ve tam tatil ret; yarım gün 08-13 kabul/13 sonrası ret; normal/yarım/kapalı açık saat 10/5/0.

- [ ] **Step 2: RED'i çalıştır**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonZamanPolitikasiTest" "-Djacoco.skip=true" test
```

Expected: eksik tip/metot compile RED'i.

- [ ] **Step 3: Ayar bean'ini ekle ve startup'ta doğrula**

```properties
rezervasyon.calisma-baslangic-saati=8
rezervasyon.calisma-bitis-saati=18
rezervasyon.yarim-gun-bitis-saati=13
rezervasyon.maksimum-sure-saat=4
rezervasyon.maksimum-ileri-gun=90
```

Başlangıç>=bitiş, yarım gün sınırı dışarıda, süre<=0/günlük aralıktan büyük veya ileri gün<=0 ise startup fail etmelidir.

- [ ] **Step 4: Policy'yi enjekte edilmiş Clock ve gerçek TatilRepository ile uygula**

`LocalDate.now(clock)`/`LocalTime.now(clock)` kullan. Kontrollü 400 mesajları tasarım belgesiyle exact olsun; frontend'den iş günü/tatil bilgisi alma.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonZamanPolitikasiTest" "-Djacoco.skip=true" test
```

---

### Task 3: Oda lookup ve güvenli genel takvim okumasını ekle

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/repository/OdaRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/RezervasyonController.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonOlusturanResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/OdaResponse.java`
- Create: `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`

**Interfaces:**

```java
List<Oda> findAllByOrderByOdaAdAscOdaIdAsc();
List<Rezervasyon> findAktifTakvim(LocalDate baslangic, LocalDate bitis, Integer odaId);
List<OdaResponse> odalariGetir(String aktorId);
List<RezervasyonResponse> takvimiGetir(String aktorId, LocalDate baslangic, LocalDate bitis, Integer odaId);

public record RezervasyonResponse(
        String id,
        OdaResponse oda,
        LocalDate tarih,
        Integer baslangicSaat,
        Integer bitisSaat,
        RezervasyonOlusturanResponse olusturan,
        boolean benMiOlusturdum,
        String baslik,
        Integer katilimciSayisi
) {}
```

- [ ] **Step 1: Endpoint yokken RED integration testleri yaz**

`GET /api/rooms` için seed/sıra/güvenli DTO; `GET /api/reservations` için yalnız AKTIF, dahil tarih aralığı, opsiyonel oda AND filtresi ve kararlı sıra. Pozitif bulunmayan oda `200 []`; 0/negatif ID, eksik/malformed/ters/367 günlük tarih `400`.

- [ ] **Step 2: Role ve privacy RED testlerini yaz**

Dört aktif rol `200`; pasif `403`, missing principal `404`, tokensız `401`. Owner ve gerçek DB Admin başlık/katılımcıyı görür. Başka rol ve JWT Admin/DB non-Admin `null` görür. Oluşturan yalnız ad/soyad; UUID/iletişim/account alanları yok. Okumalar rezervasyon/audit snapshot'ını değiştirmez.

- [ ] **Step 3: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest#aktifRollerOdalariGuvenliGorur+aktifPersonelRezervasyonTakviminiGorur+takvimBaslikGizliligiDbRoluyleUygulanir" "-Djacoco.skip=true" test
```

Expected: `404`.

- [ ] **Step 4: Repository/service/controller'ı minimal uygula**

Takvim sorgusu `durum=AKTIF`, dahil tarih, nullable oda filtresi ve `tarih, baslangicSaat, odaId, rezervasyonId` sırası taşımalıdır. Service aktörü DB'den bulup aktifliği doğrulamalı; görünürlüğü `owner || dbRol==ADMIN` ile hesaplamalıdır. Controller `@Validated`, query/path/body ve `Authentication#getName` ile sınırlı kalmalıdır.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 4: Create, kapasite ve interval çakışmalarını uygula

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/model/dto/request/RezervasyonRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/OdaRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/RezervasyonController.java`
- Modify: `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`

**Interfaces:**

```java
public record RezervasyonRequest(
        @NotNull @Positive Integer odaId,
        @NotNull LocalDate tarih,
        @NotNull @Min(0) @Max(23) Integer baslangicSaat,
        @NotNull @Min(1) @Max(24) Integer bitisSaat,
        @NotBlank @Size(max = 50) String baslik,
        @NotNull @Positive Integer katilimciSayisi
) {}

@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Oda> findByIdForUpdate(Integer odaId);
```

- [ ] **Step 1: Happy path ve dört rol için RED testleri yaz**

Dört aktif rol yalnız principal adına `201`; pasif `403`, missing `404`, tokensız `401`. Request owner/rol/durum alanı içermez. Response owner'a tam bilgi ve `benMiOlusturdum=true` verir.

- [ ] **Step 2: Validation RED testlerini yaz**

Başlık trim/boş/51; katılımcı min1 ve Büyük Salon12/Küçük Oda6 kapasitesi; unknown oda 404; geçmiş, başlamış saat, +91 gün, hafta sonu, tam/yarım tatil; 1-4 saat ve 08-18 sınırları. Başarısızlık rezervasyon/audit bırakmaz.

- [ ] **Step 3: Interval RED testlerini yaz**

```text
aynı oda 10-12 / 11-13 -> 409 Seçilen oda bu saat aralığında dolu.
aynı owner farklı oda -> 409 Bu saat aralığında başka bir rezervasyonunuz bulunuyor.
10-11 / 11-12 -> ikisi başarılı
IPTAL_EDILDI kayıt engellemez
```

- [ ] **Step 4: RED'i çalıştır**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest#aktifRollerKendiRezervasyonunuOlusturur+kapasiteVeZamanKurallariAtomikUygulanir+aktifOdaVeOwnerIntervalCakismalari409Doner+sinirSaatlerCakismaz" "-Djacoco.skip=true" test
```

- [ ] **Step 5: Create transaction'ını exact sırada uygula**

```text
Personel.findByIdForUpdate -> aktiflik -> başlık/zaman -> Oda.findByIdForUpdate
-> kapasite -> oda overlap -> owner overlap -> AKTIF entity + Clock zamanı
-> saveAndFlush -> sonraki taskta audit
```

Overlap: `yeniBaslangic < mevcutBitis AND yeniBitis > mevcutBaslangic`; yalnız `AKTIF`.

- [ ] **Step 6: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest,RezervasyonZamanPolitikasiTest" "-Djacoco.skip=true" test
```

---

### Task 5: Create audit, rollback ve persistence fallback'ini tamamla

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`
- Create: `backend/src/test/java/com/jforce/backend/RezervasyonServiceTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/GlobalExceptionHandlerTest.java`

**Interfaces:**

- Produces `AuditEylem.REZERVASYON_OLUSTURULDU`.
- Uses `AuditService.logKaydetOrtakTransaction(...)` after `saveAndFlush`.

- [ ] **Step 1: Exact audit ve dış transaction rollback RED testlerini yaz**

Başarıda tek audit:

```java
assertEquals(AuditEylem.REZERVASYON_OLUSTURULDU, audit.getEylemTipi());
assertEquals(ownerId, audit.getPersonel().getPersonelId());
assertEquals(ownerId, audit.getHedefPersonel().getPersonelId());
assertEquals("Rezervasyon oluşturuldu.", audit.getDetay());
```

Detay UUID/oda/tarih/saat/başlık/katılımcı/token içermemelidir. Dış transactional wrapper gerçek create'ten sonra `IllegalStateException` attığında rezervasyon ve audit birlikte rollback olmalıdır.

- [ ] **Step 2: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest#basariliCreateTekGuvenliAuditOlusturur+disTransactionHatasindaCreateVeAuditRollbackOlur" "-Djacoco.skip=true" test
```

Expected: audit eksikliği nedeniyle failure.

- [ ] **Step 3: Same-transaction auditi ekle**

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.REZERVASYON_OLUSTURULDU,
        aktor,
        aktor.getKullaniciAdi(),
        "Rezervasyon oluşturuldu.",
        aktor
);
```

Validation/403/404/409/read-only akışlarında çağrılmamalıdır.

- [ ] **Step 4: Beklenmeyen persistence hatası unit ve HTTP testlerini ekle**

`saveAndFlush` bir `DataIntegrityViolationException` attığında service aynı exception'ı taşımalı ve audit çağırmamalıdır. HTTP response yalnız generic `500 Beklenmeyen bir hata oluştu.` olmalı; SQL/constraint mesajı sızmamalıdır. Ürün sözleşmesinde olmayan tahmini `409` icat etme.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest,RezervasyonServiceTest,GlobalExceptionHandlerTest" "-Djacoco.skip=true" test
```

---

### Task 6: Paralel create yarışlarını gerçek MySQL kilitleriyle doğrula

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/RezervasyonConcurrencyIntegrationTest.java`
- Modify only if a defect is exposed: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify only if a defect is exposed: `backend/src/main/java/com/jforce/backend/repository/OdaRepository.java`
- Modify only if a defect is exposed: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`

**Interfaces:**

- Same owner/different rooms serialize on the `personel` primary record.
- Different owners/same room serialize on the `oda` primary record.
- Different owners/different rooms are not globally serialized.

- [ ] **Step 1: Aynı oda/farklı owner concurrency testi yaz**

İki gerçek transaction aynı oda/tarih ve çakışan saat için birlikte create etmelidir. Exact oracle:

```text
1 success
1 controlled 409 oda dolu
1 rezervasyon
1 create audit
```

Mümkünse `performance_schema.data_lock_waits` ile aynı `oda` PRIMARY record wait'i doğrulanmalı; yalnız timing threshold kullanılmamalıdır.

- [ ] **Step 2: Aynı owner/farklı oda concurrency testi yaz**

Exact oracle: 1 success, 1 controlled owner-overlap 409, 1 rezervasyon, 1 audit. Lock gözlemi `personel` PRIMARY record'una bağlanmalıdır.

- [ ] **Step 3: Farklı owner/farklı oda bağımsızlığını doğrula**

İki ayrı Personel/Oda çifti iki geçerli rezervasyon ve iki audit üretmelidir. Milisaniye performans assertion'ı yerine iki ayrı lock'un aynı anda tutulabildiğini latch/DB lock gözlemiyle kanıtla.

- [ ] **Step 4: Concurrency sınıfını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

Expected: üç senaryo gerçek MySQL 8 üzerinde başarılı. İki success görülürse testi gevşetme; kilit/transaction sırasını düzelt.

- [ ] **Step 5: Create regresyonuyla birlikte doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonConcurrencyIntegrationTest,RezervasyonControllerIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 7: Kişisel ve Admin rezervasyon geçmişini ekle

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/RezervasyonGecmisResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/RezervasyonController.java`
- Modify: `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`

**Interfaces:**

```java
public record RezervasyonGecmisResponse(
        String id,
        OdaResponse oda,
        LocalDate tarih,
        Integer baslangicSaat,
        Integer bitisSaat,
        RezervasyonOlusturanResponse olusturan,
        String baslik,
        Integer katilimciSayisi,
        RezervasyonDurum durum,
        boolean gecmisMi,
        LocalDateTime olusturmaZamani,
        LocalDateTime iptalZamani,
        RezervasyonOlusturanResponse iptalEden,
        String iptalNedeni
) {}

SayfaliResponse<RezervasyonGecmisResponse> kendiGecmisiniGetir(
        String aktorId, int page, int size, RezervasyonDurum durum,
        Integer odaId, LocalDate baslangic, LocalDate bitis);

SayfaliResponse<RezervasyonGecmisResponse> adminGecmisiniGetir(
        String aktorId, int page, int size, RezervasyonDurum durum,
        Integer odaId, String olusturanId, LocalDate baslangic, LocalDate bitis);
```

- [ ] **Step 1: `/my` owner predicate ve pagination RED testlerini yaz**

```text
GET /api/reservations/my default page=0,size=20
yalnız principal kayıtları; yabancı aynı filtreli kayıt content/count'a girmez
AKTIF geçmiş ve IPTAL_EDILDI döner
durum/oda/tarih AND; iki tarih birlikte zorunlu
sort tarih DESC, baslangicSaat DESC, rezervasyonId DESC
page>=0, size 1..100; bozuk enum/ID/query 400
```

Response başlık, katılımcı, `gecmisMi`, durum ve nullable iptal zamanı/eden güvenli ad-soyad/neden alanlarını taşır.

- [ ] **Step 2: Admin geçmişi ve DB rolü RED testlerini yaz**

`GET /api/admin/reservations`: yalnız aktif gerçek DB Admin `200`; diğer roller ve JWT Admin/DB non-Admin `403`. Aktif/pasif owner'ların bütün geçmişi; canonical `olusturanId` filtresi. Bulunmayan canonical UUID empty page, malformed UUID `400`. Admin tam başlık/katılımcı/iptal ayrıntısını görür.

Her iki okuma non-empty audit ve rezervasyon snapshot'ını değiştirmemelidir.

- [ ] **Step 3: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest#personelYalnizKendiRezervasyonGecmisiniGorur+adminSirketGeneliRezervasyonGecmisiniGorur+rezervasyonGecmisiFiltreVeSayfalamaSozlesmesi" "-Djacoco.skip=true" test
```

Expected: history mapping'leri olmadığı için `404`.

- [ ] **Step 4: Repository/service/controller akışını uygula**

Owner query'sinde `olusturan.personelId=:aktorId` zorunlu predicate ve eş count query olmalıdır. Admin query owner filtresini nullable alır. Service `/my` için dört aktif rolü; Admin için method security + aktif gerçek DB Admin'i doğrular. `gecmisMi` bitiş anı ve injected Clock ile hesaplanır; scheduled durum mutation'ı eklenmez.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 8: Owner/Admin iptalini durum, audit ve yarış güvenliğiyle ekle

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/RezervasyonIptalRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/RezervasyonController.java`
- Modify: `backend/src/test/java/com/jforce/backend/RezervasyonControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/RezervasyonConcurrencyIntegrationTest.java`

**Interfaces:**

```java
public record RezervasyonIptalRequest(@Size(max = 500) String iptalNedeni) {}
Optional<String> findOwnerIdByRezervasyonId(String rezervasyonId);
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Rezervasyon> findByIdForUpdate(String rezervasyonId);
void rezervasyonIptalEt(String aktorId, String rezervasyonId, RezervasyonIptalRequest request);
```

- [ ] **Step 1: Owner/Admin happy-path RED testlerini yaz**

Owner başlamamış kaydı body olmadan; Admin kendi kaydını owner kuralıyla; Admin başkasını trim sonrası 1..500 zorunlu nedenle iptal eder. Başarı `204`; kayıt silinmez, `IPTAL_EDILDI`, iptalEden/zaman/neden doğru; genel takvim/dashboard'dan çıkar, history'de kalır.

Controller iptal body'sini `@RequestBody(required = false)` kabul etmeli; body yokluğunu service'e nullable request/neden olarak aktarmalıdır. Owner için body zorunlu hale getirilmemelidir.

- [ ] **Step 2: Policy-before-status ve error RED testlerini yaz**

Non-owner PERSONEL/YONETICI/HR, hedef geçmiş/iptal olsa bile durum kontrolünden önce `403`. JWT Admin/DB non-Admin başkasında `403`. Missing/malformed ID `404/400`; pasif/missing principal `403/404`; token yok `401`. Başlamış, geçmiş, zaten iptal owner/Admin kaydı exact `409 Rezervasyon artık iptal edilemez.`. Başarısızlık yan etkisiz.

- [ ] **Step 3: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest#ownerKendiRezervasyonunuIptalEder+adminBaskasininRezervasyonunuNedenleIptalEder+nonOwnerDurumdanOnce403Alir+baslamisVeyaIptalRezervasyon409Doner" "-Djacoco.skip=true" test
```

- [ ] **Step 4: İptali exact lock sırasıyla uygula**

```text
aktör aktif DB snapshot -> owner ID projection -> owner Personel lock
-> reservation lock -> ownership/gerçek DB Admin -> status/time
-> Admin başka owner neden validation -> durum/metadata -> saveAndFlush
-> same-tx REZERVASYON_IPTAL_EDILDI audit
```

Audit actor işlemi yapan, target owner, detay yalnız `Rezervasyon iptal edildi.`.

- [ ] **Step 5: Rollback ve paralel iki iptal testini ekle**

Dış hata durum/metadata/auditi geri alır. Paralel iki iptalde yalnız biri `204`, diğeri `409`; tek iptal auditi bulunur.

- [ ] **Step 6: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonControllerIntegrationTest,RezervasyonConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 9: Personel pasifleştirmeyi gelecek rezervasyonlarla atomik bağla

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`

**Interfaces:**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
List<Rezervasyon> findBaslamamisAktifOwnerRezervasyonlariForUpdate(
        String ownerId, LocalDate bugun, int mevcutSaat);

int personelPasiflestirmeRezervasyonlariniIptalEt(
        Personel aktor, Personel lockedHedef);
```

- [ ] **Step 1: Pasifleştirme entegrasyonu RED testlerini yaz**

Fixture: bugün başlamamış AKTIF, gelecek AKTIF, bugün başlamış AKTIF, geçmiş AKTIF, gelecek IPTAL_EDILDI ve başka owner geleceği. Yalnız ilk ikisi yeni `IPTAL_EDILDI` olmalıdır. `iptalEden` HR/Admin, neden `Personel pasife alındı.`, zaman fixed Clock. Her değişen kayıt için tek güvenli iptal auditi; personel auditinde iptal sayısı.

- [ ] **Step 2: Dış rollback ve create yarışı testlerini yaz**

Dış transaction hatasında Personel aktifliği, rezervasyonlar ve auditler eski kalmalıdır. Aynı hedef Personel lock'u nedeniyle create önce commit ederse pasifleştirme onu iptal etmeli; pasifleştirme önce commit ederse create aktiflikte `403` almalıdır. Aktif orphan rezervasyon kalmamalıdır.

- [ ] **Step 3: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest#personelPasiflestirmeBaslamamisRezervasyonlariAtomikIptalEder+pasiflestirmeHatasindaPersonelRezervasyonVeAuditRollbackOlur" "-Djacoco.skip=true" test
```

Expected: mevcut pasifleştirme rezervasyonlara dokunmadığı için failure.

- [ ] **Step 4: PersonelService lock ve iptal entegrasyonunu uygula**

Hedef `findByIdForUpdate` ile yüklenmeli. Zaten pasif no-op korunmalı. Aktif hedef için rezervasyon iptalleri, refresh token revoke, yönetici bağlantıları ve personel audit'i aynı outer transaction'da olmalıdır.

Personel audit detayı:

```text
Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: X. İptal edilen rezervasyon sayısı: Y
```

Başlık, oda, tarih, UUID veya neden listesi yazılmamalıdır.

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest,RezervasyonControllerIntegrationTest,RezervasyonConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 10: Dashboard oda doluluk placeholder'ını gerçek özetle doldur

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/OdaDoluAralikResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/model/enums/OdaAnlikDurum.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/OdaDolulukItem.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/RezervasyonRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/RezervasyonService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/DashboardService.java`
- Modify: `backend/src/test/java/com/jforce/backend/DashboardControllerIntegrationTest.java`

**Interfaces:**

```java
public record OdaDolulukItem(
        Integer odaId,
        String odaAd,
        Integer kapasite,
        OdaAnlikDurum anlikDurum,
        Integer dolulukYuzdesi,
        List<OdaDoluAralikResponse> doluAraliklar
) {}

public record OdaDoluAralikResponse(Integer baslangicSaat, Integer bitisSaat) {}
public enum OdaAnlikDurum { MUSAIT, DOLU, KAPALI }
List<OdaDolulukItem> bugunkuOdaDoluluklariniGetir();
```

- [ ] **Step 1: Normal gün dashboard RED testlerini yaz**

Fixed Clock açık saatte: iki seed oda ad/ID sırasında; kapasite; anlık interval varsa DOLU yoksa MUSAIT; aralıklar saat sırasında; 3/10 saat `%30`; iptal kayıt sayılmaz; başlık/owner/katılımcı response'ta yok.

- [ ] **Step 2: Kapalı ve yarım gün RED testlerini yaz**

Çalışma saati dışında, hafta sonu ve tam tatilde KAPALI/%0. Yarım gün 12:00 açık, 13:00 sonrası KAPALI; 2/5 saat `%40`. Kesir `HALF_UP`. Okuma audit snapshot'ını değiştirmez.

- [ ] **Step 3: RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=DashboardControllerIntegrationTest#normalGundeOdaDoluluklariniGuvenliHesaplar+yarimGunVeKapaliGundeOdaDurumunuDogruHesaplar" "-Djacoco.skip=true" test
```

Expected: mevcut `List.of()` placeholder nedeniyle failure.

- [ ] **Step 4: Bugünkü sorgu ve hesaplamayı ekle**

Repository yalnız bugünkü `AKTIF` kayıtları oda/saat sırasıyla döndürür. Service bütün odaları lookup sırasında alır, `RezervasyonZamanPolitikasi#acikGunBilgisi` ile payda ve açık/kapalı kararını üretir.

```java
BigDecimal.valueOf(doluSaat)
        .multiply(BigDecimal.valueOf(100))
        .divide(BigDecimal.valueOf(acikSaat), 0, RoundingMode.HALF_UP)
        .intValueExact();
```

`acikSaat==0` ise 0. Dolu aralıklar sort edilmeden response'a konmaz.

- [ ] **Step 5: DashboardService placeholder'ını değiştir**

Constructor'a `RezervasyonService` ekle; `odaDoluluk` alanına `bugunkuOdaDoluluklariniGetir()` ver. Controller'a repository/hesap ekleme.

- [ ] **Step 6: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=DashboardControllerIntegrationTest,RezervasyonZamanPolitikasiTest,RezervasyonControllerIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 11: Belgeleri senkronize et ve tam modülü doğrula

**Files:**

- Modify after behavior is GREEN: `API_SPEC.md`
- Modify after behavior is GREEN: `SECURITY.md`
- Modify after behavior is GREEN: `ROADMAP.md`
- Modify after behavior is GREEN: `PROGRESS.md`
- Modify stale module status only: `README.md`

- [ ] **Step 1: Odaklı backend paketini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=RezervasyonFoundationMigrationIntegrationTest,RezervasyonZamanPolitikasiTest,RezervasyonControllerIntegrationTest,RezervasyonConcurrencyIntegrationTest,RezervasyonServiceTest,HrPersonelControllerIntegrationTest,DashboardControllerIntegrationTest,SecurityHttpIntegrationTest,TatilTakvimiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `0 failure, 0 error`, `BUILD SUCCESS`.

- [ ] **Step 2: `API_SPEC.md` exact sözleşmesini yaz**

Request/response, defaults, filters, sort, role matrix ve 400/401/403/404/409 koşul/mesajlarıyla:

```text
GET /api/rooms
GET /api/reservations
POST /api/reservations
GET /api/reservations/my
GET /api/admin/reservations
PATCH /api/reservations/{rezervasyonId}/cancel
GET /api/dashboard/ozet içindeki odaDoluluk
```

Privacy exact yazılmalı: ad/soyad genel; başlık/katılımcı owner ve DB Admin dışına `null`.

- [ ] **Step 3: `SECURITY.md` ve deployment sözleşmesini güncelle**

Principal-only create; aktif DB Personel/gerçek DB Admin; ownership-before-status; Personel->Oda ve owner->reservation lock sırası; pasifleştirme atomikliği; same-tx audit; başlık gizliliği; manual migration-before-deploy; `ddl-auto=update` sınırı.

- [ ] **Step 4: `ROADMAP.md`, `PROGRESS.md` ve README durumunu güncelle**

Yalnız uygulanan MVP tamamlandı yazılmalı. Şunlar geleceğe açık kalmalı:

```text
rezervasyon düzenleme
tekrarlayan rezervasyon grupları
davetliler ve kabul/red
bildirimler
oda bakım/aktiflik ve yönetimi
oda ekipmanları/filtreleri
Admin ad/kullanıcı adı history araması
analitik/raporlar
30 dakikalık slot
```

- [ ] **Step 5: `CONTRIBUTING.md` backend modül checklist'ini uygula**

Controller repository kullanmıyor; request owner/rol/durum almıyor; entity response değil; ownership/DB Admin backend'de; transaction/audit atomik; MySQL constraint/index gerçek container'da; negative tests yan etkisiz; reads auditsiz; ham exception/hassas alan yok.

- [ ] **Step 6: Tam backend suite ve JaCoCo çalıştır**

```powershell
.\mvnw.cmd test
```

Expected: `Failures: 0`, `Errors: 0`, `BUILD SUCCESS`, `All coverage checks have been met.`

- [ ] **Step 7: UTF-8/BOM ve stale sözleşme taraması yap**

Değişen Java/SQL/Markdown strict UTF-8, BOM=false. `rg` ile eski dashboard `List.of`, eski reservation unique constraint, eksik audit enum ve planlanan gibi kalan uygulanmış endpoint ifadelerini tara.

- [ ] **Step 8: Frontend agent aktarım raporu hazırla**

Endpoint/rol matrisi, JSON örnekleri, privacy, saat/tatil/kapasite, filtreler, owner/Admin iptal UI, dashboard alanları, hata mesajları, migration, test sonuçları ve gelecekteki özellikleri exact aktar.

---

## Execution Notes

- Her task RED -> minimal GREEN -> ilgili regresyon sırasıyla tamamlanmalıdır.
- Test compile hatası yalnız beklenen eksik tip/metottan geliyorsa hazırlık RED'idir; HTTP/business RED'i yerine sunulmaz.
- Docker/Testcontainers sandbox hatası production kusuru değildir; aynı komut yükseltilmiş veya kullanıcı ortamında çalıştırılır.
- Aynı service/repository/integration test dosyasında eşzamanlı agent değişikliği yaptırılmaz.
- Concurrency testleri yalnız süre eşiğine güvenmez; mümkün olduğunda row-lock wait + exact DB/audit sonucu kullanır.
- Migration audit assertion'ı subset değil exact Java enum kümesi eşitliğidir; legacy fixture yeni rezervasyon olaylarını migration öncesinde içermez.
- User-visible 400/403/404/409 mesajları design ile exact kalır; beklenmeyen persistence hatasına tahmini business mesajı verilmez.
- Kullanıcı backend kodunun agent tarafından yazılmasına uygulama aşaması için ayrıca açık izin vermelidir; tasarım ve plan onayı production yazma izni değildir.
- Git işlemlerini yalnız kullanıcı yapar.
