# Departman Yönetimi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif HR/Admin kullanıcıların departman oluşturmasını ve mevcut departmanı aynı ID üzerinde güvenli biçimde yeniden adlandırmasını sağlamak.

**Architecture:** Mevcut `GET /api/departments` lookup akışı değiştirilmez. `POST` ve `PATCH` işlemleri yeni `DepartmanYonetimController` ile `DepartmanYonetimService` içinde yürür; mevcut repository genişletilir, gerçek değişiklikler ortak transaction audit'iyle kaydedilir.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security method security, Spring Data JPA, Jakarta Validation, JUnit 5, Mockito, MockMvc, Testcontainers, MySQL 8.

## Global Constraints

- Java ve test dosyalarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Frontend dosyalarına dokunulmaz.
- Git komutu çalıştırılmaz.
- Mevcut `GET /api/departments` sözleşmesi ve `LookUpResponse` korunur.
- Departman silme, pasifleştirme, birleştirme ve toplu personel taşıma kapsam dışıdır.
- Oluşturma ve gerçek güncelleme audit ile aynı transaction içinde tamamlanır veya rollback olur.
- Exact normalize eşitlik no-op'tur; yalnız harf biçimi değişikliği gerçek güncellemedir.

---

## Dosya haritası

Yeni production dosyaları:

- `backend/src/main/java/com/jforce/backend/model/dto/request/DepartmanAdiRequest.java`
- `backend/src/main/java/com/jforce/backend/controller/DepartmanYonetimController.java`
- `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`

Değişecek production dosyaları:

- `backend/src/main/java/com/jforce/backend/repository/DepartmanRepository.java`
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

Yeni test dosyaları:

- `backend/src/test/java/com/jforce/backend/DepartmanYonetimControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/DepartmanYonetimServiceTest.java`

Doğrulama sonrasında değişecek belgeler:

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`
- `SECURITY.md`

---

### Task 1: Temel oluşturma ve aynı ID üzerinde yeniden adlandırma

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/DepartmanYonetimControllerIntegrationTest.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/DepartmanAdiRequest.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/DepartmanYonetimController.java`
- Create: `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/DepartmanRepository.java`

**Interfaces:**

- Produces: `POST /api/departments`
- Produces: `PATCH /api/departments/{departmanId}`
- Produces: `LookUpResponse departmanOlustur(String aktorId, DepartmanAdiRequest request)`
- Produces: `void departmanGuncelle(String aktorId, Integer departmanId, DepartmanAdiRequest request)`

- [ ] **Step 1: İki failing integration testi ekle**

İlk test aktif HR ile şu isteği gönderir:

```http
POST /api/departments
{"departmanAdi":"  İnsan Kaynakları  "}
```

Beklenen observable sonuç:

```text
201
response {id, ad="İnsan Kaynakları"}
veritabanında trim edilmiş tek kayıt
```

İkinci test mevcut departmanı günceller ve bağlı personelin aynı `departmanId` değerinde kaldığını doğrular:

```http
PATCH /api/departments/{id}
{"departmanAdi":"Kurumsal Teknoloji"}
```

Beklenen observable sonuç:

```text
204
departman adı değişmiş
departman ID değişmemiş
personel.departman.departmanId aynı
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

Beklenen: endpoint/controller bulunmadığı için iki status failure; compilation error yok.

- [ ] **Step 3: Request DTO'sunu ekle**

```java
public record DepartmanAdiRequest(
        @NotBlank
        String departmanAdi
) {
}
```

- [ ] **Step 4: Repository duplicate metotlarını ekle**

```java
boolean existsByDepartmanAdiIgnoreCase(String departmanAdi);

