# Tatil Grupları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mevcut tek günlük tatil akışını bozmadan başlangıç-bitiş aralığı taşıyan, çakışmayan, atomik ve auditli tatil grupları eklemek.

**Architecture:** Her tatil grubu mevcut `tatil` tablosunda tek satır olarak tutulur; `tarih` başlangıç, yeni `bitis_tarihi` dahil bitiştir. Aralık mutation'ları şirket genelindeki tek teknik kilit satırıyla seri hale getirilir; listeleme ve iş günü hesabı kesişen grupları tek sorguyla alır.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Spring Security Method Security, MySQL 8, Testcontainers, JUnit 5, Mockito, Jakarta Validation.

**Spec:** `docs/superpowers/specs/2026-08-23-tatil-gruplari-design.md`

## Global Constraints

- Frontend dosyalarına dokunma.
- Yeni dependency ekleme.
- Entity'yi API response'u yapma.
- Mevcut endpoint yollarını ve tek günlük JSON request uyumluluğunu koru.
- `tarih` başlangıç, `bitisTarihi` dahil bitiştir; bitiş yoksa başlangıca eşitlenir.
- Grup dahil en fazla 366 gündür.
- Mutation yalnız aktif HR/ADMIN; listeleme bütün aktif authenticated rollere açıktır.
- Geçmiş, yetki ve hedef kontrolleri no-op kontrolünden önce çalışır.
- Mutation ve audit aynı transaction'da tamamlanır.
- Git komutu çalıştırma; bu plandaki görevlerde commit adımı bilinçli olarak yoktur.
- Ürün belgelerini yalnız çalışan davranış doğrulandıktan sonra güncelle.

---

## File Map

**Create**

- `backend/db/manual-migrations/2026-08-23-add-tatil-group-range.sql` — eski tek-gün şemasını aralık modeline yükseltir ve teknik kilit satırını seed eder.
- `backend/src/main/java/com/jforce/backend/model/entity/TatilTakvimiKilit.java` — şirket takviminin tek mutation kilit satırını map eder.
- `backend/src/main/java/com/jforce/backend/repository/TatilTakvimiKilitRepository.java` — kilit satırını `PESSIMISTIC_WRITE` ile getirir.
- `backend/src/test/java/com/jforce/backend/TatilGrupMigrationIntegrationTest.java` — gerçek MySQL 8 üzerinde eski şemadan migration'ı doğrular.
- `backend/src/test/java/com/jforce/backend/TatilTakvimiConcurrencyIntegrationTest.java` — iki paralel çakışan mutation'ın birlikte başarılı olamadığını doğrular.

**Modify**

- `backend/src/main/java/com/jforce/backend/model/entity/Tatil.java` — `bitisTarihi` alanını ekler.
- `backend/src/main/java/com/jforce/backend/model/dto/request/TatilKaydetRequest.java` — opsiyonel `bitisTarihi` alır ve eski Java constructor kullanımını korur.
- `backend/src/main/java/com/jforce/backend/model/dto/response/TatilResponse.java` — zorunlu `bitisTarihi` döner.
- `backend/src/main/java/com/jforce/backend/repository/TatilRepository.java` — kesişim, self-excluding overlap ve kararlı liste sorgularını sağlar.
- `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java` — normalize, validation, lock, overlap, CRUD ve audit akışını yürütür.
- `backend/src/main/java/com/jforce/backend/service/IsGunuHesaplamaService.java` — çok günlük grupların günlük etkisini hesaplar.
- `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java` — HTTP, rol, geçmiş, audit, overlap ve geriye uyumluluk testlerini genişletir.
- `backend/src/test/java/com/jforce/backend/TatilTakvimiServiceTest.java` — lock dependency ve persistence yarış fallback testlerini günceller.
- `backend/src/test/java/com/jforce/backend/IsGunuHesaplamaServiceTest.java` — çok günlük tam/yarım grup testlerini ekler.
- `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java` — gerçek çok günlük gruplarla preview sonucunu doğrular.
- `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md`, `ROADMAP.md` — yalnız implementation ve testler tamamlandıktan sonra gerçek sözleşmeyi kaydeder.

