# Ekipman Talep, Envanter ve Zimmet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif Personel/HR self-service talebinden Yönetici/Admin kararına, fiziksel cihaz seçimine, atomik zimmete ve Admin iadesine uzanan güvenli ekipman backend modülünü tamamlamak.

**Architecture:** Mevcut controller–service–repository ayrımı korunur; ekipman tipi, fiziksel envanter, talep/karar ve zimmet use-case'leri ayrı somut service sınıflarında tutulur. Mutation'lar hedef owner → talep → ekipman → zimmet kilit sırasını kullanır; talep, cihaz, zimmet ve audit aynı transaction içinde tamamlanır. Manual MySQL migration, `ddl-auto=update` davranışına bırakılmayacak constraint/index/audit ENUM değişikliklerini taşır.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Web, Spring Security method security, Spring Data JPA/Hibernate, Jakarta Validation, MySQL 8, Testcontainers, JUnit 5, Mockito, Maven Wrapper, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-08-28-ekipman-talep-envanter-zimmet-design.md`

## Global Constraints

- Git komutlarını yalnız kullanıcı çalıştırır; plan adımlarında agent Git komutu çalıştırmaz.
- Frontend bu planın kapsamı dışındadır.
- Controller iş kuralı veya repository erişimi içermez.
- Entity doğrudan response olarak dönmez.
- Aktör yalnız JWT principal'dan alınır; body/query içindeki owner veya rol bilgisine güvenilmez.
- Method security yanında aktiflik, gerçek DB rolü, ownership ve direct-report kapsamı service'te doğrulanır.
- Bütün büyük listeler `SayfaliResponse` kullanır; varsayılan `page=0,size=20`, maksimum `100` olur.
- Path UUID'leri canonical biçimde, lookup ID'leri pozitif olarak doğrulanır.
- Audit ana mutation transaction'ına katılır; sabit detaylar UUID, seri numarası, açıklama veya karar nedeni taşımaz.
- MySQL audit ENUM değişikliği manual migration ve gerçek MySQL testi olmadan tamamlanmış sayılmaz.
- Her task sonunda ilgili hedef testler çalıştırılır; tam suite yalnız son doğrulama task'ında çalıştırılır.
- Her task sonundaki “Kullanıcı review checkpoint'i” Git işlemi değildir; kullanıcı isterse Git adımlarını kendisi yürütür.

---

## File Responsibility Map

### Model ve migration

- `backend/db/manual-migrations/2026-08-28-add-equipment-workflow.sql`: additive/backfill/constraint/index/audit ENUM migration'ı.
- `backend/src/main/java/com/jforce/backend/model/entity/EkipmanTipi.java`: tip aktifliği.
- `backend/src/main/java/com/jforce/backend/model/entity/EkipmanBilgileri.java`: kontrollü cihaz durumu ve kullanım dışı nedeni.
- `backend/src/main/java/com/jforce/backend/model/entity/EkipmanTalep.java`: karar notu ve kesin timestamp/nullability eşlemesi.
- `backend/src/main/java/com/jforce/backend/model/entity/ZimmetlemeBilgileri.java`: nullable unique talep ilişkisi.
- `backend/src/main/java/com/jforce/backend/model/enums/EkipmanDurum.java`: `KULLANIM_DISI`.
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`: ekipman audit olayları.

### Repository ve service

- `EkipmanTipiRepository`: aktif katalog, Admin filtreleri ve lock.
- `EkipmanBilgileriRepository`: Admin filtreleri, talep kapsamındaki uygun cihazlar ve cihaz lock.
- `EkipmanTalepRepository`: owner/team/Admin listeleri, pending duplicate ve talep lock.
- `ZimmetlemeBilgileriRepository`: my/Admin geçmişi, açık zimmet ve zimmet lock.
- `EkipmanAktoruYetkiService`: aktiflik, DB rolü, self-service ve karar kapsamı doğrulaması.
- `EkipmanTipiService`: güvenli katalog ve Admin tip yaşam döngüsü.
- `EkipmanEnvanterService`: cihaz create/metadata/status yönetimi.
- `EkipmanTalepService`: self-service create/list/update/cancel.
- `EkipmanTalepKararService`: team/Admin liste, uygun cihaz, approve/reject.
- `ZimmetService`: my/Admin liste ve iade.

### Controller

- `EkipmanTipiController`: aktif katalog.
- `AdminEkipmanTipiController`: Admin tip list/create/update.
- `AdminEkipmanController`: Admin fiziksel envanter.
- `EkipmanTalepController`: self-service talep endpoint'leri.
- `EkipmanTalepKararController`: Yönetici team/available/approve/reject endpoint'leri.
- `AdminEkipmanTalepController`: Admin şirket-geneli liste; approve/reject ortak karar service'ine gider.
- `ZimmetController`: kendi zimmetleri.
- `AdminZimmetController`: şirket zimmetleri ve iade.

### Testler

- `EkipmanWorkflowMigrationIntegrationTest`: legacy şema ve idempotent migration.
- `EkipmanTipiControllerIntegrationTest`: katalog/Admin tip yönetimi.
- `EkipmanEnvanterControllerIntegrationTest`: fiziksel envanter ve durum geçişleri.
- `EkipmanTalepControllerIntegrationTest`: self-service akış.
- `EkipmanTalepKararControllerIntegrationTest`: team/Admin karar ve zimmet atomikliği.
- `ZimmetControllerIntegrationTest`: my/Admin zimmet ve iade.
- `EkipmanConcurrencyIntegrationTest`: paralel create/karar/zimmet/iade.
- Mevcut `HrPersonelControllerIntegrationTest`, `AdminPersonelControllerIntegrationTest` ve `SecurityHttpIntegrationTest`: çapraz modül regresyonları.

---

### Task 1: Manual migration ve entity değişmezleri

