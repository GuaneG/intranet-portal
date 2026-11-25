# İzin Talebi Oluşturma ve Kendi Talepleri Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif ve uygun çalışanların izin türlerini görmesini, güvenilir iş günü önizlemesi almasını, atomik biçimde izin talebi oluşturmasını ve yalnız kendi taleplerini sayfalı listelemesini sağlamak.

**Architecture:** Controller'lar yalnız HTTP sözleşmesini yönetir. Ortak `IzinAktoruYetkiService` JWT–DB rol uyumunu, aktifliği ve Yönetici uygunluğunu doğrular. `IzinTalepService`, personel satırını pessimistic lock ile kilitleyerek tarih, iş günü, etkili çakışma, yıllık izin rezervasyonu, persistence ve audit işlemlerini tek transaction'da yürütür. Repository sorguları ownership'i ve durum kapsamını DB seviyesinde uygular; entity hiçbir response'a doğrudan çıkmaz.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Spring Security Method Security, Jakarta Validation, MySQL 8, Testcontainers, JUnit 5, Mockito, Maven, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-08-24-izin-talebi-olusturma-ve-kendi-talepleri-design.md`

## Global Constraints

- Frontend dosyalarına dokunma.
- Yeni dependency ekleme.
- Git komutu çalıştırma; commit adımları bilinçli olarak bu planda yoktur.
- Mevcut `GET /api/leave-requests/workday-preview` yolunu koru; yalnız onaylı rol davranışına genişlet.
- Aktör yalnız authenticated principal'dan belirlenir; request'ten personel, rol, durum, iş günü, timestamp veya audit alanı alınmaz.
- Entity'leri response olarak döndürme; parola, token, iletişim alanı veya gereksiz ID sızdırma.
- İş kuralları controller'da değil service/policy katmanında uygulanır.
- Talep, bakiye/çakışma kontrolleri ve audit aynı transaction'da tamamlanır.
- `Personel.yillikIzinHakki` bu teslimde geçici hak kaynağıdır; `ProfilResponse.kalanIzin` anlamı değiştirilmez.
- Aynı kişinin paralel create istekleri `Personel` satırı üzerinde `PESSIMISTIC_WRITE` ile seri hale getirilir.
- Hibernate `ddl-auto=update`, mevcut MySQL ENUM migration'ı yerine kabul edilmez.
- Ürün belgeleri yalnız davranış ve testler doğrulandıktan sonra güncellenir.
- Docker/Testcontainers erişimi engellenirse gerçek davranış RED/GREEN kanıtı kullanıcı ortamında alınmadan sonraki TDD aşamasına geçilmez.

---

## File Map

**Create**

- `backend/db/manual-migrations/2026-08-24-add-leave-request-foundation.sql` — durum/izin türü seed'leri, audit ENUM genişlemesi ve sorgu indeksleri.
- `backend/src/main/java/com/jforce/backend/repository/IzinTuruRepository.java` — alfabetik lookup ve ID doğrulaması.
- `backend/src/main/java/com/jforce/backend/repository/DurumTuruRepository.java` — `TalepDurum` koduyla durum satırı bulma.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTuruLookupResponse.java` — güvenli izin türü lookup DTO'su.
- `backend/src/main/java/com/jforce/backend/controller/IzinTuruController.java` — `GET /api/leave-types` HTTP sınırı.
- `backend/src/main/java/com/jforce/backend/controller/IzinTalepController.java` — create ve `/my` HTTP sınırı.
- `backend/src/main/java/com/jforce/backend/service/IzinAktoruYetkiService.java` — ortak aktiflik, JWT–DB rol uyumu ve Yönetici uygunluğu.
- `backend/src/main/java/com/jforce/backend/service/IzinTuruService.java` — read-only izin türü lookup.
- `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java` — create, validation, rezervasyon, çakışma, sayfalama ve audit use-case'i.
- `backend/src/test/java/com/jforce/backend/IzinTalepFoundationMigrationIntegrationTest.java` — eski MySQL şemasından kalıcı migration doğrulaması.
- `backend/src/test/java/com/jforce/backend/IzinTuruControllerIntegrationTest.java` — lookup sözleşmesi ve yetki matrisi.
- `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java` — create, `/my`, validation, audit ve atomiklik.
- `backend/src/test/java/com/jforce/backend/IzinTalepConcurrencyIntegrationTest.java` — aynı-personel paralel create güvenliği.
- `backend/src/test/java/com/jforce/backend/IzinTalepServiceTest.java` — persistence fallback ve transaction öncesi iş kuralları için odaklı unit testler.

**Modify**