---

### Task 1: Eski tatil şemasını güvenli aralık şemasına yükselt

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/TatilGrupMigrationIntegrationTest.java`
- Create: `backend/db/manual-migrations/2026-08-23-add-tatil-group-range.sql`

**Interfaces:**

- Consumes: Eski `tatil(tatil_id, ad, tarih, kategori, gun_suresi, version)` tablosu.
- Produces: `tatil.bitis_tarihi DATE NOT NULL`, `chk_tatil_tarih_araligi`, `idx_tatil_tarih_araligi`, `tatil_takvimi_kilit(kilit_id=1)`.

- [ ] **Step 1: Migration dosyası henüz yokken gerçek RED testi yaz**

Test kendi MySQL 8 container'ında eski tabloyu oluşturmalı, iki mevcut satır eklemeli ve henüz bulunmayan migration'ı çalıştırmayı denemelidir:

```java
@Test
void eskiTekGunKayitlariAralikSemasinaVeriKaybetmedenTasinir()
        throws Exception {
    eskiTatiliOlusturVeVeriEkle(connection);

    Path migration = Path.of(
            System.getProperty("user.dir"),
            "db", "manual-migrations",
            "2026-08-23-add-tatil-group-range.sql"
    );

    migrationCalistir(connection, migration);

    assertEquals(0, nullBitisSayisi(connection));
    assertEquals(2, baslangiclaAyniBitisSayisi(connection));
    assertEquals(7L, version(connection, 1));
    assertEquals(1, kilitSatiriSayisi(connection));
}
```

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilGrupMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `CannotReadScript`; migration dosyası bulunamadığı için test error verir.

- [ ] **Step 3: Tekrar çalıştırılabilir migration'ı yaz**

Migration şu somut işlemleri yapmalıdır:

```sql
-- Kolon yoksa nullable ekle.
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

UPDATE tatil
SET bitis_tarihi = tarih
WHERE bitis_tarihi IS NULL;

ALTER TABLE tatil
    MODIFY COLUMN bitis_tarihi DATE NOT NULL;

CREATE TABLE IF NOT EXISTS tatil_takvimi_kilit (
    kilit_id TINYINT NOT NULL,
    PRIMARY KEY (kilit_id)
);

INSERT IGNORE INTO tatil_takvimi_kilit (kilit_id) VALUES (1);
```

Constraint ve index, `information_schema.table_constraints` ile `information_schema.statistics` kontrol edilerek yoksa şu dynamic SQL ile eklenmelidir:

```sql
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
```

Migration başında bakım modu/yedek uyarısı, sonunda kolon/constraint/index/kilit doğrulama sorguları bulunmalıdır. Otomatik destructive rollback eklenmemelidir.

- [ ] **Step 4: Migration testini ikinci çalışma ve constraint kontrolleriyle tamamla**

İlk çalışmadan sonra migration tekrar çalıştırılmalı; satır sayısı, ID, tarih, bitiş ve version değerleri değişmemelidir. Ayrıca şu insert `CHECK` nedeniyle reddedilmelidir:

```sql
INSERT INTO tatil(ad, tarih, bitis_tarihi, kategori, gun_suresi, version)
VALUES ('Ters', '2026-08-15', '2026-08-11', 'SIRKET', 'TAM_GUN', 0);
```

- [ ] **Step 5: Migration testini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilGrupMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: migration iki kez çalışır; testler `0 failure, 0 error` ve build success verir.

---

### Task 2: Entity ve DTO sözleşmesini geriye uyumlu genişlet

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/entity/TatilTakvimiKilit.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/TatilTakvimiKilitRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/Tatil.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/request/TatilKaydetRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/TatilResponse.java`
- Modify: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`

**Interfaces:**

- Produces: `Tatil#getBitisTarihi()`, `Tatil#setBitisTarihi(LocalDate)`, `TatilKaydetRequest#bitisTarihi()`, `TatilResponse#bitisTarihi()`.
- Produces: `Optional<TatilTakvimiKilit> TatilTakvimiKilitRepository.takvimiKilitle()`.

