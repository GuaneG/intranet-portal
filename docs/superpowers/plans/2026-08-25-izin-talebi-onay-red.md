# İzin Talebi Onay/Red Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Güncel doğrudan Yönetici ve Admin'in izin taleplerini güvenli kuyruklardan listeleyip bekleyen talepleri atomik biçimde onaylayabilmesini veya gerekçeli reddedebilmesini sağlamak.

**Architecture:** Mevcut `IzinTalepService` create ve owner `/my` akışında kalır; yeni `IzinTalepKararService` karar listelerini, kaynak-temelli yetkilendirmeyi, kilitleri, durum geçişini, bakiye doğrulamasını ve aynı-transaction audit'i yönetir. Yönetici ve Admin listeleri ayrı controller yüzeylerinde, onay/red mutation'ları ortak `/api/leave-requests/{talepId}` yüzeyinde sunulur; generic talep motoru eklenmez.

**Tech Stack:** Java 21, Spring Boot 3, Spring MVC, Spring Security method security, Jakarta Validation, Spring Data JPA/Hibernate, MySQL 8, JUnit 5, MockMvc, Testcontainers, Mockito, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-08-25-izin-talebi-onay-red-design.md`

## Global Constraints

- Frontend, ekipman ve rezervasyon modüllerini değiştirme.
- Owner iptali ve bekleyen talep düzenleme bu planın kapsamı değildir.
- Mevcut `POST /api/leave-requests` ve `GET /api/leave-requests/my` yetki davranışını değiştirme.
- Entity'yi response olarak döndürme; ID, kullanıcı adı, e-posta ve telefon gibi gereksiz alanları açma.
- JWT principal ile DB aktörünü yeniden doğrula; kaynak yetkisini service katmanında uygula.
- Karar mutation'ında talep ve owner satır kilitleri, durum/bakiye kontrolü, mutation ve audit aynı transaction içinde olmalıdır.
- MySQL ENUM genişletmesini `ddl-auto=update` davranışına bırakma; idempotent manual migration yaz.
- Bütün hata response'ları kontrollü olmalı; ham exception/SQL/constraint ayrıntısı sızmamalıdır.
- Git komutu çalıştırma veya plana Git/commit adımı ekleme; repository talimatına göre Git işlemlerini yalnız kullanıcı yapar.
- `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md` veya `README.md` değiştirilmeden hemen önce kullanıcıdan açık Markdown onayı alınmalıdır.

## File Structure

### Create

- `backend/db/manual-migrations/2026-08-25-add-leave-request-decisions.sql` — karar notu, audit ENUM ve karar listesi indekslerinin idempotent migration'ı.
- `backend/src/test/java/com/jforce/backend/IzinTalepKararMigrationIntegrationTest.java` — legacy MySQL şemasına karşı migration ve idempotency kanıtı.
- `backend/src/main/java/com/jforce/backend/model/dto/request/IzinTalepRedRequest.java` — istemcinin gönderebildiği tek red alanı.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepKararVerenResponse.java` — güvenli karar veren adı.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepSahibiResponse.java` — karar listesindeki güvenli owner özeti.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepKararItemResponse.java` — Yönetici/Admin karar listesi item'ı.
- `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java` — liste, yetki, durum, bakiye, kilit ve audit use-case'i.
- `backend/src/main/java/com/jforce/backend/controller/IzinTalepKararController.java` — Yönetici listesi ve ortak onay/red endpoint'leri.
- `backend/src/main/java/com/jforce/backend/controller/AdminIzinTalepKararController.java` — Admin şirket geneli listesi.
- `backend/src/test/java/com/jforce/backend/IzinTalepKararControllerIntegrationTest.java` — HTTP/rol/ownership/state/bakiye/audit/regresyon testleri.
- `backend/src/test/java/com/jforce/backend/IzinTalepKararConcurrencyIntegrationTest.java` — aynı talebe paralel karar kanıtı.
- `backend/src/test/java/com/jforce/backend/IzinTalepKararServiceTest.java` — persistence hatasında audit sırası ve güvenli fallback kanıtı.

### Modify

- `backend/src/main/java/com/jforce/backend/model/entity/IzinBilgileri.java` — nullable `kararNotu` alanı.
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java` — onay/red audit tokenları.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java` — owner görünümüne nullable karar alanları.
- `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java` — talep row-lock ve iki karar listesi sorgusu.
- `backend/src/main/java/com/jforce/backend/service/IzinAktoruYetkiService.java` — aktif Yönetici/Admin karar aktörü doğrulaması.
- `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java` — yalnız response mapping'ine karar alanlarının taşınması.
- `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java` — create ve `/my` geriye uyumluluk testleri.
- Kullanıcı Markdown onayı sonrası: `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md`, gerekirse `README.md`.

---

### Task 1: Karar verisi ve MySQL migration temeli

**Files:**
- Create: `backend/db/manual-migrations/2026-08-25-add-leave-request-decisions.sql`
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepKararMigrationIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/IzinBilgileri.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Interfaces:**
- Consumes: mevcut `izin_bilgileri`, `audit_log.eylem_tipi` ve `AuditEylem` sözleşmesi.
- Produces: `IzinBilgileri#getKararNotu()/setKararNotu(String)`, `AuditEylem.IZIN_TALEBI_ONAYLANDI`, `AuditEylem.IZIN_TALEBI_REDDEDILDI` ve uygulanabilir migration.