- `backend/src/main/java/com/jforce/backend/model/dto/request/IzinTalepCreateRequest.java` — pozitif ID ve 1000 karakter not sözleşmesi.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java` — `durumAdi` ve `talepTarihi` dahil tam güvenli sözleşme.
- `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java` — personel satırını pessimistic-write kilitleyen lookup.
- `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java` — owner page, toplamlar ve overlap adayları.
- `backend/src/main/java/com/jforce/backend/service/IzinOnizlemeService.java` — ortak aktör yetkisi ve uygun Yönetici desteği.
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java` — yalnız `IZIN_TALEBI_OLUSTURULDU`.
- `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java` — preview rol matrisi regresyonu.
- `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java` — pending rezervasyonun mevcut `kalanIzin` alanını değiştirmediği regresyonu.
- `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md` — implementation ve testlerden sonra kesin sözleşme/durum.
- Kök `README.md` ve `frontend/README.md` — daha önce tamamlanan doğum günü frontend entegrasyonunu hâlâ bekliyor gösteren satırlar; yalnız doğrulanmış mevcut duruma göre düzeltilecek.

---

### Task 1: Eski MySQL şemasını izin talebi temel verileriyle güvenli biçimde yükselt

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/IzinTalepFoundationMigrationIntegrationTest.java`
- Create: `backend/db/manual-migrations/2026-08-24-add-leave-request-foundation.sql`

**Interfaces:**

- Produces four `durum_turu` rows keyed by `TalepDurum` names.
- Produces three `izin_turu` rows keyed by exact names and deduction flags.
- Extends `audit_log.eylem_tipi` with `IZIN_TALEBI_OLUSTURULDU` while retaining every existing `AuditEylem` value.
- Produces `idx_izin_personel_durum_tarih` and `idx_izin_personel_talep_sirasi` if absent.

- [ ] **Step 1: Missing migration için gerçek MySQL RED testi yaz**

Test kendi MySQL 8 container'ında eski tabloları oluşturmalı, mevcut seed ID'lerini ve bir audit satırını eklemeli, sonra henüz olmayan script'i çalıştırmalıdır:

```java
Path migration = Path.of(
        System.getProperty("user.dir"),
        "db", "manual-migrations",
        "2026-08-24-add-leave-request-foundation.sql"
);

migrationCalistir(connection, migration);
```

Assertions şunları kapsamalıdır:

```text
TalepDurum enumundaki 4 kodun tamamı ve yalnız birer satır
Yıllık İzin=true, Hastalık İzni=false, Mazeret İzni=false
önceden var olan durum/izin türü ID'lerinin korunması
audit ENUM içinde mevcut AuditEylem değerlerinin tamamı + IZIN_TALEBI_OLUSTURULDU
iki indeksin doğru kolon sırasıyla varlığı
script ikinci kez çalışınca satır/ID/veri değişmemesi
```

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd "-Dtest=IzinTalepFoundationMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `CannotReadScript`; migration dosyası bulunamadığı için test error verir.

- [ ] **Step 3: İdempotent migration script'ini yaz**

Script şu kesin kuralları uygulamalıdır:

```sql
ALTER TABLE durum_turu
    MODIFY COLUMN durum_adi
    ENUM('ONAY_BEKLIYOR','ONAYLANDI','REDDEDILDI','IPTAL_EDILDI')
    NOT NULL;

INSERT INTO durum_turu (durum_adi)
SELECT 'ONAY_BEKLIYOR'
WHERE NOT EXISTS (
    SELECT 1 FROM durum_turu WHERE durum_adi = 'ONAY_BEKLIYOR'
);
```

Aynı `INSERT ... SELECT ... WHERE NOT EXISTS` yaklaşımı diğer durumlar ve üç izin türü için kullanılmalıdır. Audit kolonunun `ALTER TABLE ... MODIFY COLUMN ... ENUM(...)` ifadesi Java `AuditEylem` değerlerinin tamamını açıkça taşımalı; eski değer düşürülmemelidir. İndeksler `information_schema.statistics` kontrolü ve dynamic SQL ile yalnız yoksa eklenmelidir:

```text
idx_izin_personel_durum_tarih(personel_id, izin_durum_id, baslangic_tarihi, bitis_tarihi)
idx_izin_personel_talep_sirasi(personel_id, talep_tarihi, izin_id)
```

- [ ] **Step 4: Migration'ın ikinci çalışma ve veri koruma assertions'ını tamamla**

İlk çalışmadan sonra bir seed satırının ID'si okunmalı, script tekrar çalıştırılmalı ve aynı ID ile tek satır kaldığı doğrulanmalıdır. Var olan audit satırı okunabilir kalmalı; yeni audit enum değeriyle insert başarılı olmalıdır.

- [ ] **Step 5: Migration testini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepFoundationMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: migration iki kez çalışır, `0 failure, 0 error`, `BUILD SUCCESS`.

---

### Task 2: Ortak izin aktörü yetkisini kur ve preview rol sözleşmesini genişlet

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/service/IzinAktoruYetkiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinOnizlemeService.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java`

