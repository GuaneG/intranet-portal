# Admin Parola Sıfırlama Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif Admin’in izinli hedef hesaba geçici parola atayıp oturumları iptal etmesini ve mevcut ilk-parola akışını zorunlu kılmasını sağlamak.

**Architecture:** İnce `AdminPersonelController` yeni request’i `ParolaService` use-case’ine iletir. Yetki DB tabanlı `PersonelYonetimYetkiService`, parola politikası mevcut `ParolaService`, token iptali `RefreshTokenService`, audit aynı transaction içinde mevcut `AuditService` tarafından uygulanır.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, BCrypt, MySQL 8 Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- Endpoint `PATCH /api/admin/personel/{personelId}/parola-sifirla`, başarı `204`.
- Yalnız aktif ve DB rolü gerçek `ADMIN`; aktif/pasif PERSONEL/HR/YONETICI hedefleri izinli.
- Öz-hedef ve ADMIN hedef yasak; kontrol parola validation’ından önce.
- Parola yalnız BCrypt hash saklanır ve hiçbir response/audit/log içinde dönmez.
- Hash, `parolaDegistirilmeliMi=true`, refresh-token silme ve audit tek transaction’dır.
- Git komutu çalıştırılmaz; frontend kapsam dışıdır.

---

### Task 1: Happy-path endpoint ve hash

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/AdminParolaSifirlaRequest.java`
- Create: `backend/src/test/java/com/jforce/backend/AdminParolaSifirlamaControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/AdminPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/ParolaService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimYetkiService.java` only if existing target validation cannot be reused safely

**Interfaces:**
- Produces `void adminParolaSifirla(String aktorId, String hedefPersonelId, AdminParolaSifirlaRequest request)`.
- Request record: `record AdminParolaSifirlaRequest(@NotBlank String geciciParola)`.

- [ ] **Step 1: Happy-path RED testini yaz**

Test aktif Admin → aktif PERSONEL çağrısında `204`, boş body, `PasswordEncoder.matches(geciciParola, hash)=true`, ham parolanın DB’de bulunmaması ve flag’in true olması beklentilerini kursun.

- [ ] **Step 2: RED’i doğrula**

```powershell
.\mvnw.cmd "-Dtest=AdminParolaSifirlamaControllerIntegrationTest#adminPersonelinParolasiniHashleyerekSifirlar" "-Djacoco.skip=true" test
```

Beklenen: eksik endpoint nedeniyle `204` yerine `404`.

- [ ] **Step 3: Minimal controller ve service use-case’ini ekle**

```java
@PatchMapping("/{personelId}/parola-sifirla")
public ResponseEntity<Void> parolaSifirla(
        @PathVariable String personelId,
        @Valid @RequestBody AdminParolaSifirlaRequest request,
        Authentication authentication) {
    parolaService.adminParolaSifirla(authentication.getName(), personelId, request);
    return ResponseEntity.noContent().build();
}
```

Service metodu `@Transactional` ve `@PreAuthorize("hasRole('ADMIN')")` olsun; aktörü `aktifAdminAktoruGetir`, hedefi repository, hedef matrisini mevcut `hedefiDogrula` ile doğrulasın. Sonra `yeniParolayiDogrula`, `hashle` ve flag güncellemesini yapsın.

- [ ] **Step 4: GREEN’i doğrula**

Aynı komutta `1/1` GREEN beklenir.

---

### Task 2: Refresh iptali, zorunlu giriş ve audit

**Files:**
- Modify: `AdminParolaSifirlamaControllerIntegrationTest.java`
- Modify: `ParolaService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Interfaces:** Yeni enum `PAROLA_SIFIRLANDI`.

- [ ] **Step 1: Refresh-token ve audit RED testlerini yaz**

Hedefin iki refresh tokenının silindiğini, başka personelin tokenının kaldığını; tek auditte actor Admin, target hedef, action `PAROLA_SIFIRLANDI`, detay exact `Personel parolası Admin tarafından sıfırlandı.` olduğunu doğrula. Detay geçici parola/hash içermesin.

- [ ] **Step 2: Login/ilk-parola uçtan uca testini yaz**

Geçici parolayla login `428` ve tokensız olmalı; `/api/auth/ilk-parola` tamamlandıktan sonra yeni parola login’i başarılı olmalı.

- [ ] **Step 3: Minimal production davranışını ekle**

```java
refreshTokenService.revokeAllForPersonel(hedef);
auditService.logKaydetOrtakTransaction(
        AuditEylem.PAROLA_SIFIRLANDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Personel parolası Admin tarafından sıfırlandı.",
        hedef);
```

- [ ] **Step 4: Hedefli testleri GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=AdminParolaSifirlamaControllerIntegrationTest" "-Djacoco.skip=true" test
```

---

### Task 3: Yetki, validation ve rollback

**Files:**
- Modify: `AdminParolaSifirlamaControllerIntegrationTest.java`
- Modify only on proven RED: `ParolaService.java`, `PersonelYonetimYetkiService.java`, `GlobalExceptionHandler.java`

- [ ] **Step 1: Hedef/aktör matrisini ekle**

Aktif ve pasif PERSONEL/HR/YONETICI izinli; öz-hedef/Admin hedef, PERSONEL/HR/YONETICI aktör, JWT-Admin/DB-HR, pasif Admin `403`; missing aktör/hedef `404`; no-token `401`.

- [ ] **Step 2: Validation matrisini ekle**

Null/boş/eksik/bozuk body ve mevcut parola politikasındaki kısa, uzun, 72-byte üstü, whitespace, eksik karakter sınıfı, kullanıcı adıyla aynı ve mevcut parolayla aynı değerler `400` olmalı. Yasak hedef zayıf parola gönderse bile `403` almalı.

- [ ] **Step 3: Yan etkisizliği ve rollback’i ekle**

Başarısız isteklerde eski hash/flag/token/audit korunmalı. Test `@Transactional` wrapper, service’i çağırıp `IllegalStateException` atmalı; bütün state geri gelmeli.

- [ ] **Step 4: Full feature suite’i çalıştır**

```powershell
.\mvnw.cmd "-Dtest=AdminParolaSifirlamaControllerIntegrationTest,ParolaControllerIntegrationTest,AuthControllerIntegrationTest,RefreshServiceIntegrationTest,PersonelYonetimYetkiServiceTest" "-Djacoco.skip=true" test
```

---

### Task 4: Review, belgeler ve full doğrulama

- [ ] **Step 1:** Bağımsız review’da yetki sırası, secret sızıntısı ve transaction sınırını incele.
- [ ] **Step 2:** Kullanıcıdan Markdown yazma onayı al; `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md` içine kesin sözleşme ve 15 dakikalık access-token penceresini yaz.
- [ ] **Step 3:** UTF-8/CRLF/U+FFFD/mojibake/duplicate bölüm kontrollerini çalıştır.
- [ ] **Step 4:** Kullanıcı ortamında `.\mvnw.cmd test` ile tam backend ve JaCoCo doğrulamasını al.