**Files:**
- Create: `backend/db/manual-migrations/2026-08-28-add-equipment-workflow.sql`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanWorkflowMigrationIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/EkipmanTipi.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/EkipmanBilgileri.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/EkipmanTalep.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/ZimmetlemeBilgileri.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/EkipmanDurum.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Interfaces:**
- Consumes: mevcut MySQL tabloları `ekipman_tipi`, `ekipman_bilgileri`, `ekipman_talep`, `zimmetleme_bilgileri`, `audit_log`.
- Produces: `EkipmanTipi#isAktifMi`, `EkipmanBilgileri#getKullanimDisiNedeni`, `EkipmanTalep#getKararNotu`, `ZimmetlemeBilgileri#getTalep`, `EkipmanDurum.KULLANIM_DISI` ve yeni audit enum değerleri.

- [ ] **Step 1: Legacy şema fixture'ı ve başarısız migration testi yaz**

Test gerçek MySQL container'ında eski kolonlarla tabloları kurmalı; mevcut bir tip, cihaz, talep ve kapalı zimmet eklemeli. Migration öncesinde yeni kolonların bulunmadığını doğrulamalı.

```java
@Test
void migrationEkipmanAkisiniConstraintlerleGenisletirVeVeriyiKorur() {
    legacySemayiVeVeriyiHazirla();
    migrationCalistir();

    assertTrue(columnExists("ekipman_tipi", "aktif_mi"));
    assertEquals(Boolean.TRUE,
            jdbcTemplate.queryForObject(
                    "SELECT aktif_mi FROM ekipman_tipi WHERE ekipman_tipi_id = 1",
                    Boolean.class));
    assertTrue(columnExists("ekipman_talep", "karar_notu"));
    assertTrue(columnExists("zimmetleme_bilgileri", "talep_id"));
    assertTrue(indexExists("zimmetleme_bilgileri", "uk_zimmet_talep"));
    assertTrue(indexExists("zimmetleme_bilgileri", "uk_zimmet_acik_ekipman"));
    assertAuditEnumJavaIleAyni();

    migrationCalistir();
    assertLegacyVeriKorundu();
}
```

- [ ] **Step 2: Testi çalıştır ve migration dosyası bulunamadığı için başarısız olduğunu doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=EkipmanWorkflowMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: FAIL; SQL dosyası okunamaz veya beklenen kolon/constraint bulunmaz.

- [ ] **Step 3: Idempotent manual migration'ı yaz**

Migration sırası:

```sql
ALTER TABLE ekipman_tipi
    ADD COLUMN aktif_mi BOOLEAN NULL;

UPDATE ekipman_tipi SET aktif_mi = TRUE WHERE aktif_mi IS NULL;

ALTER TABLE ekipman_tipi
    MODIFY COLUMN aktif_mi BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE ekipman_talep
    ADD COLUMN karar_notu VARCHAR(500) NULL;

ALTER TABLE ekipman_bilgileri
    ADD COLUMN kullanim_disi_nedeni VARCHAR(500) NULL;

ALTER TABLE zimmetleme_bilgileri
    ADD COLUMN talep_id VARCHAR(36) NULL;
```

Gerçek dosyada her kolon, index ve constraint `information_schema` ile varlık/tanım kontrolünden sonra eklenmeli. Açık zimmet uniqueness'i MySQL generated nullable kolonuyla kurulmalı:

```sql
ALTER TABLE zimmetleme_bilgileri
    ADD COLUMN acik_ekipman_id VARCHAR(36)
    GENERATED ALWAYS AS (
        CASE WHEN iade_tarihi IS NULL THEN ekipman_id ELSE NULL END
    ) STORED;

CREATE UNIQUE INDEX uk_zimmet_acik_ekipman
    ON zimmetleme_bilgileri (acik_ekipman_id);
```

Audit ENUM `AuditEylem` içindeki bütün mevcut ve yeni değerleri birebir taşımalı; önceki değerlerden hiçbiri düşürülmemeli.

- [ ] **Step 4: Entity ve enum eşlemelerini migration ile aynı hâle getir**

```java
public enum EkipmanDurum {
    DEPODA,
    PERSONELDE,
    KULLANIM_DISI
}
```

```java
@Column(name = "aktif_mi", nullable = false)
private boolean aktifMi = true;

@Column(name = "kullanim_disi_nedeni", length = 500)
private String kullanimDisiNedeni;

@Column(name = "karar_notu", length = 500)
private String kararNotu;

@OneToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "talep_id", unique = true)
private EkipmanTalep talep;
```

Yeni audit olaylarını ekle:

```java
EKIPMAN_TALEBI_OLUSTURULDU,
EKIPMAN_TALEBI_ONAYLANDI,
EKIPMAN_TALEBI_REDDEDILDI,
EKIPMAN_TALEBI_IPTAL_EDILDI,
EKIPMAN_TIPI_EKLENDI,
EKIPMAN_TIPI_GUNCELLENDI,
EKIPMAN_EKLENDI,
EKIPMAN_GUNCELLENDI,
ZIMMET_IADE_EDILDI
```

- [ ] **Step 5: Migration testini ve entity persistence smoke testini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanWorkflowMigrationIntegrationTest,BackendApplicationTests" "-Djacoco.skip=true" test
```

Expected: PASS; migration ikinci çalıştırmada veri kaybetmez ve application context açılır.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Migration'ın gerçek ortamda uygulanmadan önce yedek/bakım penceresi gerektirdiğini, hiçbir production DB işlemi yapılmadığını ve yalnız repository SQL dosyasının hazırlandığını raporla.

---

### Task 2: Repository ve DTO sözleşme temeli

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/repository/EkipmanTipiRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/EkipmanBilgileriRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/EkipmanTalepRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/ZimmetlemeBilgileriRepository.java`
- Create/Modify: ilgili `model/dto/request/Ekipman*.java` ve `model/dto/response/Ekipman*.java`, `ZimmetResponse.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 entity alanları.
- Produces: sonraki service task'larının kullanacağı repository lock/list metotları ve güvenli DTO record'ları.

- [ ] **Step 1: Repository sözleşmelerini gerçek MySQL testiyle tanımla**

```java
@Test
void uygunCihazSorgusuYalnizAyniTipDepodaCihazlariKararliDoner() {
    Page<EkipmanBilgileri> sonuc = ekipmanRepository
            .findUygunCihazlar(tipId, EkipmanDurum.DEPODA,
                     PageRequest.of(0, 20));

    assertThat(sonuc.getContent())
            .extracting(EkipmanBilgileri::getEkipmanDurum)
            .containsOnly(EkipmanDurum.DEPODA);
    assertThat(sonuc.getContent())
            .extracting(e -> e.getEkipmanTipiId().getEkipmanTipiId())
            .containsOnly(tipId);
}
```

Aynı test sınıfı talep `findByIdForUpdate`, cihaz `findByIdForUpdate`, zimmet `findByIdForUpdate`, duplicate pending ve açık zimmet sorgularını doğrulamalı.

- [ ] **Step 2: Testi repository'ler bulunmadığı için başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanRepositoryIntegrationTest" "-Djacoco.skip=true" test
```