**Interfaces:**

```java
public Personel talepAktoruGetir(String aktorId);
public Personel lookupAktoruGetir(String aktorId);
public void talepIslemineUygunluguDogrula(Personel aktor);
public void lookupUygunlugunuDogrula(Personel aktor);
```

- [ ] **Step 1: Uygun Yönetici preview için RED HTTP testleri ekle**

Testler şu matrisi açıkça doğrulamalıdır:

```text
aktif PERSONEL 200
aktif HR 200
aktif YONETICI + aktif YONETICI üst yönetici 200
kök YONETICI 403
pasif/yanlış rollü üst yöneticisi olan YONETICI 403
ADMIN 403
pasif aktör 403
missing principal 404
JWT authority ile DB rolü farklı 403
```

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinHesaplamaControllerIntegrationTest#aktifVeUygunYoneticiIsGunuOnizler+kokYoneticiIsGunuOnizleyemez" "-Djacoco.skip=true" test
```

Expected: mevcut `@PreAuthorize` Yönetici'yi reddettiği için uygun Yönetici testi `403` alır.

- [ ] **Step 3: Ortak policy bileşenini minimal uygula**

`IzinAktoruYetkiService` private ortak kontrolde:

```java
if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
    throw new AccessDeniedException("Pasif personel izin işlemi yapamaz.");
}

RolAdi jwtRolu = securityContexttekiTekUygulamaRolunuGetir();
RolAdi dbRolu = aktor.getRol().getRolAdi();
if (jwtRolu != dbRolu) {
    throw new AccessDeniedException("JWT ve personel rolü uyuşmuyor.");
}
```

Talep/preview/list uygunluğu:

```java
PERSONEL -> izin ver
HR -> izin ver
YONETICI -> aktor.yonetici mevcut, aktif ve rolü YONETICI ise izin ver
ADMIN -> reddet
```

Lookup uygunluğu aynı kontrole ek olarak aktif `ADMIN` için izin verir. `talepAktoruGetir` ve `lookupAktoruGetir`, missing DB personelini `404 Personel bulunamadı.` ile döndürür.

- [ ] **Step 4: Preview service'i ortak bileşene geçir**

`IzinOnizlemeService` artık doğrudan `PersonelRepository` veya rol switch'i taşımamalıdır:

```java
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR')")
public IsGunuOnizlemeResponse isGunuOnizle(...) {
    izinAktoruYetkiService.talepAktoruGetir(aktorId);
    return new IsGunuOnizlemeResponse(
            isGunuHesaplamaService.isGunuSayisiniHesapla(
                    baslangicTarihi,
                    bitisTarihi
            )
    );
}
```

- [ ] **Step 5: Preview rol ve validation paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinHesaplamaControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: yeni rol matrisi ile mevcut tarih/401 testleri başarılı.

---

### Task 3: Güvenli izin türü lookup endpoint'ini ekle

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/repository/IzinTuruRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTuruLookupResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/IzinTuruService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/IzinTuruController.java`
- Create: `backend/src/test/java/com/jforce/backend/IzinTuruControllerIntegrationTest.java`

**Interfaces:**

```java
public record IzinTuruLookupResponse(
        Integer id,
        String ad,
        boolean yillikHaktanDuserMi
) {}

List<IzinTuru> findAllByOrderByIzinTuruAdiAscIzinTuruIdAsc();
```

- [ ] **Step 1: Endpoint yokken RED integration testi yaz**

Happy path farklı adlarla eklenmiş türlerin alfabetik ve güvenli DTO olarak döndüğünü doğrulamalıdır:

```java
mockMvc.perform(get("/api/leave-types")
        .header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(personel, RolAdi.PERSONEL)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].ad").value("Hastalık İzni"))
    .andExpect(jsonPath("$[0].yillikHaktanDuserMi").value(false))
    .andExpect(jsonPath("$[0].parolaHash").doesNotExist());
```

Matriste PERSONEL, uygun YONETICI, HR ve ADMIN `200`; kök YONETICI/pasif `403`, missing principal `404`, unauthenticated `401` olmalıdır. Her okumada audit `0` kalmalıdır.

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTuruControllerIntegrationTest#aktifPersonelIzinTurleriniAlfabetikVeGuvenliGorur" "-Djacoco.skip=true" test
```

Expected: endpoint bulunmadığı için `404`.

- [ ] **Step 3: Repository, DTO, service ve controller'ı minimal ekle**

Service sözleşmesi:

```java
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
public List<IzinTuruLookupResponse> izinTurleriniGetir(String aktorId) {
    izinAktoruYetkiService.lookupAktoruGetir(aktorId);
    return izinTuruRepository
            .findAllByOrderByIzinTuruAdiAscIzinTuruIdAsc()
            .stream()
            .map(tur -> new IzinTuruLookupResponse(
                    tur.getIzinTuruId(),
                    tur.getIzinTuruAdi(),
                    tur.isYillikHaktanDuserMi()
            ))
            .toList();
}
```

Controller yalnız `Authentication#getName`, service çağrısı ve `200` response üretmelidir.