- [ ] **Step 1: Legacy MySQL fixture'ıyla başarısız migration testini yaz**

Test, yeni tokenları içermeyen gerçek legacy audit ENUM'u, `karar_notu` içermeyen `izin_bilgileri` tablosunu ve bir eski izin/audit satırını kurmalıdır. Aşağıdaki ana oracle'ları kullan:

```java
@Test
void kararMigrationiKolonEnumIndeksVeEskiVeriyiKoruyarakIdempotentCalisir()
        throws Exception {
    Path migration = Path.of(
            System.getProperty("user.dir"),
            "db", "manual-migrations",
            "2026-08-25-add-leave-request-decisions.sql"
    );

    try (Connection connection = baglan()) {
        assertFalse(kolonVarMi(connection, "izin_bilgileri", "karar_notu"));
        assertFalse(auditEnumTokenlari(connection).contains(
                "IZIN_TALEBI_ONAYLANDI"));
        assertFalse(auditEnumTokenlari(connection).contains(
                "IZIN_TALEBI_REDDEDILDI"));

        migrationCalistir(connection, migration);

        assertKararNotuKolonu(connection, "varchar", 500, true);
        assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
        assertIndexKolonlari(connection, "idx_izin_talep_sirasi",
                List.of("talep_tarihi", "izin_id"));
        assertIndexKolonlari(connection, "idx_izin_durum_talep_sirasi",
                List.of("izin_durum_id", "talep_tarihi", "izin_id"));
        assertEquals("eski-not", eskiIzinNotu(connection));
        assertEquals("LOGIN_BASARILI", eskiAuditEylemi(connection));

        yeniAuditTokenlariniInsertEt(connection);
        migrationCalistir(connection, migration);

        assertEquals(beklenenAuditEylemleri(), auditEnumTokenlari(connection));
        assertEquals(1, kolonSayisi(connection, "izin_bilgileri", "karar_notu"));
        assertEquals("eski-not", eskiIzinNotu(connection));
    }
}
```

Legacy audit fixture'ını `AuditEylem.values()` üzerinden kurma; yeni Java tokenları eklendiğinde testin false-positive olmasını önlemek için Task 1 öncesindeki sabit token listesini açıkça yaz.

- [ ] **Step 2: Migration testini çalıştır ve gerçek RED'i doğrula**

Run:

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd "-Dtest=IzinTalepKararMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `CannotReadScript` veya eksik migration dosyasına bağlı test error; production davranışı henüz değiştirilmemiş olmalıdır.

- [ ] **Step 3: Minimum idempotent SQL migration'ı yaz**

Migration şu işlemleri yapmalıdır:

```sql
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
```

Ardından `audit_log.eylem_tipi` mevcut bütün `AuditEylem` tokenları ile birlikte iki yeni tokenı içeren exact ENUM olarak `MODIFY COLUMN` et. Aynı information-schema/prepare desenini iki indeks için kullan:

```sql
CREATE INDEX idx_izin_talep_sirasi
    ON izin_bilgileri (talep_tarihi, izin_id);

CREATE INDEX idx_izin_durum_talep_sirasi
    ON izin_bilgileri (izin_durum_id, talep_tarihi, izin_id);
```

- [ ] **Step 4: Entity ve Java audit enumunu migration'la eşleştir**

`IzinBilgileri` alanı:

```java
@Column(name = "karar_notu", length = 500)
private String kararNotu;

public String getKararNotu() {
    return kararNotu;
}

public void setKararNotu(String kararNotu) {
    this.kararNotu = kararNotu;
}
```

`AuditEylem` sonuna:

```java
IZIN_TALEBI_OLUSTURULDU,
IZIN_TALEBI_ONAYLANDI,
IZIN_TALEBI_REDDEDILDI
```

- [ ] **Step 5: Migration testini GREEN doğrula**

Run: Task 1 Step 2 komutu.

Expected: `Tests run: 1, Failures: 0, Errors: 0`, `BUILD SUCCESS`.

- [ ] **Step 6: Fresh schema derlemesini doğrula**

Run:

```powershell
.\mvnw.cmd "-DskipTests" "-Djacoco.skip=true" test-compile
```

Expected: `BUILD SUCCESS`; entity ve enum derlenir.

---

### Task 2: Owner response'unda karar görünürlüğü

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepKararVerenResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 1 `IzinBilgileri#kararNotu`, mevcut `islemYapanId` ve `islemTarihi`.
- Produces: create ve `/my` response'unda nullable `kararNotu`, `islemTarihi`, `kararVeren`.

- [ ] **Step 1: Pending create ve kararlı `/my` response testlerini RED yaz**

Create response'ta alanların isimleriyle bulunup `null` olduğunu; doğrudan DB fixture'ıyla reddedilmiş ve onaylanmış kayıtta güvenli karar vereni doğrula:

```java
talepOlustur(personel, RolAdi.PERSONEL, gecerliRequestBody())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.kararNotu").value(nullValue()))
        .andExpect(jsonPath("$.islemTarihi").value(nullValue()))
        .andExpect(jsonPath("$.kararVeren").value(nullValue()));

kendiTalepleriniGetir(personel, RolAdi.PERSONEL, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icerik[0].kararNotu")
                .value("Ekip planlaması nedeniyle uygun değil."))
        .andExpect(jsonPath("$.icerik[0].islemTarihi")
                .value("2026-08-25T14:30:00"))
        .andExpect(jsonPath("$.icerik[0].kararVeren.ad").value("Ayşe"))
        .andExpect(jsonPath("$.icerik[0].kararVeren.soyad").value("Yılmaz"))
        .andExpect(jsonPath("$.icerik[0].kararVeren.personelId").doesNotExist())
        .andExpect(jsonPath("$.icerik[0].kararVeren.ePosta").doesNotExist());
```

Onaylanmış fixture için `kararNotu=null`, karar veren ve işlem tarihi dolu olmalıdır. Test öncesi/sonrası izin ve audit snapshot'larının salt okumada değişmediğini de koru.

- [ ] **Step 2: Hedefli testi RED çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#bekleyenCreateResponseKararAlanlariniNullDoner+myKararBilgileriniGuvenliVeNullableDoner" "-Djacoco.skip=true" test
```

Expected: JSON path bulunamadığı veya record constructor eksikliği nedeniyle FAIL.

- [ ] **Step 3: Güvenli karar veren DTO'sunu ve genişletilmiş owner DTO'sunu yaz**

```java
public record IzinTalepKararVerenResponse(
        String ad,
        String soyad
) {
}
```

`IzinTalepResponse` sonuna:

```java
String kararNotu,
LocalDateTime islemTarihi,
IzinTalepKararVerenResponse kararVeren
```

- [ ] **Step 4: Mevcut mapper'ı nullable karar alanlarıyla genişlet**

`IzinTalepService#responseaDonustur` içinde:

```java
Personel kararVeren = izinTalebi.getIslemYapanId();
IzinTalepKararVerenResponse kararVerenResponse = kararVeren == null
        ? null
        : new IzinTalepKararVerenResponse(
                kararVeren.getAdi(),
                kararVeren.getSoyadi()
        );
```

Record constructor'a `izinTalebi.getKararNotu()`, `getIslemTarihi()` ve `kararVerenResponse` ekle. Create akışında bu alanları istemciden alma veya ayrıca set etme.

- [ ] **Step 5: Hedefli ve mevcut owner suite'ini GREEN çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: mevcut create/my testleriyle birlikte tüm sınıf `BUILD SUCCESS`.

---

### Task 3: Yönetici ve Admin karar listeleri

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepSahibiResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepKararItemResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/IzinTalepKararController.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/AdminIzinTalepKararController.java`
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepKararControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinAktoruYetkiService.java`

**Interfaces:**
- Consumes: `SayfaliResponse<T>`, `TalepDurum`, `IzinTalepKararVerenResponse`.
- Produces:
  - `SayfaliResponse<IzinTalepKararItemResponse> takimTalepleriniGetir(String,int,int,TalepDurum)`
  - `SayfaliResponse<IzinTalepKararItemResponse> adminTalepleriniGetir(String,int,int,TalepDurum)`
  - `Personel kararAktoruGetir(String)`

- [ ] **Step 1: Yönetici/Admin liste ve rol matrisini RED yaz**

Test sınıfını `AbstractIntegrationTest` + gerçek MySQL/MockMvc ile kur. En az şu fixture'ları aynı testte ayır: aktör Yönetici, aktif doğrudan çalışan, pasif doğrudan çalışan, dolaylı çalışan, başka ekibin çalışanı, aktörün kendi talebi ve Admin. Temel oracle:

```java
getTeam(yonetici, RolAdi.YONETICI, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icerik.length()").value(4))
        .andExpect(jsonPath("$.icerik[*].talepSahibi.ad",
                everyItem(is("Doğrudan"))))
        .andExpect(jsonPath("$.icerik[0].talepSahibi.personelId")
                .doesNotExist())
        .andExpect(jsonPath("$.icerik[0].talepSahibi.ePosta")
                .doesNotExist());

getAdmin(admin, RolAdi.ADMIN, "")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.icerik[*].talepSahibi.aktifMi",
                hasItems(true, false)));
```

Durum filtresinde aynı durumdaki yabancı/dolaylı/pasif kayıtlar ekleyerek owner predicate ile `durum` AND semantiğini kanıtla. Filtresiz çağrıda dört durumun tamamını; `talepTarihi DESC, izinId DESC` sırasını ve pagination metadata'sını doğrula. Liste öncesi non-empty izin/audit snapshot alıp okuma sonrası exact değişmezliği assert et.

Rol matrisi:

```text
/team: YONETICI 200; PERSONEL/HR/ADMIN 403; token yok 401
/api/admin/leave-requests: ADMIN 200; PERSONEL/HR/YONETICI 403; token yok 401
pasif veya JWT–DB rolü uyuşmayan aktör 403; missing aktör 404
```