- [ ] **Step 1: Eski JSON request için failing HTTP testi ekle**

Test mevcut dört alanlı body'yi aynen göndermeli ve response'ta aynı başlangıç/bitişi beklemelidir:

```java
mockMvc.perform(post("/api/holidays")
        .contentType(MediaType.APPLICATION_JSON)
        .content("""
            {
              "ad": "Tek Gün Tatili",
              "tarih": "2026-10-29",
              "kategori": "RESMI_SABIT",
              "gunSuresi": "TAM_GUN"
            }
            """)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(hr, RolAdi.HR)))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.tarih").value("2026-10-29"))
    .andExpect(jsonPath("$.bitisTarihi").value("2026-10-29"));
```

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#eskiTekGunRequestiAyniBaslangicVeBitisleCalisir" "-Djacoco.skip=true" test
```

Expected: `$.bitisTarihi` bulunmadığı için assertion fail.

- [ ] **Step 3: Entity ve DTO alanlarını ekle**

`Tatil`:

```java
@Column(name = "bitis_tarihi", nullable = false)
private LocalDate bitisTarihi;

public LocalDate getBitisTarihi() {
    return bitisTarihi;
}

public void setBitisTarihi(LocalDate bitisTarihi) {
    this.bitisTarihi = bitisTarihi;
}
```

`TatilKaydetRequest` canonical sırası:

```java
public record TatilKaydetRequest(
        @NotBlank @Size(max = 100) String ad,
        @NotNull LocalDate tarih,
        LocalDate bitisTarihi,
        @NotNull TatilKategori kategori,
        @NotNull TatilGunSuresi gunSuresi
) {
    public TatilKaydetRequest(
            String ad,
            LocalDate tarih,
            TatilKategori kategori,
            TatilGunSuresi gunSuresi
    ) {
        this(ad, tarih, null, kategori, gunSuresi);
    }
}
```

`TatilResponse` yeni alanı `tarih` sonrasında taşımalıdır:

```java
public record TatilResponse(
        Integer tatilId,
        String ad,
        LocalDate tarih,
        LocalDate bitisTarihi,
        TatilKategori kategori,
        TatilGunSuresi gunSuresi
) {}
```

- [ ] **Step 4: Teknik kilit entity/repository'sini ekle**

```java
@Entity
@Table(name = "tatil_takvimi_kilit")
public class TatilTakvimiKilit {
    @Id
    @Column(name = "kilit_id")
    private Integer kilitId;

    protected TatilTakvimiKilit() {}

    public TatilTakvimiKilit(Integer kilitId) {
        this.kilitId = kilitId;
    }

    public Integer getKilitId() {
        return kilitId;
    }
}
```

```java
public interface TatilTakvimiKilitRepository
        extends JpaRepository<TatilTakvimiKilit, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from TatilTakvimiKilit k where k.kilitId = 1")
    Optional<TatilTakvimiKilit> takvimiKilitle();
}
```

Integration test setup'ı Hibernate'in temiz oluşturduğu tabloda kilit satırını açıkça hazırlamalıdır:

```java
if (!tatilTakvimiKilitRepository.existsById(1)) {
    tatilTakvimiKilitRepository.saveAndFlush(new TatilTakvimiKilit(1));
}
```

Bu fixture migration doğrulamasının yerine geçmez; yalnız Hibernate clean-schema test ortamının seed ihtiyacını karşılar.

- [ ] **Step 5: Service response mapping'inde tek günü normalize et**

Minimal geçişte create sırasında:

```java
LocalDate bitisTarihi = request.bitisTarihi() == null
        ? request.tarih()
        : request.bitisTarihi();