boolean existsByDepartmanAdiIgnoreCaseAndDepartmanIdNot(
        String departmanAdi,
        Integer departmanId
);
```

- [ ] **Step 5: Controller sözleşmesini ekle**

```java
@RestController
@RequestMapping("/api/departments")
public class DepartmanYonetimController {
    @PostMapping
    public ResponseEntity<LookUpResponse> departmanOlustur(
            @Valid @RequestBody DepartmanAdiRequest request,
            Authentication authentication
    )

    @PatchMapping("/{departmanId}")
    public ResponseEntity<Void> departmanGuncelle(
            @PathVariable Integer departmanId,
            @Valid @RequestBody DepartmanAdiRequest request,
            Authentication authentication
    )
}
```

- [ ] **Step 6: Minimum service akışını ekle**

```java
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public LookUpResponse departmanOlustur(
        String aktorId,
        DepartmanAdiRequest request
)

@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public void departmanGuncelle(
        String aktorId,
        Integer departmanId,
        DepartmanAdiRequest request
)
```

Service aktörü repository'den yükler, aktifliği doğrular, adı trim eder, entity'yi `saveAndFlush` ile kaydeder ve entity'yi DTO'ya dönüştürür. Bu ilk GREEN adımında audit enum ve ayrıntılı hata dalları Task 2–3 testlerinden önce eklenmez.

- [ ] **Step 7: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

---

### Task 2: Validation, duplicate ve no-op semantiği

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/DepartmanYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`

**Interfaces:**

- Consumes: Task 1 endpoint ve service imzaları.
- Produces: kontrollü `400`, `404`, `409` ve exact no-op davranışı.

- [ ] **Step 1: Failing validation ve duplicate integration testlerini ekle**

Test tablosu literal girdiler ve status'lar kullanır:

```text
POST null/eksik/boş/blank ad -> 400
POST trim sonrası 51 karakter -> 400
PATCH departmanId 0/-1 -> 400
PATCH olmayan pozitif ID -> 404 Departman bulunamadı.
POST mevcut adın farklı case'i -> 409 Departman zaten mevcut.
PATCH başka departmana farklı case duplicate -> 409
PATCH exact normalize aynı ad -> 204, save/audit yan etkisi yok
PATCH yalnız case değişikliği -> 204 ve ad güncellenir
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

- [ ] **Step 3: Service iş kurallarını ekle**

```java
private String departmanAdiniNormalizeEt(String departmanAdi) {
    String normalizeAd = departmanAdi.trim();
    if (normalizeAd.length() > 50) {
        throw new GecersizIstekException(
                "Departman adı en fazla 50 karakter olmalıdır."
        );
    }
    return normalizeAd;
}
```

Güncellemede sıralama:

```text
aktif aktör doğrula
pozitif ID doğrula
adı normalize et
departmanı yükle
exact equals ise return
başka ID'de ignore-case duplicate kontrolü
aynı entity üzerinde adı değiştir
saveAndFlush
```

- [ ] **Step 4: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

---

### Task 3: Aktif HR/Admin güvenliği, audit ve rollback

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/DepartmanYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`

**Interfaces:**

- Produces: `DEPARTMAN_EKLENDI`
- Produces: `DEPARTMAN_GUNCELLENDI`

- [ ] **Step 1: Failing security/audit integration testlerini ekle**

```text
aktif HR POST/PATCH -> izinli
aktif Admin POST/PATCH -> izinli
PERSONEL/YONETICI -> 403
pasif HR/Admin -> 403
olmayan principal -> 404 Personel bulunamadı.
token yok -> 401
oluşturma -> tek DEPARTMAN_EKLENDI audit
gerçek rename/case-only rename -> tek DEPARTMAN_GUNCELLENDI audit
no-op -> audit yok
audit aktörü principal, hedefPersonel null
outer transaction hata -> departman ve audit birlikte rollback
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

- [ ] **Step 3: Audit enum ve service çağrılarını ekle**

```java
DEPARTMAN_EKLENDI,
DEPARTMAN_GUNCELLENDI
```

Oluşturma:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.DEPARTMAN_EKLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Departman oluşturuldu: " + normalizeAd,
        null
);
```