- [ ] **Step 2: Liste testlerini gerçek 404 RED ile çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#yoneticiYalnizAktifDogrudanCalisanTalepleriniGorur+adminAktifPasifSirketGeneliTalepleriGorur" "-Djacoco.skip=true" test
```

Expected: iki endpoint de `404`, `BUILD FAILURE`.

- [ ] **Step 3: Karar aktörü doğrulamasını ekle**

`IzinAktoruYetkiService` public metodu:

```java
public Personel kararAktoruGetir(String aktorId) {
    Personel aktor = aktoruGetir(aktorId);
    ortakUygunluguDogrula(aktor);

    RolAdi rol = aktor.getRol().getRolAdi();
    if (rol != RolAdi.YONETICI && rol != RolAdi.ADMIN) {
        throw new AccessDeniedException(
                "Bu işlem için yetkiniz bulunmuyor."
        );
    }
    return aktor;
}
```

Bu metot Yönetici'nin üst yöneticisi olup olmadığına bakmaz; kök Yönetici de doğrudan çalışan taleplerine karar verebilir. Mevcut create doğrulamasını değiştirme.

- [ ] **Step 4: Repository liste sorgularını ekle**

```java
@Query(
        value = """
            SELECT i
            FROM IzinBilgileri i
            JOIN FETCH i.personelId p
            JOIN FETCH p.departman
            JOIN FETCH i.izinTuruId
            JOIN FETCH i.izinDurumId
            LEFT JOIN FETCH i.islemYapanId
            WHERE p.calisiyorMu = true
              AND p.yonetici.personelId = :yoneticiId
              AND (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
            """,
        countQuery = """
            SELECT COUNT(i)
            FROM IzinBilgileri i
            WHERE i.personelId.calisiyorMu = true
              AND i.personelId.yonetici.personelId = :yoneticiId
              AND (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
            """
)
Page<IzinBilgileri> findTakimKararTalepleri(
        @Param("yoneticiId") String yoneticiId,
        @Param("durum") TalepDurum durum,
        Pageable pageable
);

@Query(
        value = """
            SELECT i
            FROM IzinBilgileri i
            JOIN FETCH i.personelId p
            JOIN FETCH p.departman
            JOIN FETCH i.izinTuruId
            JOIN FETCH i.izinDurumId
            LEFT JOIN FETCH i.islemYapanId
            WHERE (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
            """,
        countQuery = """
            SELECT COUNT(i)
            FROM IzinBilgileri i
            WHERE (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
            """
)
Page<IzinBilgileri> findAdminKararTalepleri(
        @Param("durum") TalepDurum durum,
        Pageable pageable
);
```

- [ ] **Step 5: Liste DTO'larını ve minimum service mapping'ini yaz**

```java
public record IzinTalepSahibiResponse(
        String ad,
        String soyad,
        String departman,
        String profilFoto,
        boolean aktifMi
) {
}
```

`IzinTalepKararItemResponse` tasarım belgesindeki flat talep alanlarını, `IzinTalepSahibiResponse talepSahibi` ve nullable `IzinTalepKararVerenResponse kararVeren` alanını exact sırayla taşımalıdır.

`IzinTalepKararService` liste metotları `@Transactional(readOnly = true)` ve exact method security ile çalışsın:

```java
@PreAuthorize("hasRole('YONETICI')")
public SayfaliResponse<IzinTalepKararItemResponse> takimTalepleriniGetir(
        String aktorId, int page, int size, TalepDurum durum)

@PreAuthorize("hasRole('ADMIN')")
public SayfaliResponse<IzinTalepKararItemResponse> adminTalepleriniGetir(
        String aktorId, int page, int size, TalepDurum durum)
```

İki metot `kararAktoruGetir`, `PageRequest.of(page,size,sort)` ve ortak private mapper kullanmalıdır. Sort:

```java
Sort.by(Sort.Order.desc("talepTarihi"))
        .and(Sort.by(Sort.Order.desc("izinId")))
```

- [ ] **Step 6: İnce controller'ları yaz**

`IzinTalepKararController`:

```java
@Validated
@RestController
@RequestMapping("/api/leave-requests")
class IzinTalepKararController {
    @GetMapping("/team")
    ResponseEntity<SayfaliResponse<IzinTalepKararItemResponse>>
    takimTalepleriniGetir(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) TalepDurum durum,
            Authentication authentication)
}
```

`AdminIzinTalepKararController` aynı parametrelerle `@RequestMapping("/api/admin/leave-requests")` ve `@GetMapping` kullanır. Controller'lar rol/ownership hesaplamaz.

- [ ] **Step 7: Liste suite'ini GREEN çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: liste, filtre, pagination, rol ve read-only testleri GREEN.

---

### Task 4: Bekleyen talebi onaylama

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/IzinTalepKararController.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepKararControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 karar aktörü/list service'i, `PersonelRepository#findByIdForUpdate`, `DurumTuruRepository#findByDurumKodu`, mevcut bakiye toplam sorgusu.
- Produces: `void talebiOnayla(String aktorId, String talepId)` ve `PATCH /api/leave-requests/{talepId}/approve`.

- [ ] **Step 1: Onay happy-path, yetki, durum, bakiye ve audit testlerini RED yaz**

En az şu gerçek HTTP/MySQL senaryolarını ekle:

```text
- Güncel aktif doğrudan Yönetici -> 204; durum ONAYLANDI
- Admin -> aktif ve pasif owner talebi 204
- başka/eski/dolaylı Yönetici -> 403, talep/audit değişmez
- PERSONEL/HR -> 403; token yok -> 401
- pasif aktör/JWT–DB rol mismatch -> 403; missing aktör -> 404
- malformed talep UUID -> 400 Geçersiz istek
- valid missing UUID -> 404 İzin talebi bulunamadı.
- ONAYLANDI/REDDEDILDI/IPTAL_EDILDI -> 409 Talep artık işlem beklemiyor.
- güncel hak azaltılmış annual owner -> 409 İzin bakiyesi artık yeterli değil.
- nonannual izin -> bakiye düşük olsa da 204
```

Başarılı onayda exact DB ve audit oracle:

```java
assertEquals(TalepDurum.ONAYLANDI,
        yenilenen.getIzinDurumId().getDurumKodu());
assertEquals(yonetici.getPersonelId(),
        yenilenen.getIslemYapanId().getPersonelId());
assertEquals(SABIT_ISLEM_TARIHI, yenilenen.getIslemTarihi());
assertNull(yenilenen.getKararNotu());

assertEquals(AuditEylem.IZIN_TALEBI_ONAYLANDI, audit.getEylemTipi());
assertEquals(yonetici.getPersonelId(), audit.getPersonel().getPersonelId());
assertEquals(owner.getPersonelId(), audit.getHedefPersonel().getPersonelId());
assertEquals("İzin talebi onaylandı.", audit.getDetay());
```

Karar yetkisini durum kontrolünden önce kanıtlamak için başka Yönetici'nin zaten onaylanmış talebe çağrısında `403` bekle; `409` dönmemelidir.

- [ ] **Step 2: Onay endpoint'ini RED çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#yoneticiAktifDogrudanCalisanTalebiniOnaylar" "-Djacoco.skip=true" test
```

Expected: endpoint eksikliği nedeniyle `404`.

- [ ] **Step 3: Talep row-lock repository metodunu ekle**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT i FROM IzinBilgileri i WHERE i.izinId = :izinId")
Optional<IzinBilgileri> findByIdForUpdate(@Param("izinId") String izinId);
```

- [ ] **Step 4: Minimum transactional onay akışını yaz**

```java
@Transactional
@PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
public void talebiOnayla(String aktorId, String talepId) {
    Personel aktor = izinAktoruYetkiService.kararAktoruGetir(aktorId);
    String normalizeTalepId = talepIdDogrula(talepId);
    IzinBilgileri talep = izinBilgileriRepository
            .findByIdForUpdate(normalizeTalepId)
            .orElseThrow(() -> new KaynakBulunamadiException(
                    "İzin talebi bulunamadı."));
    Personel owner = personelRepository
            .findByIdForUpdate(talep.getPersonelId().getPersonelId())
            .orElseThrow(() -> new KaynakBulunamadiException(
                    "Personel bulunamadı."));

    kararYetkisiniDogrula(aktor, owner);
    bekleyenDurumuDogrula(talep);
    onayBakiyesiniDogrula(owner, talep);

    DurumTuru onaylandi = durumTuruRepository
            .findByDurumKodu(TalepDurum.ONAYLANDI)
            .orElseThrow(() -> new KaynakBulunamadiException(
                    "İzin durumu bulunamadı."));
    talep.setIzinDurumId(onaylandi);
    talep.setIslemYapanId(aktor);
    talep.setIslemTarihi(LocalDateTime.now(clock));
    talep.setKararNotu(null);
    izinBilgileriRepository.saveAndFlush(talep);
    auditService.logKaydetOrtakTransaction(
            AuditEylem.IZIN_TALEBI_ONAYLANDI,
            aktor,
            aktor.getKullaniciAdi(),
            "İzin talebi onaylandı.",
            owner
    );
}
```

Helper sözleşmeleri:

```java
private String talepIdDogrula(String talepId) // canonical UUID, aksi 400
private void kararYetkisiniDogrula(Personel aktor, Personel owner)
private void bekleyenDurumuDogrula(IzinBilgileri talep)
private void onayBakiyesiniDogrula(Personel owner, IzinBilgileri talep)
```

`kararYetkisiniDogrula`: Admin'e owner aktiflik/manager ilişkisi şartı koymaz; Yönetici için owner aktif olmalı ve `owner.getYonetici().getPersonelId()` aktör ID'sine eşit olmalıdır. `bekleyenDurumuDogrula` exact `KaynakCakismasiException("Talep artık işlem beklemiyor.")` atar.

Annual bakiye helper'ı `ONAYLANDI + ONAY_BEKLIYOR` toplamını owner'ın kilitli güncel `yillikIzinHakki` değeriyle karşılaştırır; mevcut talep pending toplama dahildir. Aşımda `KaynakCakismasiException("İzin bakiyesi artık yeterli değil.")`; nonannual türde return.

- [ ] **Step 5: İnce onay endpoint'ini ekle**