tatil.setBitisTarihi(bitisTarihi);
```

Response mapping:

```java
return new TatilResponse(
        tatil.getTatilId(),
        tatil.getAd(),
        tatil.getTarih(),
        tatil.getBitisTarihi(),
        tatil.getKategori(),
        tatil.getGunSuresi()
);
```

Test fixture'larındaki bütün doğrudan `Tatil` kurulumları için bitiş başlangıca eşitlenmelidir.

- [ ] **Step 6: Hedef testi ve mevcut tatil test compile'ını doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#eskiTekGunRequestiAyniBaslangicVeBitisleCalisir" "-Djacoco.skip=true" test
.\mvnw.cmd "-DskipTests" "-Djacoco.skip=true" test-compile
```

Expected: hedef test ve test compilation başarılı.

---

### Task 3: Kesişen grup listeleme ve çok günlük iş günü hesabı

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/repository/TatilRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IsGunuHesaplamaService.java`
- Modify: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/IsGunuHesaplamaServiceTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java`

**Interfaces:**

- Produces: `List<Tatil> findAllKesisen(LocalDate baslangic, LocalDate bitis)`.
- Consumes: `Tatil.tarih` ve `Tatil.bitisTarihi` dahil aralığı.

- [ ] **Step 1: Ay sınırını kesen grup için failing listeleme testi yaz**

1 Ağustos–5 Ağustos sorgusunda 29 Temmuz–2 Ağustos grubu dönmeli; 28 Temmuz'da biten ilgisiz grup dönmemelidir:

```java
mockMvc.perform(get("/api/holidays")
        .param("baslangicTarihi", "2026-08-01")
        .param("bitisTarihi", "2026-08-05")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(personel, RolAdi.PERSONEL)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.length()").value(1))
    .andExpect(jsonPath("$[0].tarih").value("2026-07-29"))
    .andExpect(jsonPath("$[0].bitisTarihi").value("2026-08-02"));
```

- [ ] **Step 2: Çok günlük tam/yarım iş günü için failing unit testleri yaz**

```java
@Test
void cokGunlukYarimTatilHerKapsananHaftaIcineYarimKatkiYapar() {
    Tatil grup = tatil(
            LocalDate.of(2026, 8, 24),
            LocalDate.of(2026, 8, 28),
            TatilGunSuresi.YARIM_GUN
    );
    when(tatilRepository.findAllKesisen(baslangic, bitis))
            .thenReturn(List.of(grup));

    assertEquals(
            new BigDecimal("2.5"),
            service.isGunuSayisiniHesapla(baslangic, bitis)
    );
}
```

Ayrı test; hafta sonuna taşan tam günlük grubun yalnız hafta içlerini düşmesini ve preview aralığıyla kısmi kesişimi doğrulamalıdır.

- [ ] **Step 3: RED'leri doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#oncekiAyBaslayanKesisenTatilGrubuListedeDoner,IsGunuHesaplamaServiceTest" "-Djacoco.skip=true" test
```

Expected: eski `tarih between` sorgusu grubu bulamaz ve yeni repository metodu henüz yoktur.

- [ ] **Step 4: Repository kesişim sorgusunu ekle**

```java
@Query("""
        select t
        from Tatil t
        where t.tarih <= :bitisTarihi
          and t.bitisTarihi >= :baslangicTarihi
        order by t.tarih asc, t.tatilId asc
        """)
List<Tatil> findAllKesisen(
        @Param("baslangicTarihi") LocalDate baslangicTarihi,
        @Param("bitisTarihi") LocalDate bitisTarihi
);
```

Liste service'i ve `IsGunuHesaplamaService` eski `findAllByTarihBetweenOrderByTarihAscTatilIdAsc` yerine bu metodu kullanmalıdır.

- [ ] **Step 5: Günün bir grup tarafından kapsanmasını hesapla**

```java
private TatilGunSuresi kapsayanTatilSuresi(
        LocalDate gun,
        List<Tatil> tatiller
) {
    return tatiller.stream()
            .filter(tatil -> !gun.isBefore(tatil.getTarih()))
            .filter(tatil -> !gun.isAfter(tatil.getBitisTarihi()))
            .map(Tatil::getGunSuresi)
            .findFirst()
            .orElse(null);
}
```

Mevcut hafta sonu önceliği korunmalı; repository gün başına tekrar çağrılmamalıdır.

- [ ] **Step 6: Listeleme, unit ve gerçek preview GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#oncekiAyBaslayanKesisenTatilGrubuListedeDoner,IsGunuHesaplamaServiceTest,IzinHesaplamaControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: kesişen liste, tam/yarım grup ve preview testleri başarılı.

---

### Task 4: Çok günlük create/update/delete, validation ve overlap kuralları

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/repository/TatilRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Modify: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/TatilTakvimiServiceTest.java`