Güncelleme:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.DEPARTMAN_GUNCELLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Departman güncellendi: " + eskiAd + " -> " + normalizeAd,
        null
);
```

- [ ] **Step 4: GREEN ve rollback doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimControllerIntegrationTest test
```

---

### Task 4: Unique constraint yarışı fallback'i

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/DepartmanYonetimServiceTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/DepartmanYonetimService.java`

**Interfaces:**

- Produces: `DataIntegrityViolationException` → `KaynakCakismasiException("Departman zaten mevcut.")`

- [ ] **Step 1: İki failing unit test ekle**

Mockito ile aktif HR aktörü hazırlanır. Oluşturma ve güncellemede duplicate ön kontrolü `false`, `saveAndFlush` ise `DataIntegrityViolationException` döndürür:

```java
assertThrows(
        KaynakCakismasiException.class,
        () -> service.departmanOlustur(
                "hr-id",
                new DepartmanAdiRequest("Finans")
        )
);
```

```java
assertThrows(
        KaynakCakismasiException.class,
        () -> service.departmanGuncelle(
                "hr-id",
                7,
                new DepartmanAdiRequest("Kurumsal Finans")
        )
);
```

İki test de `verifyNoInteractions(auditService)` doğrular.

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=DepartmanYonetimServiceTest test
```

- [ ] **Step 3: Güvenli save helper'ını ekle**

```java
private Departman departmaniGuvenliKaydet(
        Departman departman
) {
    try {
        return departmanRepository.saveAndFlush(departman);
    } catch (DataIntegrityViolationException exception) {
        throw new KaynakCakismasiException(
                "Departman zaten mevcut."
        );
    }
}
```

- [ ] **Step 4: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=DepartmanYonetimControllerIntegrationTest,DepartmanYonetimServiceTest" test
```

---

### Task 5: Lookup regresyonu, tam backend paketi ve belgeler

**Files:**

- Verify: `LookupControllerIntegrationTest`
- Verify: bütün backend testleri
- Modify after verification: `API_SPEC.md`
- Modify after verification: `ROADMAP.md`
- Modify after verification: `PROGRESS.md`
- Modify after verification: `SECURITY.md`

- [ ] **Step 1: Departman yönetimi ve lookup regresyonunu birlikte çalıştır**

```powershell
.\mvnw.cmd "-Dtest=DepartmanYonetimControllerIntegrationTest,DepartmanYonetimServiceTest,LookupControllerIntegrationTest" test
```

- [ ] **Step 2: Tam backend suite'ini çalıştır**

```powershell
.\mvnw.cmd test
```

Kabul: gerçek çıktıda `0 failures`, `0 errors`, `BUILD SUCCESS`. Önceki doğrulanmış taban 279 testtir; belgeye yalnız yeni gerçek toplam yazılır.

- [ ] **Step 3: Belgeleri doğrulanmış davranışla güncelle**

```text
API_SPEC.md: POST/PATCH sözleşmesi, validation, duplicate, no-op ve audit
ROADMAP.md: departman ekleme/güncelleme maddesi [x]
SECURITY.md: aktif HR/Admin ve principal doğrulaması
PROGRESS.md: gerçek odaklı ve tam test sayıları
```

---

## Son kabul ölçütleri

- Mevcut GET lookup sözleşmesi değişmez.
- Aktif HR/Admin departman oluşturabilir ve aynı ID üzerinde rename yapabilir.
- Bağlı personel ilişkileri rename sonrasında korunur.
- Exact no-op audit/persistence yan etkisizdir; case-only değişiklik gerçek güncellemedir.
- Duplicate ön kontrolü ve database yarışı kontrollü `409` döner.
- Gerçek değişiklik ve audit aynı transaction'da tamamlanır veya rollback olur.
- Odaklı ve tam backend testleri başarılıdır.