- [ ] **Step 4: Lookup paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTuruControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: tüm lookup sözleşmesi, roller ve auditsizlik başarılı.

---

### Task 4: Talep DTO/persistence temelini ve happy-path create akışını ekle

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/repository/DurumTuruRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/IzinTalepController.java`
- Create: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/request/IzinTalepCreateRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`

**Interfaces:**

```java
Optional<DurumTuru> findByDurumKodu(TalepDurum durumKodu);

@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Personel p where p.personelId = :personelId")
Optional<Personel> findByIdForUpdate(@Param("personelId") String personelId);

public IzinTalepResponse talepOlustur(
        String aktorId,
        IzinTalepCreateRequest request
);
```

- [ ] **Step 1: PERSONEL happy-path RED HTTP testi yaz**

Test gelecekteki üç iş günlük aralık, geçerli izin türü ve boşluklu not göndermeli; `201` response ve DB alanlarını doğrulamalıdır:

```json
{
  "izinTuruId": 1,
  "baslangicTarihi": "2026-09-01",
  "bitisTarihi": "2026-09-03",
  "yoneticiyeNot": "  Aile ziyareti  "
}
```

Expected response alanları:

```text
id UUID string
izinTuru exact name
isGunuSayisi 3.0
yoneticiyeNot "Aile ziyareti"
durum "ONAY_BEKLIYOR"
durumAdi "Onay bekliyor"
talepTarihi non-null ISO datetime
```

DB satırında principal personel, backend hesabı, seeded durum ve null karar alanları doğrulanmalıdır.

- [ ] **Step 2: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#aktifPersonelTalepOlustururVeGuvenliDtoAlir" "-Djacoco.skip=true" test
```

Expected: endpoint bulunmadığı için `404`.

- [ ] **Step 3: Request ve response DTO'larını exact sözleşmeye getir**

```java
public record IzinTalepCreateRequest(
        @NotNull @Positive Integer izinTuruId,
        @NotNull LocalDate baslangicTarihi,
        @NotNull LocalDate bitisTarihi,
        @Size(max = 1000) String yoneticiyeNot
) {}

public record IzinTalepResponse(
        String id,
        String izinTuru,
        LocalDate baslangicTarihi,
        LocalDate bitisTarihi,
        BigDecimal isGunuSayisi,
        String yoneticiyeNot,
        TalepDurum durum,
        String durumAdi,
        LocalDateTime talepTarihi
) {}
```

`@FutureOrPresent` kaldırılmalıdır; geçmiş tarih iş kuralı injected `Clock` kullanan service'te deterministik biçimde uygulanır.

- [ ] **Step 4: Minimal create service/controller akışını ekle**

Controller:

```java
@PostMapping
public ResponseEntity<IzinTalepResponse> talepOlustur(
        @Valid @RequestBody IzinTalepCreateRequest request,
        Authentication authentication
) {
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(izinTalepService.talepOlustur(
                    authentication.getName(), request));
}
```

Service ilk GREEN'de:

```text
locked actor load
common actor validation
type/status load
date validation through IsGunuHesaplamaService
note normalization
entity populate and saveAndFlush
response map
```

Henüz bakiye/overlap/audit testleri eklenmeden bunların spekülatif davranışı yazılmamalıdır.

- [ ] **Step 5: Happy-path GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#aktifPersonelTalepOlustururVeGuvenliDtoAlir" "-Djacoco.skip=true" test
```

Expected: `1 test, 0 failure, 0 error`, `BUILD SUCCESS`.

---

### Task 5: Create rol, validation, sıfır-gün ve atomiklik kurallarını tamamla

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/request/IzinTalepCreateRequest.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`

**Interfaces:**

- Consumes: `IzinAktoruYetkiService.talepIslemineUygunluguDogrula(lockedAktor)`.
- Produces controlled 400/403/404 without partial request or audit.

- [ ] **Step 1: Rol matrisi RED testlerini ekle**

```text
PERSONEL 201, yöneticisi olmasa da
HR 201, yöneticisi olmasa da
uygun YONETICI 201
kök YONETICI 403
ADMIN 403
pasif aktör 403
missing principal 404
JWT–DB rol uyuşmazlığı 403
unauthenticated 401
```

Her başarısız senaryoda `izinBilgileriRepository.count()==0` ve audit `0` olmalıdır.

- [ ] **Step 2: Request/business validation RED testlerini ekle**

```text
body yok/bozuk 400
izinTuruId null/0/negatif 400
tarihler null/ters/geçmiş/367 dahil gün 400
yalnız hafta sonu veya tam tatil sonucu 0.0 -> 400
bilinmeyen pozitif izin türü -> 404 "İzin türü bulunamadı."
not trim sonrası boş -> DB/response null
trim sonrası 1000 kabul, 1001 -> 400
```

- [ ] **Step 3: RED paketini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#talepOlusturmaRolMatrisi+gecersizTalepBodyleri400VeYanEtkisizdir+sifirIsGunuTalebi400Doner+bilinmeyenIzinTuru404VeAtomiktir" "-Djacoco.skip=true" test
```