**Interfaces:**

- Produces: `boolean cakisanTatilVar(LocalDate baslangic, LocalDate bitis)`.
- Produces: `boolean cakisanBaskaTatilVar(Integer haricTatilId, LocalDate baslangic, LocalDate bitis)`.
- Consumes: `TatilTakvimiKilitRepository.takvimiKilitle()`.

- [ ] **Step 1: Çok günlük happy-path ve validation RED testlerini yaz**

Test matrisi:

```text
11–15 Ağustos create → 201, tek DB satırı, iki tarih doğru
15–11 Ağustos → 400
1 Ocak 2026–2 Ocak 2027 → 400
geçmiş başlangıç → 400
bitisTarihi yok → tek gün
çok günlük update → 204, aynı tatilId, bütün grup değişir
çok günlük delete → 204, tek satır silinir
```

- [ ] **Step 2: Tüm kesişim türleri için RED testlerini yaz**

Mevcut 11–15 Ağustos grubuna karşı şu requestler `409` olmalıdır:

```text
11–15  aynı
10–12  soldan kesişim
14–17  sağdan kesişim
12–14  içeride
10–17  dışarıdan kapsama
13–13  tek gün kesişimi
```

16 Ağustos'ta başlayan grup başarılı olmalıdır. Başarısız her durumda mevcut satır ve audit sayısı değişmemelidir.

- [ ] **Step 3: RED paketini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#hrCokGunlukTatilGrubuOlusturur+tersVeyaCokUzunTatilGrubu400Doner+cakisanTatilGrubu409VeAtomikOlur+cokGunlukGrupGuncellenirVeSilinir" "-Djacoco.skip=true" test
```

Expected: yeni aralık davranışları uygulanmadığı için testler fail.

- [ ] **Step 4: Repository overlap sorgularını ekle**

```java
@Query("""
        select (count(t) > 0)
        from Tatil t
        where t.tarih <= :bitisTarihi
          and t.bitisTarihi >= :baslangicTarihi
        """)
boolean cakisanTatilVar(
        @Param("baslangicTarihi") LocalDate baslangicTarihi,
        @Param("bitisTarihi") LocalDate bitisTarihi
);

@Query("""
        select (count(t) > 0)
        from Tatil t
        where t.tatilId <> :haricTatilId
          and t.tarih <= :bitisTarihi
          and t.bitisTarihi >= :baslangicTarihi
        """)
boolean cakisanBaskaTatilVar(
        @Param("haricTatilId") Integer haricTatilId,
        @Param("baslangicTarihi") LocalDate baslangicTarihi,
        @Param("bitisTarihi") LocalDate bitisTarihi
);
```

- [ ] **Step 5: Ortak aralık doğrulamasını service'e ekle**

```java
private TarihAraligi tatilAraliginiDogrula(TatilKaydetRequest request) {
    LocalDate baslangic = request.tarih();
    LocalDate bitis = request.bitisTarihi() == null
            ? baslangic
            : request.bitisTarihi();

    if (baslangic == null) {
        throw new GecersizIstekException("Tatil başlangıç tarihi zorunludur.");
    }
    if (bitis.isBefore(baslangic)) {
        throw new GecersizIstekException("Tatil bitiş tarihi başlangıç tarihinden önce olamaz.");
    }
    if (ChronoUnit.DAYS.between(baslangic, bitis) > 365) {
        throw new GecersizIstekException("Tatil grubu en fazla 366 gün olabilir.");
    }
    return new TarihAraligi(baslangic, bitis);
}