Expected: test compilation FAIL; repository tipleri veya metotları yoktur.

- [ ] **Step 3: Repository interface'lerini minimum sorgularla ekle**

```java
public interface EkipmanBilgileriRepository
        extends JpaRepository<EkipmanBilgileri, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EkipmanBilgileri e WHERE e.ekipmanId = :id")
    Optional<EkipmanBilgileri> findByIdForUpdate(@Param("id") String id);

    @Query("""
            SELECT e FROM EkipmanBilgileri e
            WHERE e.ekipmanTipiId.ekipmanTipiId = :tipId
              AND e.ekipmanDurum = :durum
            ORDER BY e.marka, e.model, e.seriNo, e.ekipmanId
            """)
    Page<EkipmanBilgileri> findUygunCihazlar(
            @Param("tipId") Integer tipId,
            @Param("durum") EkipmanDurum durum,
            Pageable pageable);

    boolean existsBySeriNoIgnoreCase(String seriNo);
}
```

`EkipmanTalepRepository` en az şu semantik metotları üretmeli:

```java
boolean existsPendingByPersonelAndTip(
        String personelId,
        Integer ekipmanTipiId,
        TalepDurum durum);

@Query("SELECT t.personelId.personelId FROM EkipmanTalep t WHERE t.talepId = :talepId")
Optional<String> findOwnerIdByTalepId(@Param("talepId") String talepId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM EkipmanTalep t WHERE t.talepId = :talepId")
Optional<EkipmanTalep> findByIdForUpdate(@Param("talepId") String talepId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        SELECT t FROM EkipmanTalep t
        WHERE t.personelId.personelId = :personelId
          AND t.talepDurum = :durum
        ORDER BY t.talepId
        """)
List<EkipmanTalep> findPendingByPersonelForUpdate(
        @Param("personelId") String personelId,
        @Param("durum") TalepDurum durum);
```

`ZimmetlemeBilgileriRepository`, iade işlemindeki ortak kilit sırasını kurmak için kilitsiz sahip snapshot'ı ile kilitli zimmet sorgusunu ayrı tutmalı:

```java
@Query("SELECT z.personelId.personelId FROM ZimmetlemeBilgileri z WHERE z.zimmetId = :zimmetId")
Optional<String> findOwnerIdByZimmetId(@Param("zimmetId") String zimmetId);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT z FROM ZimmetlemeBilgileri z WHERE z.zimmetId = :zimmetId")
Optional<ZimmetlemeBilgileri> findByIdForUpdate(@Param("zimmetId") String zimmetId);
```

Sayfalı owner/team/Admin sorguları projection veya `JOIN FETCH`/`@EntityGraph` ile DTO mapping sırasında N+1 üretmeyecek şekilde tanımlanmalı.

- [ ] **Step 4: Request/response DTO'larını endpoint use-case'lerinden türet**

Örnek request'ler:

```java
public record EkipmanTalepOnayRequest(
        @NotBlank String ekipmanId
) {}

public record EkipmanTalepRedRequest(
        @NotBlank @Size(max = 500) String kararNotu
) {}

public record EkipmanDurumUpdateRequest(
        @NotNull EkipmanDurum durum,
        @Size(max = 500) String neden
) {}
```

Uygun cihaz response'u yalnız güvenli seçim alanlarını taşımalı:

```java
public record UygunEkipmanResponse(
        String id,
        String marka,
        String model,
        String seriNo
) {}
```

- [ ] **Step 5: Repository testini ve tüm main/test compilation'ı çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanRepositoryIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; sorgular MySQL 8 üzerinde doğru lock/list davranışı gösterir.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Yeni repository sorgularını, DTO'ların güvenli alanlarını ve hiçbir endpoint'in henüz açılmadığını raporla.

---

### Task 3: Aktif katalog ve Admin ekipman tipi yönetimi

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/EkipmanAktoruYetkiService.java`
- Create: `backend/src/main/java/com/jforce/backend/service/EkipmanTipiService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/EkipmanTipiController.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/AdminEkipmanTipiController.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanTipiControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/config/SecurityConfig.java` yalnız route-level erişim gerekiyorsa.

**Interfaces:**
- Consumes: Task 2 `EkipmanTipiRepository` ve tip DTO'ları.
- Produces: `GET /api/equipment-types` ile Admin list/create/PATCH sözleşmeleri; sonraki task'lar aktif tip doğrulaması için `EkipmanTipiService#aktifTipGetir(Integer)` kullanır.

- [ ] **Step 1: Katalog ve Admin rol matrisinin failing integration testlerini yaz**

```java
@ParameterizedTest
@EnumSource(value = RolAdi.class)
void aktifRollerYalnizAktifTipleriGuvenliVeAlfabetikGorur(RolAdi rol) {
    mockMvc.perform(get("/api/equipment-types")
                    .with(jwtFor(aktifPersonel(rol))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ad").value("Klavye"))
            .andExpect(jsonPath("$[0].aktifMi").doesNotExist());
}

@Test
void bekleyenTalebiBulunanTipPasifeAlinamaz() {
    mockMvc.perform(patch("/api/admin/equipment-types/{id}", tipId)
                    .with(jwtFor(admin))
                    .contentType(APPLICATION_JSON)
                    .content("{\"aktifMi\":false}"))
            .andExpect(status().isConflict());
}
```

- [ ] **Step 2: Testleri 404 ile başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTipiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: FAIL; endpoint'ler henüz yoktur.

- [ ] **Step 3: Aktör doğrulama ve katalog okumayı uygula**