Expected: eksik service validation/rol davranışları nedeniyle assertion failure.

- [ ] **Step 4: Service validation sırasını uygula**

No-op olmadığı için sıra şu olmalıdır:

```text
1 locked principal lookup
2 aktiflik + JWT–DB rol + Yönetici uygunluğu
3 request tarih ve not business validation
4 izin türü lookup
5 ONAY_BEKLIYOR durum lookup
6 backend iş günü hesabı ve >0 kontrolü
7 sonraki task'larda overlap/bakiye
8 save/audit
```

Clock constructor dependency'si `ZamanConfig` bean'ini kullanmalıdır. Kontrollü mesajlar:

```text
Geçersiz tarih aralığı.
İzin talebi en fazla 366 gün olabilir.
İzin talebi en az bir iş günü içermelidir.
İzin türü bulunamadı.
İzin durumu bulunamadı.
```

- [ ] **Step 5: Validation/rol paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: create happy path, rol ve validation testleri başarılı.

---

### Task 6: Bakiye rezervasyonu ve etkili iş günü çakışmasını uygula

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java`

**Interfaces:**

```java
BigDecimal yillikHaktanDusenToplam(
        String personelId,
        Collection<TalepDurum> durumlar
);

List<IzinBilgileri> findCakismaAdaylari(
        String personelId,
        Collection<TalepDurum> durumlar,
        LocalDate baslangicTarihi,
        LocalDate bitisTarihi
);
```

- [ ] **Step 1: Bakiye rezervasyonu RED testlerini yaz**

Test matrisi:

```text
14 hak, 4 onaylı + 3 bekleyen -> en fazla 7 günlük yeni yıllık talep
tam 7 günlük talep başarılı
7.5 veya 8 günlük talep 400 "Yetersiz izin bakiyesi."
reddedilmiş/iptal edilmiş talepler rezervasyona girmez
yillikHaktanDuserMi=false tür bakiye kontrolünden muaftır
başarısızlık mevcut talepleri değiştirmez ve yeni satır/audit bırakmaz
```

- [ ] **Step 2: Etkili iş günü overlap RED testlerini yaz**

```text
ONAY_BEKLIYOR ortak etkili iş günü -> 409
ONAYLANDI ortak etkili iş günü -> 409
farklı izin türü de -> 409
REDDEDILDI/IPTAL_EDILDI -> engellemez
takvim aralığı yalnız ortak hafta sonu -> engellemez
takvim aralığı yalnız ortak tam günlük tatil -> engellemez
ortak yarım iş günü -> 409
```

Exact conflict message: `Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.`

- [ ] **Step 3: RED paketini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#bekleyenTalepBakiyeyiRezerveEder+yillikHaktanDusmeyenTurBakiyedenBagimsizdir+etkiliIsGunuCakismasi409VeAtomiktir+yalnizCalisilmayanGunKesisimiCakismaSayilmaz" "-Djacoco.skip=true" test
```

Expected: bakiye/overlap kuralları henüz olmadığı için fazla create başarılı olur.

- [ ] **Step 4: Repository toplam ve aday sorgularını ekle**

Toplam JPQL:

```java
select coalesce(sum(i.isGunuSayisi), 0.0)
from IzinBilgileri i
where i.personelId.personelId = :personelId
  and i.izinTuruId.yillikHaktanDuserMi = true
  and i.izinDurumId.durumKodu in :durumlar
```

Overlap aday JPQL:

```java
select i
from IzinBilgileri i
where i.personelId.personelId = :personelId
  and i.izinDurumId.durumKodu in :durumlar
  and i.baslangicTarihi <= :bitisTarihi
  and i.bitisTarihi >= :baslangicTarihi
order by i.baslangicTarihi asc, i.izinId asc
```

- [ ] **Step 5: Bakiye ve overlap service kontrollerini ekle**

Bakiye:

```java
BigDecimal kullanilan = repository.yillikHaktanDusenToplam(
        personelId, List.of(TalepDurum.ONAYLANDI));
BigDecimal rezerve = repository.yillikHaktanDusenToplam(
        personelId, List.of(TalepDurum.ONAY_BEKLIYOR));
BigDecimal kullanilabilir = BigDecimal
        .valueOf(aktor.getYillikIzinHakki())
        .subtract(kullanilan)
        .subtract(rezerve);
if (yeniIsGunu.compareTo(kullanilabilir) > 0) {
    throw new GecersizIstekException("Yetersiz izin bakiyesi.");
}
```