private record TarihAraligi(LocalDate baslangic, LocalDate bitis) {}
```

Geçmiş create/update request kontrolü normalize aralıktan sonra başlangıca uygulanmalıdır.

- [ ] **Step 6: Mutation başında teknik kilidi al ve overlap'i kontrol et**

Service constructor'a `TatilTakvimiKilitRepository` eklenir. Her mutation transaction'ında ID/aktörün temel doğrulamasından sonra:

```java
private void takvimiKilitle() {
    tatilTakvimiKilitRepository.takvimiKilitle()
            .orElseThrow(() -> new IllegalStateException(
                    "Tatil takvimi kilit satırı bulunamadı."
            ));
}
```

Create overlap:

```java
if (tatilRepository.cakisanTatilVar(aralik.baslangic(), aralik.bitis())) {
    throw new KaynakCakismasiException(
            "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
    );
}
```

Update aynı kontrolü `cakisanBaskaTatilVar(tatilId, aralik.baslangic(), aralik.bitis())` ile yapar. Mevcut hedefin `getTarih().isBefore(today)` kontrolü no-op'tan önce kalır.

- [ ] **Step 7: Persistence fallback mesajını aralık sözleşmesine çevir**

`DataIntegrityViolationException`, aynı güvenli overlap mesajına çevrilmelidir. SQL veya constraint adı response'a taşınmamalıdır.

- [ ] **Step 8: Hedef CRUD/validation/overlap paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest,TatilTakvimiServiceTest" "-Djacoco.skip=true" test
```

Expected: eski ve yeni tatil controller/service testleri başarılı.

---

### Task 5: Gerçek MySQL üzerinde eşzamanlı overlap güvenliği

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/TatilTakvimiConcurrencyIntegrationTest.java`
- Modify only if test exposes a defect: `TatilTakvimiService.java`, `TatilTakvimiKilitRepository.java`

**Interfaces:**

- Consumes: Aynı transaction içindeki `PESSIMISTIC_WRITE` takvim kilidi ve overlap sorgusu.
- Produces: Paralel çakışan iki create için tam olarak bir success ve bir controlled conflict garantisi.

- [ ] **Step 1: Deterministik iki-thread integration testi yaz**

Test `@MockitoSpyBean TatilTakvimiKilitRepository` kullanarak ilk transaction kilidi aldıktan sonra ikinci transaction'ın aynı kilidi istemesini deterministik biçimde koordine etmelidir:

```java
@MockitoSpyBean
private TatilTakvimiKilitRepository kilitRepository;

AtomicInteger kilitCagriSirasi = new AtomicInteger();
CountDownLatch ilkKilitAlindi = new CountDownLatch(1);
CountDownLatch ikinciKilitDenendi = new CountDownLatch(1);