```java
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public List<EkipmanTipiLookupResponse> aktifTipleriGetir(String aktorId) {
    ekipmanAktoruYetkiService.aktifAktorGetir(aktorId);
    return ekipmanTipiRepository.findAllByAktifMiTrueOrderByTipAdiAscEkipmanTipiIdAsc()
            .stream()
            .map(t -> new EkipmanTipiLookupResponse(
                    t.getEkipmanTipiId(), t.getTipAdi()))
            .toList();
}
```

- [ ] **Step 4: Admin create/PATCH ve no-op davranışını uygula**

Admin aktörü DB'den tekrar doğrula; adı trim et; case-insensitive duplicate'i kontrollü `409` yap. Pasifleştirmede tip satırını kilitle ve pending talep sayısını kontrol et. Gerçek değişiklikte `EKIPMAN_TIPI_EKLENDI/GUNCELLENDI` audit'i aynı transaction'a yaz.

```java
if (Objects.equals(tip.getTipAdi(), normalizeAd)
        && tip.isAktifMi() == hedefAktiflik) {
    return;
}
```

- [ ] **Step 5: Katalog/Admin tip testlerinin tamamını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTipiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; rol, aktiflik, duplicate, pasifleştirme, no-op, audit ve rollback senaryoları geçer.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Aktif katalog ve Admin tip yönetiminin hazır olduğunu; talep/envanter endpoint'lerinin henüz hazır olmadığını raporla.

---

### Task 4: Admin fiziksel envanter yönetimi

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/EkipmanEnvanterService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/AdminEkipmanController.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanEnvanterControllerIntegrationTest.java`
- Modify: mevcut `EkipmanCreateRequest.java`, `EkipmanUpdateRequest.java`, `EkipmanResponse.java`.

**Interfaces:**
- Consumes: `EkipmanTipiService#aktifTipGetir`, `EkipmanBilgileriRepository`, `ZimmetlemeBilgileriRepository`.
- Produces: `/api/admin/equipment` list/create/metadata/status endpoint'leri ve karar task'ının kullanacağı güvenilir fiziksel cihaz kayıtları.

- [ ] **Step 1: Create, metadata ve durum geçişlerinin failing testlerini yaz**

```java
@Test
void adminCihaziBodyDurumunaBakmadanDepodaOlusturur() {
    mockMvc.perform(post("/api/admin/equipment")
                    .with(jwtFor(admin))
                    .contentType(APPLICATION_JSON)
                    .content(validCreateJson()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.durum").value("DEPODA"));
}

@Test
void personeldekiCihazGenericPatchIleDepoyaAlinamaz() {
    mockMvc.perform(patch("/api/admin/equipment/{id}/status", cihazId)
                    .with(jwtFor(admin))
                    .contentType(APPLICATION_JSON)
                    .content("{\"durum\":\"DEPODA\"}"))
            .andExpect(status().isConflict());
}
```

- [ ] **Step 2: Testleri endpoint yokluğu nedeniyle başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanEnvanterControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: FAIL with 404.

- [ ] **Step 3: Create ve sayfalı Admin listeyi uygula**

Create body'den durum kabul etme; tip aktif olmalı; marka/seri trim sonrası non-blank, model blank ise `null`; seri duplicate'i precheck + DB exception dönüşümüyle `409` yap.

```java
EkipmanBilgileri cihaz = new EkipmanBilgileri();
cihaz.setEkipmanTipiId(aktifTip);
cihaz.setMarka(normalizeMarka);
cihaz.setModel(normalizeNullableModel);
cihaz.setSeriNo(normalizeSeriNo);
cihaz.setEkipmanDurum(EkipmanDurum.DEPODA);
```

- [ ] **Step 4: Metadata ve ayrı status PATCH'i uygula**

Metadata PATCH tip/durum taşımaz. Status service'i cihazı `FOR UPDATE` yükler:

```java
switch (cihaz.getEkipmanDurum()) {
    case DEPODA -> kullanimDisiVeyaNoOp(cihaz, request);
    case KULLANIM_DISI -> depoyaAlVeyaNoOp(cihaz, request);
    case PERSONELDE -> throw new KaynakCakismasiException(
            "Personeldeki ekipmanın durumu iade edilmeden değiştirilemez.");
}
```

`KULLANIM_DISI` için normalize neden `1..500`; `DEPODA`ya dönüşte neden `null` olur. Gerçek değişiklik `EKIPMAN_GUNCELLENDI`, create `EKIPMAN_EKLENDI` audit'i üretir.

- [ ] **Step 5: Envanter testlerinin tamamını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanEnvanterControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; güvenli Admin-only alanlar, filtreler, transition, no-op, audit ve rollback geçer.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Fiziksel stok yönetiminin hazır olduğunu; `PERSONELDE` durumunun hâlâ yalnız sonraki karar/zimmet task'ında oluşacağını raporla.

---

### Task 5: Personel/HR self-service ekipman talepleri

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/EkipmanTalepService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/EkipmanTalepController.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanTalepControllerIntegrationTest.java`
- Modify: `EkipmanTalepCreateRequest.java`, `EkipmanTalepResponse.java` ve Task 2 update DTO'su.

**Interfaces:**
- Consumes: `EkipmanAktoruYetkiService`, `EkipmanTipiService`, `EkipmanTalepRepository`, `DurumTuruRepository`, `AuditService`, `Clock`.
- Produces: POST/my/PATCH/cancel endpoint'leri ve kilit altında owner+tip pending invariant'ı.

- [ ] **Step 1: Rol, create, duplicate, update ve cancel failing testlerini yaz**

```java
@ParameterizedTest
@EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR"})
void personelVeHrAktifTipIcinTalepOlusturur(RolAdi rol) {
    mockMvc.perform(post("/api/equipment-requests")
                    .with(jwtFor(aktifPersonel(rol)))
                    .contentType(APPLICATION_JSON)
                    .content("{\"ekipmanTipiId\":1,\"aciklama\":\"  İkinci ekran  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.aciklama").value("İkinci ekran"))
            .andExpect(jsonPath("$.durum").value("ONAY_BEKLIYOR"));
}