Her overlap adayı için kesişim:

```java
LocalDate ortakBaslangic = max(yeniBaslangic, mevcutBaslangic);
LocalDate ortakBitis = min(yeniBitis, mevcutBitis);
if (isGunuHesaplamaService
        .isGunuSayisiniHesapla(ortakBaslangic, ortakBitis)
        .compareTo(BigDecimal.ZERO) > 0) {
    throw new KaynakCakismasiException(CAKISMA_MESAJI);
}
```

- [ ] **Step 6: Profil bakiyesi anlamını koruyan regresyon testi ekle**

Bekleyen yıllık izin talebi oluşturulduktan sonra `GET /api/profile` içindeki `kalanIzin`, yalnız `ONAYLANDI` tüketimi düşmeye devam etmelidir; pending rezervasyon bu response'a gizlice yansımamalıdır.

- [ ] **Step 7: Bakiye/overlap/profil paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest,ProfilControllerIntegrationTest,IzinHesaplamaControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: bakiye, overlap, preview ve profil regresyonları başarılı.

---

### Task 7: Paralel create isteklerinde pessimistic lock garantisini doğrula

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/IzinTalepConcurrencyIntegrationTest.java`
- Modify only if the test exposes a defect: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify only if the test exposes a defect: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`

**Interfaces:**

- Consumes: `PersonelRepository.findByIdForUpdate` inside the create transaction.
- Produces: same-person requests serialize before overlap/balance reads.

- [ ] **Step 1: Deterministik aynı-personel parallel RED/GREEN integration testi yaz**

İki executor thread aynı kişi için çakışan veya toplamda bakiyeyi aşan iki talep göndermelidir. Her thread kendi `SecurityContext`'ini kurup temizlemelidir. `@MockitoSpyBean PersonelRepository` ve latch'ler, ilk transaction personel kilidini aldıktan sonra ikinci çağrının kilidi istemesini kanıtlamalıdır.

Assertions:

```java
assertEquals(1, basariliSayisi);
assertEquals(1, kontrolluRedSayisi);
assertEquals(1L, izinBilgileriRepository.count());
assertEquals(1L, auditLogRepository.count());
```

Test timeout kullanmalı ve SQL logunda ikinci transaction'ın ilk commit sonrasında ilerlediği görülebilmelidir.

- [ ] **Step 2: Concurrency testini çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepConcurrencyIntegrationTest" "-Djacoco.skip=true" test
```

Expected: lock doğru yer ve transaction içindeyse GREEN. Test iki başarı veya timeout verirse testi gevşetme; lock sorgusu/transaction sınırını düzelt.

- [ ] **Step 3: Farklı personellerin gereksiz yere birbirini kilitlemediğini doğrula**

Ayrı personel satırlarıyla iki geçerli talep aynı anda tamamlanabilmeli; DB'de iki talep ve iki audit bulunmalıdır. Test yalnız correctness kanıtlar; hassas süre assertion'ı kullanmamalıdır.

- [ ] **Step 4: Concurrency ve create paketini birlikte doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepConcurrencyIntegrationTest,IzinTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: paralellik ve HTTP davranışları başarılı.

---

### Task 8: Create audit/rollback ve persistence fallback davranışını tamamla

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`
- Create: `backend/src/test/java/com/jforce/backend/IzinTalepServiceTest.java`

**Interfaces:**

- Produces `AuditEylem.IZIN_TALEBI_OLUSTURULDU`.
- Uses `AuditService.logKaydetOrtakTransaction(...)`.

- [ ] **Step 1: Güvenli audit için RED integration testi yaz**

Gerçek değişiklik sonrası tek audit:

```java
assertEquals(AuditEylem.IZIN_TALEBI_OLUSTURULDU, audit.getEylemTipi());
assertEquals(ownerId, audit.getPersonel().getPersonelId());
assertEquals(ownerId, audit.getHedefPersonel().getPersonelId());
assertEquals("İzin talebi oluşturuldu.", audit.getDetay());
```

Audit detayında yönetici notu, izin türü, tarihler, iş günü, bakiye, token veya parola olmamalıdır.

- [ ] **Step 2: Dış transaction rollback RED testini yaz**

Testte `@Primary` wrapper service, gerçek `talepOlustur` çağrısından sonra aynı dış transaction içinde `IllegalStateException` atmalıdır. Sonuçta talep ve audit birlikte `0` kalmalıdır.