```java
@PatchMapping("/{talepId}/approve")
public ResponseEntity<Void> talebiOnayla(
        @PathVariable String talepId,
        Authentication authentication
) {
    izinTalepKararService.talebiOnayla(
            authentication.getName(), talepId);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 6: Onay testlerini GREEN çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: liste + onay senaryolarının tamamı GREEN.

- [ ] **Step 7: Dış transaction rollback testini ekle ve doğrula**

Test-only ayrı Spring bean'de `@Transactional` metot, gerçek `talebiOnayla` çağrısından sonra `IllegalStateException("rollback-probe")` atsın. Transaction içindeyken durum/audit sayısının değiştiğini, çağrı sonrasında talebin tekrar pending ve audit snapshot'ın değişmemiş olduğunu doğrula.

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#disTransactionHatasindaOnayVeAuditBirlikteRollbackOlur" "-Djacoco.skip=true" test
```

Expected: `Tests run: 1`, `BUILD SUCCESS`.

---

### Task 5: Gerekçeli red akışı

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/IzinTalepRedRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/IzinTalepKararController.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepKararControllerIntegrationTest.java`

**Interfaces:**
- Consumes: Task 4 actor/lock/permission/state helper'ları.
- Produces: `void talebiReddet(String aktorId, String talepId, IzinTalepRedRequest request)` ve `PATCH /api/leave-requests/{talepId}/reject`.

- [ ] **Step 1: Red happy-path ve validation testlerini RED yaz**

Testler şunları exact doğrulasın:

```text
- Yönetici doğrudan aktif owner talebini 204 reddeder.
- Admin pasif owner talebini 204 reddeder.
- "  Ekip planlaması uygun değil.  " DB'de trim edilmiş saklanır.
- body yok/malformed JSON/null/boş/yalnız whitespace/trim sonrası 501 karakter -> 400 Geçersiz istek.
- 500 karakter -> 204.
- Düşük annual bakiye redde engel değildir.
- Aynı authorization/state/error matrisi onayla korunur.
- Başarısız red talep/audit yan etkisi bırakmaz.
```

Audit exact:

```java
assertEquals(AuditEylem.IZIN_TALEBI_REDDEDILDI, audit.getEylemTipi());
assertEquals("İzin talebi reddedildi.", audit.getDetay());
assertFalse(audit.getDetay().contains("Ekip planlaması"));
```

- [ ] **Step 2: Red endpoint'ini 404 RED çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#yoneticiTalebiTrimEdilmisGerekceyleReddeder" "-Djacoco.skip=true" test
```

Expected: `404`, `BUILD FAILURE`.

- [ ] **Step 3: Request DTO ve service validation'ını yaz**

```java
public record IzinTalepRedRequest(
        @NotNull String kararNotu
) {
}
```

Service'te trim sonrası exact iş kuralı:

```java
private String kararNotunuNormalizeEt(IzinTalepRedRequest request) {
    String kararNotu = request.kararNotu().trim();
    if (kararNotu.isEmpty() || kararNotu.length() > 500) {
        throw new GecersizIstekException("Geçersiz istek");
    }
    return kararNotu;
}
```

`talebiReddet` aktör → UUID → talep lock → owner lock → permission → pending durum sırasını Task 4 ile aynı kullanır; bakiye kontrolü yapmaz. Durumu `REDDEDILDI`, işlem yapan/zamanı ve normalize `kararNotu` alanını set eder; `saveAndFlush` sonrasında same-transaction red audit'i yazar.

- [ ] **Step 4: İnce red endpoint'ini ekle**

```java
@PatchMapping("/{talepId}/reject")
public ResponseEntity<Void> talebiReddet(
        @PathVariable String talepId,
        @Valid @RequestBody IzinTalepRedRequest request,
        Authentication authentication
) {
    izinTalepKararService.talebiReddet(
            authentication.getName(), talepId, request);
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 5: Red testlerini GREEN çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: liste + onay + red senaryoları GREEN.

- [ ] **Step 6: Owner `/my` gerçek karar regresyonunu doğrula**

Önce HTTP ile red kararı ver, ardından owner tokenıyla `/my` çağır. `kararNotu`, sabit `islemTarihi`, güvenli `kararVeren` ve `REDDEDILDI` durumunu doğrula; karar verenin ID/e-posta/kullanıcı adının olmadığını assert et.

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#ownerMyReddedilenTalebinGuvenliKararBilgileriniGorur" "-Djacoco.skip=true" test
```

Expected: `BUILD SUCCESS`.

---

### Task 6: Paralel kararların seri hale getirilmesi

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepKararConcurrencyIntegrationTest.java`
- Modify only if the test exposes a real defect: `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java`, `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`

**Interfaces:**
- Consumes: `IzinBilgileriRepository#findByIdForUpdate`, Task 4/5 mutation metotları.
- Produces: aynı pending talebe paralel onay/redde tam bir başarı ve tam bir controlled `409` kanıtı.

- [ ] **Step 1: Deterministik gerçek MySQL concurrency testini yaz**

`@MockitoSpyBean IzinBilgileriRepository` ile Spring Data proxy'nin orijinal `defaultAnswer` delegate'ini sakla. İlk `findByIdForUpdate(talepId)` gerçek DB çağrısı döndükten sonra ilk transaction'ı latch ile tut; ikinci istek gerçek lock çağrısına girsin. Ana test thread'i MySQL `performance_schema.data_lock_waits` ve `data_locks` üzerinden:

```sql
requesting.OBJECT_NAME = 'izin_bilgileri'
AND requesting.INDEX_NAME = 'PRIMARY'
AND requesting.LOCK_STATUS = 'WAITING'
AND blocking.LOCK_STATUS = 'GRANTED'
AND requesting.LOCK_DATA = blocking.LOCK_DATA
AND requesting.LOCK_DATA LIKE CONCAT('%', ?, '%')
```

koşullarıyla aynı `talepId` row wait'ini görmeden ilk transaction'ı bırakma. Her worker kendi `SecurityContext`'ini kurup `finally` içinde temizlesin.

Son oracle:

```java
assertEquals(1, sonuclar.stream().filter(Sonuc::basarili).count());
assertEquals(1, sonuclar.stream().filter(Sonuc::conflict).count());
assertEquals(1L, auditLogRepository.count());
IzinBilgileri sonuc = izinBilgileriRepository.findById(talepId).orElseThrow();
assertTrue(Set.of(TalepDurum.ONAYLANDI, TalepDurum.REDDEDILDI)
        .contains(sonuc.getIzinDurumId().getDurumKodu()));
```

- [ ] **Step 2: Concurrency testini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

Expected with correct Task 4 lock: `Tests run: 1`, bir `204` eşdeğeri başarı ve bir `Talep artık işlem beklemiyor.` conflict, `BUILD SUCCESS`.

- [ ] **Step 3: Yalnız gerçek defect varsa minimum production fix uygula**

Test iki başarı veya iki audit gösterirse `talebiOnayla/talebiReddet` içinde durum kontrolünün `findByIdForUpdate` sonrasında olduğundan ve transaction proxy üzerinden çağrıldığından emin ol. Lock'u kaldırma, optimistic retry ekleme veya global isolation değiştirme. Test ilk çalışmada geçerse production'a dokunma.

- [ ] **Step 4: Concurrency + karar HTTP paketini birlikte doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararConcurrencyIntegrationTest,IzinTalepKararControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: iki sınıfın tamamı GREEN.

---

### Task 7: Persistence hata sırası ve güvenli 500 regresyonu

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepKararServiceTest.java`
- Modify only if required by a failing real test: `backend/src/main/java/com/jforce/backend/service/IzinTalepKararService.java`
- Modify only if existing generic behavior is broken: `backend/src/test/java/com/jforce/backend/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: Task 4/5 `saveAndFlush` → audit sırası ve mevcut generic `GlobalExceptionHandler` fallback'i.
- Produces: persistence hatasında audit çağrılmadığı ve HTTP'nin güvenli generic 500 döndürdüğü kanıt.

- [ ] **Step 1: Service unit testinde exact persistence instance ve audit-never oracle'ı yaz**

Mock'ları geçerli aktör/talep/owner/durum akışını geçecek şekilde kur; `saveAndFlush(talep)` çağrısında aynı `DataIntegrityViolationException persistenceHatasi` nesnesini fırlat:

```java
DataIntegrityViolationException persistenceHatasi =
        new DataIntegrityViolationException("constraint-secret");
when(izinBilgileriRepository.saveAndFlush(talep))
        .thenThrow(persistenceHatasi);

DataIntegrityViolationException firlatilan = assertThrows(
        DataIntegrityViolationException.class,
        () -> service.talebiOnayla(aktorId, talepId)
);

assertSame(persistenceHatasi, firlatilan);
verifyNoInteractions(auditService);
```

Red için de aynı save-before-audit sırasını tek parametrik ya da ikinci açık testle doğrula.

- [ ] **Step 2: Service unit testini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararServiceTest" "-Djacoco.skip=true" test
```

Expected: minimum implementasyon doğru sıradaysa GREEN; değilse audit'i yalnız başarılı `saveAndFlush` sonrasına taşı.

- [ ] **Step 3: HTTP generic 500 güvenlik testini ekle**

Test profile içinde yalnız karar service'inin `talebiOnayla` çağrısında `RuntimeException("sql-secret")` atan bir `@Primary` test double kullan. MockMvc oracle:

```java
patchApprove(yonetici, talepId)
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message")
                .value("Beklenmeyen bir hata oluştu."))
        .andExpect(content().string(not(containsString("sql-secret"))));
```

Test fixture talep ve audit snapshot'ını before/after exact eşitlikle doğrulasın.

- [ ] **Step 4: Güvenli hata testini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararControllerIntegrationTest#beklenmeyenKararHatasiGuvenli500DonerVeYanEtkisizdir,GlobalExceptionHandlerTest" "-Djacoco.skip=true" test
```

Expected: generic handler değiştirilmeden GREEN. Ham mesaj sızarsa yalnız mevcut generic fallback'i dar biçimde düzelt ve bütün handler testlerini çalıştır.

---

### Task 8: Dokümantasyon, regresyon ve tam doğrulama

**Files:**
- Modify after explicit user approval: `API_SPEC.md`
- Modify after explicit user approval: `SECURITY.md`
- Modify after explicit user approval: `ROADMAP.md`
- Modify after explicit user approval: `PROGRESS.md`
- Modify only if overview materially changes and after approval: `README.md`
- Verify: all files changed by Tasks 1–7

**Interfaces:**
- Consumes: bütün uygulanmış endpoint/rol/error/migration/test sözleşmesi.
- Produces: kodla uyumlu ürün/güvenlik belgeleri ve full-suite/coverage kanıtı.