@Test
void ayniOwnerVeTipIcinIkinciBekleyenTalep409Doner() {
    talepOlustur(owner, tip);
    talepOlusturIstegi(owner, tip).andExpect(status().isConflict());
}
```

- [ ] **Step 2: Testleri 404 ile başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: FAIL; endpoint yoktur.

- [ ] **Step 3: Create ve `/my` akışını uygula**

```java
@Transactional
@PreAuthorize("hasAnyRole('PERSONEL', 'HR')")
public EkipmanTalepResponse talepOlustur(
        String aktorId,
        EkipmanTalepCreateRequest request) {
    Personel owner = personelRepository.findByIdForUpdate(aktorId)
            .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
    ekipmanAktoruYetkiService.selfServiceAktoruDogrula(owner);
    EkipmanTipi tip = ekipmanTipiService.aktifTipGetir(request.ekipmanTipiId());
    duplicatePendingDogrula(owner, tip);

    EkipmanTalep talep = new EkipmanTalep();
    talep.setPersonelId(owner);
    talep.setEkipmanTipiId(tip);
    talep.setAciklama(normalizeNullableText(request.aciklama(), 255));
    talep.setTalepDurum(TalepDurum.ONAY_BEKLIYOR);
    talep.setTalepTarihi(LocalDateTime.now(clock));

    EkipmanTalep kaydedilen = ekipmanTalepRepository.saveAndFlush(talep);
    auditService.logKaydetOrtakTransaction(
            AuditEylem.EKIPMAN_TALEBI_OLUSTURULDU,
            owner,
            owner,
            "Ekipman talebi oluşturuldu.");
    return responseaDonustur(kaydedilen);
}
```

Entity alan adları Task 1'de oluşturulan modelle birebir eşleştirilmeli; değişmezler `talepTarihi=LocalDateTime.now(clock)`, durum `ONAY_BEKLIYOR` ve owner'ın yalnız doğrulanmış principal olmasıdır.

- [ ] **Step 4: Bekleyen update ve cancel akışını uygula**

Her iki işlem owner satırını, ardından talebi kilitlemeli. Ownership durum kontrolünden önce doğrulanmalı; kapsam dışı talep güvenli `404`, terminal talep `409` almalı. Update normalize no-op ve auditsiz; cancel `EKIPMAN_TALEBI_IPTAL_EDILDI` audit'i üretir.

- [ ] **Step 5: Self-service testlerinin tamamını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; role, inactive/missing principal, validation, duplicate, pagination, ownership, no-op, audit ve rollback geçer.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Self-service'in hazır olduğunu; henüz Yönetici/Admin karar endpoint'lerinin eklenmediğini raporla.

---

### Task 6: Yönetici/Admin kuyrukları ve uygun cihaz read-model'i

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/EkipmanTalepKararService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/EkipmanTalepKararController.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/AdminEkipmanTalepController.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanTalepKararControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 2 sayfalı sorgular ve güvenli karar DTO'ları, `EkipmanAktoruYetkiService`.
- Produces: `/team`, `/api/admin/equipment-requests` ve `/{talepId}/available-equipment` read endpoint'leri; Task 7 aynı service içinde mutation metotlarını tamamlar.

- [ ] **Step 1: Scope ve sızıntı failing testlerini yaz**

```java
@Test
void yoneticiYalnizAktifDogrudanCalisanTalepleriniGorur() {
    mockMvc.perform(get("/api/equipment-requests/team")
                    .with(jwtFor(yonetici)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.icerik.length()").value(1))
            .andExpect(jsonPath("$.icerik[0].sahip.ad").value("Ayşe"))
            .andExpect(jsonPath("$.icerik[0].sahip.email").doesNotExist());
}

@Test
void uygunCihazListesiYalnizTalepTipindekiDepodaCihazlariDoner() {
    mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", talepId)
                    .with(jwtFor(yetkiliYonetici)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.icerik[0].seriNo").exists())
            .andExpect(jsonPath("$.icerik[0].durum").doesNotExist());
}
```

- [ ] **Step 2: Testleri endpoint yokluğu nedeniyle başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepKararControllerIntegrationTest#yoneticiYalnizAktifDogrudanCalisanTalepleriniGorur+uygunCihazListesiYalnizTalepTipindekiDepodaCihazlariDoner" "-Djacoco.skip=true" test
```

Expected: FAIL with 404.

- [ ] **Step 3: Team/Admin repository scope sorgularını service'e bağla**

Team query'nin zorunlu predicate'i `owner.yonetici.personelId = aktorId AND owner.calisiyorMu = true` olmalı. Admin query aktif/pasif bütün owner'ları kapsamalı. Her iki liste filtreleri `AND`, sayfalama bounded ve sıra kararlı olmalı.

- [ ] **Step 4: Talep-kapsamlı uygun cihaz endpoint'ini uygula**

```java
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
public SayfaliResponse<UygunEkipmanResponse> uygunCihazlariGetir(
        String aktorId,
        String talepId,
        int page,
        int size) {
    Personel aktor = ekipmanAktoruYetkiService.kararAktoruGetir(aktorId);
    EkipmanTalep talep = kapsamIcindeTalepGetir(aktor, canonicalUuid(talepId));
    return uygunCihazSayfasinaDonustur(
            ekipmanRepository.findUygunCihazlar(
                    talep.getEkipmanTipi().getEkipmanTipiId(),
                    EkipmanDurum.DEPODA,
                    pageRequest(page, size)));
}
```

- [ ] **Step 5: Read-model test grubunu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: mevcut read senaryoları PASS; mutation testleri bu task'ta henüz eklenmemiştir.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Yönetici/Admin kuyrukları ile fiziksel cihaz seçkisinin hazır olduğunu; onay/red mutation'larının sıradaki task olduğunu raporla.

---

### Task 7: Atomik onay/red ve zimmet oluşturma

**Files:**
- Modify: `EkipmanTalepKararService.java`
- Modify: `EkipmanTalepKararController.java`
- Modify: `AdminEkipmanTalepController.java`
- Modify: `EkipmanTalepKararControllerIntegrationTest.java`
- Create: `backend/src/test/java/com/jforce/backend/EkipmanTalepKararServiceTest.java`

**Interfaces:**
- Consumes: `EkipmanTalepOnayRequest`, `EkipmanTalepRedRequest`, request/equipment/assignment lock repository metotları.
- Produces: `talebiOnayla(String,String,EkipmanTalepOnayRequest)` ve `talebiReddet(String,String,EkipmanTalepRedRequest)`; onaylı talep+cihaz+zimmet değişmezi.

- [ ] **Step 1: Onay/red/rollback failing testlerini yaz**

```java
@Test
void yoneticiSecilenCihazlaTalebiAtomikOnaylar() throws Exception {
    mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talepId)
                    .with(jwtFor(yonetici))
                    .contentType(APPLICATION_JSON)
                    .content("{\"ekipmanId\":\"" + cihazId + "\"}"))
            .andExpect(status().isNoContent());

    assertEquals("ONAYLANDI", talepDurumu(talepId));
    assertEquals("PERSONELDE", cihazDurumu(cihazId));
    assertEquals(1, talebeBagliZimmetSayisi(talepId));
    tekGuvenliAudit(AuditEylem.EKIPMAN_TALEBI_ONAYLANDI, yonetici, owner);
}
```

Yanlış tip, cihazın artık `DEPODA` olmaması, owner pasifliği, terminal talep, zorunlu red nedeni, Admin olumlu akışı, Yönetici başka takım ve dış transaction rollback testlerini aynı sınıfa ekle.

- [ ] **Step 2: Testleri endpoint/mutation eksikliği nedeniyle başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: yeni mutation testleri FAIL.

- [ ] **Step 3: Ortak karar yetkisini durum kontrolünden önce uygula**

```java
private void kararYetkisiniDogrula(Personel aktor, Personel owner) {
    if (aktor.getPersonelId().equals(owner.getPersonelId())) {
        throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor.");
    }
    if (aktor.getRolId().getRolAdi() == RolAdi.ADMIN) {
        return;
    }
    boolean dogrudanCalisan = owner.getYoneticiId() != null
            && owner.getYoneticiId().getPersonelId().equals(aktor.getPersonelId())
            && owner.isCalisiyorMu();
    if (!dogrudanCalisan) {
        throw new KaynakBulunamadiException("Ekipman talebi bulunamadı.");
    }
}
```

- [ ] **Step 4: Onay transaction'ını owner → request → equipment → assignment sırasıyla uygula**

```java
@Transactional
@PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
public void talebiOnayla(
        String aktorId,
        String talepId,
        EkipmanTalepOnayRequest request) {
    Personel aktor = ekipmanAktoruYetkiService.kararAktoruGetir(aktorId);
    String dogrulanmisTalepId = canonicalUuid(talepId);
    String ownerId = ekipmanTalepRepository.findOwnerIdByTalepId(dogrulanmisTalepId)
            .orElseThrow(() -> new KaynakBulunamadiException("Ekipman talebi bulunamadı."));
    Personel owner = personelRepository.findByIdForUpdate(ownerId)
            .orElseThrow(() -> new KaynakBulunamadiException("Ekipman talebi bulunamadı."));
    EkipmanTalep talep = ekipmanTalepRepository.findByIdForUpdate(dogrulanmisTalepId)
            .orElseThrow(() -> new KaynakBulunamadiException("Ekipman talebi bulunamadı."));

    kararYetkisiniDogrula(aktor, owner);
    bekleyenTalepVeAktifOwnerDogrula(talep, owner);

    String ekipmanId = canonicalUuid(request.ekipmanId());
    EkipmanBilgileri ekipman = ekipmanRepository.findByIdForUpdate(ekipmanId)
            .orElseThrow(() -> new KaynakBulunamadiException("Ekipman bulunamadı."));
    secilenCihaziDogrula(talep, ekipman);
    if (zimmetRepository.existsByEkipmanIdAndIadeTarihiIsNull(ekipmanId)) {
        throw new KaynakCakismasiException("Ekipman artık uygun değil.");
    }

    LocalDateTime islemZamani = LocalDateTime.now(clock);
    ZimmetlemeBilgileri zimmet = new ZimmetlemeBilgileri();
    zimmet.setEkipmanId(ekipman);
    zimmet.setPersonelId(owner);
    zimmet.setTalepId(talep);
    zimmet.setTeslimTarihi(islemZamani);
    zimmetRepository.saveAndFlush(zimmet);

    ekipman.setEkipmanDurum(EkipmanDurum.PERSONELDE);
    talep.setTalepDurum(TalepDurum.ONAYLANDI);
    talep.setIslemYapanId(aktor);
    talep.setIslemTarihi(islemZamani);
    talep.setKararNotu(null);
    ekipmanRepository.saveAndFlush(ekipman);
    ekipmanTalepRepository.saveAndFlush(talep);

    auditService.logKaydetOrtakTransaction(
            AuditEylem.EKIPMAN_TALEBI_ONAYLANDI,
            aktor,
            owner,
            "Ekipman talebi onaylandı.");
}
```

Task 1'de seçilen entity alan adları farklıysa setter'lar o modele uyarlanmalı; kilit sırası ve doğrulama sırası değişmemelidir. Constraint kaynaklı yarış `KaynakCakismasiException` ile güvenli `409`a çevrilmeli.

- [ ] **Step 5: Red transaction'ını uygula**

Red nedeni trim sonrası `1..500`; talep `REDDEDILDI`, `islemYapan`, `islemTarihi`, `kararNotu` alanları güncellenir. `EKIPMAN_TALEBI_REDDEDILDI` audit detayı nedeni içermez.

- [ ] **Step 6: Controller integration ve service rollback testlerini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=EkipmanTalepKararControllerIntegrationTest,EkipmanTalepKararServiceTest" "-Djacoco.skip=true" test
```

Expected: PASS; başarısız onay/red hiçbir talep/cihaz/zimmet/audit yan etkisi bırakmaz.

- [ ] **Step 7: Kullanıcı review checkpoint'i**

Talep kararının artık fiziksel cihaz seçimiyle atomik zimmet ürettiğini ve doğrudan zimmet endpoint'inin eklenmediğini raporla.

---

### Task 8: Zimmet listeleri ve Admin iade

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/service/ZimmetService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/ZimmetController.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/AdminZimmetController.java`
- Create: `backend/src/test/java/com/jforce/backend/ZimmetControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 7 zimmet kayıtları, `ZimmetlemeBilgileriRepository`, `EkipmanBilgileriRepository`, `AuditService`, `Clock`.
- Produces: `/api/assignments/my`, `/api/admin/assignments`, `/{zimmetId}/return`.

- [ ] **Step 1: Kendi zimmeti, Admin görünümü ve iade failing testlerini yaz**

```java
@Test
void adminAcikZimmetiIadeEderCihaziDepoyaAlirVeGecmisiKorur() {
    mockMvc.perform(patch("/api/admin/assignments/{id}/return", zimmetId)
                    .with(jwtFor(admin)))
            .andExpect(status().isNoContent());

    assertNotNull(iadeTarihi(zimmetId));
    assertEquals("DEPODA", cihazDurumu(cihazId));
    assertEquals(1, zimmetSatiriSayisi(zimmetId));
    tekGuvenliAudit(AuditEylem.ZIMMET_IADE_EDILDI, admin, owner);
}
```

- [ ] **Step 2: Testleri 404 ile başarısız çalıştır**

```powershell
.\mvnw.cmd "-Dtest=ZimmetControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: FAIL; endpoint'ler yoktur.

- [ ] **Step 3: My/Admin sayfalı read-model'leri uygula**

My query principal ownership predicate'iyle bütün açık/geçmiş zimmetleri döndürür. Admin query aktif/pasif owner'ı ve `aktif`, `personelId`, `ekipmanTipiId` filtrelerini `AND` uygular. Owner response'u başka personel alanı taşımaz; Admin response'u yalnız güvenli owner özeti taşır.

- [ ] **Step 4: İade transaction'ını uygula**

`findOwnerIdByZimmetId` snapshot → owner lock → `findByIdForUpdate` zimmet lock → cihaz lock sırasını kullan. Açık zimmet değilse `409`; iade zamanı `LocalDateTime.now(clock)`; cihaz `DEPODA`, kullanım dışı nedeni `null`; audit aynı transaction'da.

```java
if (zimmet.getIadeTarihi() != null) {
    throw new KaynakCakismasiException("Zimmet artık açık değil.");
}
```

- [ ] **Step 5: Zimmet testlerinin tamamını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=ZimmetControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; ownership, Admin, pasif owner, pagination, çift iade, audit ve rollback geçer.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Zimmet görüntüleme/iadenin hazır olduğunu ve kayıtların fiziksel silinmediğini raporla.

---

### Task 9: Personel pasifleştirme ve rol değişikliği entegrasyonu

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/EkipmanTalepRepository.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/AdminPersonelControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `EkipmanTalepRepository#findPendingByPersonelForUpdate`, `DurumTuruRepository`, `AuditService`.
- Produces: pasifleştirme veya self-service dışı role geçişte pending taleplerin aynı outer transaction'da iptali.

- [ ] **Step 1: Pasifleştirme entegrasyonunun failing testini yaz**

```java
@Test
void personelPasiflestirmeBekleyenEkipmanTalepleriniIptalEderAcikZimmetiKorur() {
    String talepId = bekleyenEkipmanTalebiOlustur(hedef);
    String zimmetId = acikZimmetOlustur(hedef);

    personeliPasiflestirIstegi(hr, hedef)
            .andExpect(status().isNoContent());

    assertEquals("IPTAL_EDILDI", ekipmanTalepDurumu(talepId));
    assertEquals(hr.getPersonelId(), talepIslemYapan(talepId));
    assertNull(zimmetIadeTarihi(zimmetId));
    assertEquals("PERSONELDE", zimmetliCihazDurumu(zimmetId));
}
```

- [ ] **Step 2: Rol değişikliği entegrasyonunun failing testini yaz**

```java
@ParameterizedTest
@EnumSource(value = RolAdi.class, names = {"YONETICI", "ADMIN"})
void selfServiceDisiRoleGecisBekleyenEkipmanTalepleriniIptalEder(RolAdi yeniRol) {
    String talepId = bekleyenEkipmanTalebiOlustur(hedef);
    rolDegistir(admin, hedef, yeniRol).andExpect(status().isNoContent());
    assertEquals("IPTAL_EDILDI", ekipmanTalepDurumu(talepId));
}
```

- [ ] **Step 3: Hedef testleri çalıştır ve talepler pending kaldığı için başarısız olduğunu doğrula**

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: yeni assertion'lar FAIL; mevcut personel davranışları geçer.

- [ ] **Step 4: Ortak pending-talep iptal yardımcı metodunu PersonelService transaction'ına ekle**

```java
private void bekleyenEkipmanTalepleriniIptalEt(
        Personel hedef,
        Personel aktor,
        LocalDateTime islemZamani) {
    List<EkipmanTalep> talepler = ekipmanTalepRepository
            .findPendingByPersonelForUpdate(
                    hedef.getPersonelId(), TalepDurum.ONAY_BEKLIYOR);
    if (talepler.isEmpty()) {
        return;
    }
    DurumTuru iptal = durumTuruRepository
            .findByDurumKodu(TalepDurum.IPTAL_EDILDI)
            .orElseThrow(() -> new KaynakBulunamadiException(
                    "Talep durumu bulunamadı."));
    for (EkipmanTalep talep : talepler) {
        talep.setTalepDurum(iptal);
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(islemZamani);
        talep.setKararNotu(null);
        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Ekipman talebi iptal edildi.",
                hedef);
    }
}
```

Pasifleştirmede her zaman; rol değişikliğinde yalnız eski rol `PERSONEL/HR` ve yeni rol `YONETICI/ADMIN` ise çağır. Açık zimmetlere dokunma.

- [ ] **Step 5: Outer transaction rollback testlerini ekle ve çalıştır**

Personel/rol/audit işleminden sonra enjekte edilen hata bütün personel ve talep değişikliklerini rollback etmelidir.

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; personel, talepler ve audit birlikte commit/rollback olur.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Pasif personelde açık zimmetin korunduğunu ve pending taleplerin hangi aktörle iptal edildiğini raporla.

---

### Task 10: Concurrency, Security ve tam modül doğrulaması

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/EkipmanConcurrencyIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/SecurityHttpIntegrationTest.java`
- Modify: gerekiyorsa `backend/src/main/java/com/jforce/backend/config/SecurityConfig.java`
- Modify: concurrency testlerinin gösterdiği minimum service/repository kodu.

**Interfaces:**
- Consumes: Task 3–9 bütün endpoint ve transaction'ları.
- Produces: gerçek MySQL'de yarış güvenliği ve bütün HTTP rol yüzeyinin regresyon kanıtı.

- [ ] **Step 1: Paralel create ve karar testlerini yaz**

```java
@Test
void paralelAyniOwnerTipCreateIslemlerindenYalnizBiriBasariliOlur()
        throws Exception {
    List<Sonuc> sonuclar = paralelCalistir(
            () -> createTalep(owner, tip),
            () -> createTalep(owner, tip));

    assertEquals(1, basariliSayisi(sonuclar));
    assertEquals(1, conflictSayisi(sonuclar));
    assertEquals(1, pendingTalepSayisi(owner, tip));
}

@Test
void ikiTalepAyniCihaziSecerseYalnizBiriZimmetOlusturur()
        throws Exception {
    List<Sonuc> sonuclar = paralelCalistir(
            () -> approve(talepA, cihaz),
            () -> approve(talepB, cihaz));

    assertEquals(1, basariliSayisi(sonuclar));
    assertEquals(1, conflictSayisi(sonuclar));
    assertEquals(1, acikZimmetSayisi(cihaz));
}
```

- [ ] **Step 2: Approve/reject/cancel, çift iade ve pasifleştirme/approve yarışlarını ekle**

Her yarış iki commit sırasını da event/latch tabanlı deterministik şekilde zorlamalı; arbitrary `sleep` kullanılmamalı. Son durumda tek terminal talep, en fazla tek açık zimmet ve doğru audit sayısı doğrulanmalı.

- [ ] **Step 3: Concurrency testlerini çalıştır ve gerçek kilit açıklarını gözle**

```powershell
.\mvnw.cmd "-Dtest=EkipmanConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

Expected: İlk çalıştırmada eksik lock/exception dönüşümü varsa FAIL; yalnız minimum lock sırası veya constraint dönüşümü düzeltilir.

- [ ] **Step 4: Security route matrisini ekle**

`SecurityHttpIntegrationTest` en az unauthenticated `401`, yanlış rol `403`, doğru rolün controller'a ulaşması ve JWT–DB rol uyumsuzluğunu doğrulamalı. Service `@PreAuthorize` kuralları asıl rol matrisini korumalı; frontend görünürlüğüne güvenilmemeli.

- [ ] **Step 5: İlgili ekipman/security test paketini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=Ekipman*IntegrationTest,EkipmanTalepKararServiceTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS; bütün ekipman ve HTTP security senaryoları geçer.

- [ ] **Step 6: Kullanıcı review checkpoint'i**

Concurrency sonucunu tek kazanan/controlled conflict sayılarıyla, kilit sırasını ve security matrisini raporla.

---

### Task 11: Ana belgeler, regresyon ve JaCoCo teslim kontrolü

**Files:**
- Modify after explicit user approval: `API_SPEC.md`
- Modify after explicit user approval: `SECURITY.md`
- Modify after explicit user approval: `ROADMAP.md`
- Modify after explicit user approval: `PROGRESS.md`
- Inspect: `README.md`, `CONTRIBUTING.md`; yalnız gerçekten gerekli ve kullanıcı tarafından onaylanan değişikliği yap.

**Interfaces:**
- Consumes: doğrulanmış Task 1–10 davranışları.
- Produces: kodla aynı endpoint/rol/hata/migration sözleşmesi ve tam backend doğrulama raporu.

- [x] **Step 1: Belge değişikliği için kullanıcıdan açık onay al**

Onay verilmeden ana Markdown dosyalarını değiştirme. Onay mesajında değişecek dosyaları ve nedenlerini kısa şekilde belirt.

- [x] **Step 2: API_SPEC ve SECURITY'yi uygulanmış davranışla güncelle**

`API_SPEC.md` rol matrisi, request/response örnekleri, filtre/sayfalama, 400/401/403/404/409 ve status transition'ları taşımalı. `SECURITY.md` principal/DB role/direct-report doğrulaması, scoped device lookup, lock/transaction, audit ve migration riskini açıklamalı.

- [x] **Step 3: ROADMAP ve PROGRESS'i gerçek test sonucuna göre güncelle**

Yalnız gerçekten tamamlanan checklist maddelerini işaretle. Direct assignment, bakım, çoklu depo, QR ve type-specific attributes uygulanmış gibi gösterilmemeli; geleceğe bırakılmalıdır.

- [x] **Step 4: CONTRIBUTING tam backend modül kontrol listesini uygula**

Controller/service/repository ayrımı, DTO güvenliği, validation, DB constraints, audit, rollback, positive/negative/security/concurrency/migration testlerini tek tek doğrula.

- [x] **Step 5: Hedef regresyon grubunu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=Ekipman*IntegrationTest,EkipmanTalepKararServiceTest,HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: PASS.

- [x] **Step 6: Tam backend suite ve JaCoCo'yu çalıştır**

```powershell
.\mvnw.cmd test
```

Expected: BUILD SUCCESS, bütün testler `Failures: 0, Errors: 0` ve `All coverage checks have been met.`

- [x] **Step 7: Nihai kullanıcı raporu hazırla**

Rapor şunları içermeli:

- değişen endpointler ve rol matrisi,
- migration dosyası ve production'da kendiliğinden uygulanmadığı uyarısı,
- audit olayları,
- çalıştırılan hedef/tam test komutları ve sayıları,
- çalıştırılamayan kontrol varsa nedeni,
- MVP sonrası bırakılan direct assignment ve diğer genişlemeler,
- frontend agent için request/response/visibility entegrasyon özeti.

- [ ] **Step 8: Kullanıcı review checkpoint'i**

Tamamlanma iddiasını yalnız Step 5 ve Step 6 gerçek çıktıları başarılıysa kullan. Git işlemlerini kullanıcıya bırak.