doAnswer(invocation -> {
    int sira = kilitCagriSirasi.incrementAndGet();

    if (sira == 2) {
        ikinciKilitDenendi.countDown();
    }

    Optional<TatilTakvimiKilit> sonuc =
            (Optional<TatilTakvimiKilit>) invocation.callRealMethod();

    if (sira == 1) {
        ilkKilitAlindi.countDown();
        assertTrue(ikinciKilitDenendi.await(5, TimeUnit.SECONDS));
    }

    return sonuc;
}).when(kilitRepository).takvimiKilitle();
```

Önce ilk future başlatılır ve `ilkKilitAlindi.await(...)` ile gerçekten DB kilidini aldığı doğrulanır; ardından ikinci future başlatılır. İkinci çağrı spy'a girip `ikinciKilitDenendi` sinyali verdikten sonra ilk transaction ilerler, kaydı ekler ve commit olur. İkinci transaction kilidi daha sonra alıp overlap sorgusunda ilk kaydı görmelidir.

Her iki executor thread'i Spring proxy üzerinden `tatilOlustur` çağırmalıdır. Her thread kendi `SecurityContext` authentication'ını kurmalı ve sonunda temizlemelidir:

```java
Callable<Sonuc> istek(LocalDate baslangic, LocalDate bitis) {
    return () -> {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                hr.getPersonelId(), null, List.of(new SimpleGrantedAuthority("ROLE_HR"))
        ));
        SecurityContextHolder.setContext(context);
        try {
            service.tatilOlustur(
                    hr.getPersonelId(),
                    new TatilKaydetRequest(
                            "Paralel Tatil",
                            baslangic,
                            bitis,
                            TatilKategori.SIRKET,
                            TatilGunSuresi.TAM_GUN
                    )
            );
            return Sonuc.BASARILI;
        } catch (KaynakCakismasiException exception) {
            return Sonuc.CAKISMA;
        } finally {
            SecurityContextHolder.clearContext();
        }
    };
}
```

İki future aynı başlangıç bariyerinden serbest bırakılmalıdır. Sonuç assertions:

```java
assertEquals(1, sonuclar.stream().filter(s -> s == BASARILI).count());
assertEquals(1, sonuclar.stream().filter(s -> s == CAKISMA).count());
assertEquals(1L, tatilRepository.count());
assertEquals(1L, auditLogRepository.count());
```

Test timeout kullanmalı; deadlock halinde süresiz beklememelidir.

- [ ] **Step 2: Önce concurrency testini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

Expected: Tasarlanan lock doğruysa GREEN. Fail veya timeout olursa SQL loglarından iki transaction'ın kilit sırası incelenir; testi gevşetmek yerine lock akışı düzeltilir.

- [ ] **Step 3: Aynı grup stale update regresyonunu koru**

Mevcut optimistic lock service testleri yeni kilit repository mock'u ile çalıştırılmalı:

```java
when(tatilTakvimiKilitRepository.takvimiKilitle())
        .thenReturn(Optional.of(new TatilTakvimiKilit(1)));
```

Persistence exception olduğunda audit çağrılmadığı doğrulanmaya devam etmelidir.

- [ ] **Step 4: Concurrency ve service paketini birlikte doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiConcurrencyIntegrationTest,TatilTakvimiServiceTest" "-Djacoco.skip=true" test
```

Expected: concurrency, optimistic locking ve fallback testleri başarılı.

---