- [ ] **Step 3: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#gercekTalepTekGuvenliAuditOlusturur+disTransactionHatasindaTalepVeAuditBirlikteRollbackOlur" "-Djacoco.skip=true" test
```

Expected: audit enum/save henüz olmadığı için audit assertion'ı fail.

- [ ] **Step 4: Audit olayını ve aynı-transaction kaydını ekle**

`saveAndFlush` sonrasında:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.IZIN_TALEBI_OLUSTURULDU,
        aktor,
        aktor.getKullaniciAdi(),
        "İzin talebi oluşturuldu.",
        aktor
);
```

Validation, 403/404/409 ve read-only işlemlerde bu çağrı yapılmamalıdır.

- [ ] **Step 5: Persistence exception için odaklı unit test ekle**

`izinBilgileriRepository.saveAndFlush` bir `DataIntegrityViolationException` verdiğinde service bu beklenmeyen hatayı yeni bir business conflict'e çevirmemeli; exception yukarı taşınmalı ve mevcut generic handler HTTP'de yalnız `500 Beklenmeyen bir hata oluştu.` döndürmelidir. Unit test aynı exception instance'ının taşındığını ve audit'in çağrılmadığını; HTTP integration testi SQL/constraint mesajının response'a çıkmadığını doğrulamalıdır. Ürün sözleşmesinde olmayan yeni `409` mesajı icat edilmemelidir.

- [ ] **Step 6: Audit/rollback/unit paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest,IzinTalepServiceTest" "-Djacoco.skip=true" test
```

Expected: tek audit, doğru actor/target/detail, rollback ve güvenli persistence davranışı başarılı.

---

### Task 9: Owner-only sayfalı `/my` listesini ekle

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/controller/IzinTalepController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/IzinTalepService.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`
- Modify: `backend/src/test/java/com/jforce/backend/IzinTalepControllerIntegrationTest.java`

**Interfaces:**

```java
Page<IzinBilgileri> findKendiTalepleri(
        String personelId,
        TalepDurum durum,
        Pageable pageable
);

public SayfaliResponse<IzinTalepResponse> kendiTalepleriniGetir(
        String aktorId,
        int page,
        int size,
        TalepDurum durum
);
```

- [ ] **Step 1: Endpoint yokken owner-only pagination RED testi yaz**

İki kullanıcıya talepler eklenmeli; aktör yalnız kendisininkileri görmelidir:

```text
GET /api/leave-requests/my?page=0&size=2
200 SayfaliResponse
icerik length 2
toplamKayit yalnız owner kayıtları
talepTarihi DESC, eşitlikte izinId DESC
başkasının notu/talebi görünmez
```

- [ ] **Step 2: Durum ve parametre validation RED testlerini ekle**

```text
durum=ONAY_BEKLIYOR -> yalnız ilgili durum
durum=BOZUK -> 400
page=-1 -> 400
size=0 veya 101 -> 400
varsayılan page=0,size=10
```

Rol matrisi create ile aynıdır: PERSONEL/HR/uygun YONETICI `200`; kök YONETICI/Admin/pasif `403`; missing principal `404`; unauthenticated `401`. Liste audit üretmez.

- [ ] **Step 3: RED'i doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest#personelYalnizKendiTalepleriniKararliSayfaylaGorur+kendiTalepleriDurumFiltresiVeParametreDogrulamasi" "-Djacoco.skip=true" test
```

Expected: `/my` mapping olmadığı için `404`.

- [ ] **Step 4: Owner-scoped repository sorgusunu ekle**

```java
@Query("""
        select i
        from IzinBilgileri i
        where i.personelId.personelId = :personelId
          and (:durum is null or i.izinDurumId.durumKodu = :durum)
        """)
Page<IzinBilgileri> findKendiTalepleri(
        @Param("personelId") String personelId,
        @Param("durum") TalepDurum durum,
        Pageable pageable
);
```

Ownership request parametresi değildir; principal ID zorunlu query predicate'idir.

- [ ] **Step 5: Service/controller sayfalama akışını ekle**

Controller defaults ve min/max validation:

```java
@Validated
@RestController
@RequestMapping("/api/leave-requests")
class IzinTalepController {

@RequestParam(defaultValue = "0") @Min(0) int page,
@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
@RequestParam(required = false) TalepDurum durum
}
```

`@Validated`, query-param constraint'lerinin method validation ile gerçekten `400` üretmesi için controller üzerinde bulunmalıdır.

Service sort:

```java
Sort sort = Sort.by(Sort.Order.desc("talepTarihi"))
        .and(Sort.by(Sort.Order.desc("izinId")));