- [ ] **Step 1: Ürün Markdown dosyaları için kullanıcıdan açık onay iste**

Onay alınmadan bu beş dosyaya hiçbir patch uygulama. Onay verilmezse dokümantasyon değişikliğini atla ve final raporda açık kalan belge güncellemesi olarak belirt.

- [ ] **Step 2: Onay sonrası `API_SPEC.md` sözleşmesini güncelle**

Exact olarak kaydet:

```text
GET /api/leave-requests/team?page=&size=&durum?
GET /api/admin/leave-requests?page=&size=&durum?
PATCH /api/leave-requests/{talepId}/approve
PATCH /api/leave-requests/{talepId}/reject
```

Rol matrisi, default “Hepsi”, DTO alanları, nullable `/my` karar alanları, `204`, exact `400/401/403/404/409/500` koşul ve mesajları eklenmelidir. Owner cancel satırı planlanan olarak kalmalıdır.

- [ ] **Step 3: `SECURITY.md` ve roadmap/progress kayıtlarını güncelle**

`SECURITY.md`: JWT–DB rol doğrulaması, güncel manager ilişkisi, Admin kapsamı, request+owner pessimistic lock sırası, approval-time balance, same-transaction audit, ENUM migration ve pasif owner farkını açıkla.

`ROADMAP.md`: onay/red ve iki audit maddesini tamamlandı işaretle; owner iptalini açık bırak. Generic workflow motorunun yapılmadığını, ekipman kararında gerçek tekrar ortaya çıkınca değerlendirileceğini yaz.

`PROGRESS.md`: tarih, endpointler, transaction/audit/migration/test sonuçlarını yalnız doğrulanan gerçek sayılarla ekle. `README.md` özet satırını “create/my/onay/red backend hazır; owner iptali ve gerekiyorsa frontend entegrasyonu açık” biçiminde düzelt.

- [ ] **Step 4: Odaklı backend regresyon paketini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepKararMigrationIntegrationTest,IzinTalepKararControllerIntegrationTest,IzinTalepKararConcurrencyIntegrationTest,IzinTalepKararServiceTest,IzinTalepControllerIntegrationTest,IzinHesaplamaControllerIntegrationTest,ProfilControllerIntegrationTest,SecurityHttpIntegrationTest,GlobalExceptionHandlerTest" "-Djacoco.skip=true" test
```

Expected: tüm odaklı sınıflar `0 failure, 0 error`, `BUILD SUCCESS`.

- [ ] **Step 5: Tam backend suite ve JaCoCo doğrulamasını çalıştır**

Run:

```powershell
.\mvnw.cmd test
```

Expected: `BUILD SUCCESS`, `Failures: 0`, `Errors: 0`, `All coverage checks have been met.` Testcontainers/Docker sandbox erişimi engellenirse aynı exact komut için masaüstü onay penceresiyle escalated execution iste; komutu veya kapsamı sessizce değiştirme.

- [ ] **Step 6: UTF-8, placeholder ve sözleşme tutarlılığı kontrolü yap**

Değişen Java/SQL/Markdown dosyalarını strict UTF-8 olarak oku; BOM olmadığını doğrula. Plan/spec/ürün belgelerinde şu stale ifadeleri ara ve düzelt:

```powershell
rg -n "onay/red.*bekliyor|IZIN_TALEBI_ONAYLANDI.*plan|IZIN_TALEBI_REDDEDILDI.*plan|TBD|TODO" API_SPEC.md SECURITY.md ROADMAP.md PROGRESS.md README.md
```

Owner iptalinin hâlâ planlanan olduğu ifadeyi silme.

- [ ] **Step 7: Son teslim raporunu hazırla**

Raporda şunları sayısal ve somut ver:

```text
- Değişen/yeni endpointler
- Yönetici/Admin rol ve hedef matrisi
- State transition ve exact hata kodları
- karar_notu + audit ENUM migration dosyası ve uygulama sırası
- Audit actor/target/detail sözleşmesi
- Çalıştırılan hedefli ve full test komutları; exact test/failure/error sayıları
- Çalıştırılmayan kontroller ve kalan riskler
- Frontend için default Hepsi, filtre enumu, red body, 204 sonrası refetch ve nullable karar alanları
```

## Plan Self-Review

- Spec coverage: onaylanan tasarımın endpoint, rol, owner scope, pasif owner, default filtre, state, bakiye, audit, migration, concurrency, owner response, error ve test maddelerinin her biri Task 1–8'e bağlandı.
- Placeholder scan: plan uygulama adımlarında `TBD`, `TODO`, “uygun hata ekle” veya tanımsız “benzeri” adım bırakılmadı. Task 8'deki `rg` içindeki `TBD|TODO` yalnız taranacak literal desenlerdir.
- Type consistency: `IzinTalepKararService`, `IzinTalepRedRequest`, `IzinTalepKararItemResponse`, `IzinTalepKararVerenResponse`, `IzinTalepSahibiResponse` ve dört endpoint adı bütün görevlerde aynı kullanıldı.
- Scope check: tek alt sistem olan izin karar akışı ele alındı; owner iptali, ekipman, rezervasyon, frontend ve generic motor kapsam dışında bırakıldı.
- Repository rule: Git/commit adımları bilinçli biçimde eklenmedi.