### Task 6: Audit, rollback, güvenlik ve tam tatil regresyonu

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java`
- Verify: `backend/src/test/java/com/jforce/backend/SecurityHttpIntegrationTest.java`

**Interfaces:**

- Consumes: Mevcut `TATIL_EKLENDI`, `TATIL_GUNCELLENDI`, `TATIL_SILINDI` audit olayları.
- Produces: Grup mutation başına tek audit ve transaction rollback kanıtı.

- [ ] **Step 1: Grup audit assertions ekle**

Her gerçek create/update/delete için:

```java
assertEquals(1L, auditLogRepository.count());
AuditLog audit = auditLogRepository.findAll().getFirst();
assertEquals(AuditEylem.TATIL_EKLENDI, audit.getEylemTipi());
assertEquals(hr.getPersonelId(), audit.getPersonel().getPersonelId());
assertNull(audit.getHedefPersonel());
assertEquals("Tatil oluşturuldu.", audit.getDetay());
```

Audit detayında tatil adı, tarih aralığı, token veya başka hassas veri bulunmamalıdır.

- [ ] **Step 2: No-op ve başarısızlık auditsizliğini doğrula**

Şu durumların her birinde audit `0` kalmalıdır:

```text
normalize aynı update
overlap create
overlap update
geçmiş update/delete
validation 400
yetkisiz rol
```

- [ ] **Step 3: Dış transaction rollback testlerini grup alanlarıyla genişlet**

Create/update/delete helper'ları dışarıda `IllegalStateException` fırlattığında DB'de grup, iki tarih, version ve audit birlikte eski durumuna dönmelidir.

- [ ] **Step 4: Rol ve read-only regresyonunu çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest,IzinHesaplamaControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: bütün aktif roller listeleyebilir; yalnız HR/Admin mutation yapar; preview PERSONEL/HR matrisini korur; testler başarılı.

- [ ] **Step 5: Tatil ve izin odaklı geniş paketi çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=TatilGrupMigrationIntegrationTest,TatilTableMigrationIntegrationTest,TatilTakvimiControllerIntegrationTest,TatilTakvimiServiceTest,TatilTakvimiConcurrencyIntegrationTest,IsGunuHesaplamaServiceTest,IzinHesaplamaControllerIntegrationTest,ProfilControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: tüm odaklı testler `0 failure, 0 error` ve build success.

---

### Task 7: API/güvenlik belgeleri ve nihai doğrulama

**Files:**

- Modify: `API_SPEC.md`
- Modify: `SECURITY.md`
- Modify: `PROGRESS.md`
- Modify: `ROADMAP.md`

**Interfaces:**

- Consumes: Testlerle doğrulanmış nihai endpoint ve migration davranışı.
- Produces: Frontend ve deployment için güncel tatil grubu sözleşmesi.

- [ ] **Step 1: `API_SPEC.md` sözleşmesini güncelle**

Şunları açıkça kaydet:

```text
tarih = dahil başlangıç
bitisTarihi = opsiyonel request / zorunlu response dahil bitiş
bitis yoksa tek gün
listeleme aralık kesişimi
366 gün sınırı
overlap 409 mesajı
geçmiş grup politikası
POST 201, PATCH/DELETE 204
```

Çok günlük response örneği ekle; eski tek günlük request'in geçerli kaldığını belirt.

- [ ] **Step 2: `SECURITY.md` ve deployment notlarını güncelle**

Şunları kaydet:

```text
HR/Admin DB rol doğrulaması
global şirket takvimi mutation kilidi
overlap check + mutation + audit aynı transaction
optimistic locking ek savunması
read-only listeleme/auditsizlik
manual migration'ın uygulama deploy'undan önce çalıştırılması
```

Mevcut audit ENUM migration eksikliğini çözülmüş gibi işaretleme; ayrı risk olarak bırak.

- [ ] **Step 3: `PROGRESS.md` ve `ROADMAP.md` gerçek durumu güncelle**

Yalnız başarıyla doğrulanan maddeleri tamamlandı olarak işaretle. Frontend tatil grubu gösterimini backend tamamlandı diye bitmiş göstermemelidir.

- [ ] **Step 4: Tam backend doğrulamasını çalıştır**

Run:

```powershell
.\mvnw.cmd test
```

Expected:

```text
Failures: 0
Errors: 0
BUILD SUCCESS
All coverage checks have been met.
```

- [ ] **Step 5: Migration rollout kontrolünü raporla**

Teslim raporu şunları içermelidir:

```text
değişen dosyalar ve sorumlulukları
kesin request/response sözleşmesi
rol ve hata matrisi
migration'ın eski kayıtları nasıl koruduğu
takvim kilidi ve overlap akışı
iş günü hesabı
audit/rollback davranışı
odaklı ve tam test sonuçları
çalıştırılmayan kontroller
mevcut DB'ye migration'ın ayrıca uygulanması gereği
audit ENUM kalıcı migration'ının ayrı açık risk olduğu
```

---

## Execution Notes

- Her task kendi RED → minimal GREEN → ilgili regresyon döngüsünde tamamlanmalıdır.
- Bir RED, hedeflenen eksik davranış yerine Docker/Testcontainers erişimi nedeniyle oluşursa production koduna geçmeden önce gerçek davranış RED'i kullanıcı ortamında doğrulanmalıdır.
- Aynı migration/entity/service dosyalarında eşzamanlı çalışan birden fazla agent kullanılmamalıdır.
- Kullanıcı backend kodunu varsayılan olarak kendisi yazar; agent'ın uygulaması için ayrıca açık backend yazma izni gerekir.
- Git işlemlerini yalnız kullanıcı yapar.