PageRequest pageable = PageRequest.of(page, size, sort);
```

`SayfaliResponse` mevcut ortak record ile oluşturulmalıdır.

- [ ] **Step 6: `/my` ve create paketini GREEN doğrula**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: owner-only sayfalama, filtre, roller, auditsizlik ve create testleri başarılı.

---

### Task 10: Odaklı regresyon, ürün belgeleri ve tam backend doğrulaması

**Files:**

- Modify after behavior is GREEN: `API_SPEC.md`
- Modify after behavior is GREEN: `SECURITY.md`
- Modify after behavior is GREEN: `ROADMAP.md`
- Modify after behavior is GREEN: `PROGRESS.md`
- Modify stale status only: `README.md`
- Modify stale status only: `frontend/README.md`

**Interfaces:**

- Produces exact frontend contract for lookup, preview, create and `/my`.
- Records migration-before-deploy and audit ENUM requirement.

- [ ] **Step 1: İzin/tatil/security odaklı regresyonu çalıştır**

Run:

```powershell
.\mvnw.cmd "-Dtest=IzinTalepFoundationMigrationIntegrationTest,IzinTuruControllerIntegrationTest,IzinHesaplamaControllerIntegrationTest,IzinTalepControllerIntegrationTest,IzinTalepConcurrencyIntegrationTest,IzinTalepServiceTest,IsGunuHesaplamaServiceTest,TatilTakvimiControllerIntegrationTest,ProfilControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: `0 failure, 0 error`, `BUILD SUCCESS`.

- [ ] **Step 2: `API_SPEC.md` exact sözleşmesini güncelle**

Şunları açıkça kaydet:

```text
GET /api/leave-types response ve rol matrisi
GET /api/leave-requests/workday-preview genişleyen Yönetici davranışı
POST /api/leave-requests exact request/201 response
GET /api/leave-requests/my page/size/durum/default/sort
400/401/403/404/409 mesaj ve koşulları
pending rezervasyonun profile.kalanIzin anlamını değiştirmediği
```

- [ ] **Step 3: `SECURITY.md` ve deployment sözleşmesini güncelle**

Şunları kaydet:

```text
JWT–DB rol uyumu ve aktif aktör kontrolü
Yönetici uygunluk kuralı
principal-only ownership
Personel PESSIMISTIC_WRITE lock
bakiye/overlap/save/audit same transaction
audit exact actor/target/detail
manual migration deploydan önce zorunlu
ddl-auto=update ENUM genişlemesi yerine geçmez
```

- [ ] **Step 4: `ROADMAP.md` ve `PROGRESS.md` gerçek durumu güncelle**

Yalnız lookup/preview/create/my tamamlandı olarak işaretlenir. İptal, onay/red, yönetici/Admin listeleri ve hak tahakkuku açık kalır. Eski “Yönetici izin oluşturamaz” ifadesi, yalnız uygun non-root Yönetici oluşturabilir biçiminde düzeltilir.

Audit roadmap'i:

```text
bu teslim: IZIN_TALEBI_OLUSTURULDU
iptal teslimi: IZIN_TALEBI_IPTAL_EDILDI
onay teslimi: IZIN_TALEBI_ONAYLANDI
red teslimi: IZIN_TALEBI_REDDEDILDI
edit ayrıca onaylanırsa: IZIN_TALEBI_GUNCELLENDI
```

- [ ] **Step 5: README durum sapmalarını düzelt**

Kök ve frontend README'de doğum günü takvimi frontend entegrasyonunu bekliyor gösteren satırlar, daha önce doğrulanmış 13/13 frontend test sonucuyla uyumlu hale getirilir. Bu değişiklik izin endpoint'lerine ilişkin yeni frontend implementation yapılmış gibi yazılmamalıdır.

- [ ] **Step 6: Tam backend suite ve coverage çalıştır**

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

- [ ] **Step 7: Teslim raporunu hazırla**

Rapor şunları içermelidir:

```text
değişen dosyalar ve sorumlulukları
repository -> service -> DTO/controller veri akışı
kesin endpoint ve rol matrisi
bakiye rezervasyonu ve etkili overlap mantığı
transaction/lock/audit davranışı
400/401/403/404/409 sözleşmesi
migration içeriği ve uygulama sırası
odaklı ve tam test sonuçları
çalıştırılamayan kontroller
frontend'in kullanacağı exact alanlar
sonraki teslim: cancellation + manager/Admin processing
```

---

## Execution Notes

- Her task kendi RED → minimal GREEN → ilgili regresyon döngüsünde tamamlanmalıdır.
- Test compile hatası davranış RED'i değildir; önce test kodu derlenir hale getirilip endpoint/service eksikliğine ait gerçek RED alınmalıdır.
- MySQL/Testcontainers sandbox nedeniyle çalışmazsa kullanıcıdan exact komutla yerel kanıt istenir; Docker hatası production davranışı hakkında kanıt sayılmaz.
- Aynı service/repository/test dosyasında eşzamanlı agent çalıştırılmamalıdır.
- Kullanıcı backend kodunun agent tarafından yazılmasına bu özellik için ayrıca açık izin vermelidir; plan onayı tek başına production yazma izni sayılmaz.
- Git işlemlerini yalnız kullanıcı yapar.
