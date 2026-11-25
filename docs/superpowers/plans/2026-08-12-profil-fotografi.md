# Güvenli Profil Fotoğrafı Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif kullanıcıların kendi JPEG/PNG profil fotoğrafını güvenli biçimde yüklemesini, değiştirmesini ve silmesini; yalnız aktif authenticated kullanıcıların fotoğrafı görüntülemesini sağlamak.

**Architecture:** `ProfilController` self-service PUT/DELETE sözleşmesini, yeni `ProfilFotografiController` authenticated binary GET sözleşmesini taşır. `ProfilFotografiService` aktör, aktiflik, transaction, audit ve replacement akışını; `ProfilFotografiDepolamaService` yalnız görsel doğrulama, resize/re-encode, güvenli yol ve fiziksel depolamayı yönetir. MySQL yalnız UUID tabanlı dosya anahtarını tutar; görseller yapılandırılabilir webroot-dışı klasörde saklanır.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC multipart, Spring Security/JWT, Spring Data JPA, Java ImageIO, JUnit 5, Mockito, MockMvc, Testcontainers MySQL 8.

## Global Constraints

- Tasarım kaynağı: `docs/superpowers/specs/2026-08-12-profil-fotografi-design.md`.
- Frontend kapsam dışıdır. Markdown belgeleri uygulama sonunda yeniden açık kullanıcı onayıyla güncellenir.
- Hiçbir Git komutu çalıştırılmaz; commit adımı uygulanmaz.
- Yeni dependency eklenmez; görsel işleme Java ImageIO ile yapılır.
- Yalnız JPEG ve PNG kabul edilir. Ham dosya en fazla 2 MB, iki eksen en fazla 2048 px ve toplam piksel en fazla 4.194.304 olur.
- Kalıcı çıktı en-boy oranı korunarak en uzun kenarı en fazla 1024 px olacak biçimde yeniden üretilir; küçük görsel büyütülmez.
- Kalıcı ad yalnız backend tarafından `<UUID>.jpg` veya `<UUID>.png` biçiminde üretilir.
- Development depolama varsayılanı `C:/intranet-portal-data/profil-fotograflari`; testler yalnız OS temp alanını kullanır.
- `def_pfp.png`, özel fotoğraf yok anlamındaki mevcut sentinel değerdir; fiziksel dosya olarak sunulmaz.
- Upload/delete request'i personel ID almaz. Pasif principal upload, delete ve GET işlemlerinde `403` alır.
- Kesin endpoint'ler: `PUT /api/profil/fotograf`, `DELETE /api/profil/fotograf`, `GET /api/profil-fotograflari/{dosyaAnahtari}`.
- GET public static resource değildir; mevcut JWT filter chain korunur.
- Ham exception, decoder mesajı, orijinal ad, UUID anahtarı ve fiziksel yol response/audit içinde bulunmaz.
- Dosya sistemi ile MySQL arasındaki rollback/after-commit telafisi otomatik testlerle korunur.

---

## File Map

**Create:**

- `backend/src/main/java/com/jforce/backend/model/dto/response/ProfilFotografiResponse.java`
- `backend/src/main/java/com/jforce/backend/service/ProfilFotografiDosyasi.java`
- `backend/src/main/java/com/jforce/backend/exception/DesteklenmeyenDosyaTuruException.java`
- `backend/src/main/java/com/jforce/backend/exception/DosyaCokBuyukException.java`
- `backend/src/main/java/com/jforce/backend/service/ProfilFotografiDepolamaService.java`
- `backend/src/main/java/com/jforce/backend/service/ProfilFotografiService.java`
- `backend/src/main/java/com/jforce/backend/controller/ProfilFotografiController.java`
- `backend/src/test/java/com/jforce/backend/ProfilFotografiDepolamaServiceTest.java`
- `backend/src/test/java/com/jforce/backend/ProfilFotografiControllerIntegrationTest.java`

**Modify:**

- `backend/src/main/java/com/jforce/backend/controller/ProfilController.java`
- `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/resources/application-test.properties`
- `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/DashboardControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/PersonelYonetimControllerIntegrationTest.java`

---

### Task 1: Secure storage core

**Files:** Storage service, storage value record, two exceptions, storage unit test, application properties.

**Produces:**

```java
String ProfilFotografiDepolamaService.kaydet(MultipartFile dosya)
ProfilFotografiDosyasi ProfilFotografiDepolamaService.getir(String anahtar)
boolean ProfilFotografiDepolamaService.ozelFotografMi(String anahtar)
void ProfilFotografiDepolamaService.sil(String anahtar)
```

- [ ] **Step 1: Write the first failing unit tests**

Use `@TempDir Path tempDir`. Generate real test images in memory:

```java
private MockMultipartFile jpeg(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertTrue(ImageIO.write(image, "jpg", out));
    return new MockMultipartFile("dosya", "profil.jpg",
            MediaType.IMAGE_JPEG_VALUE, out.toByteArray());
}

@Test
void gecerliJpegUuidAdiIleKaydedilir() throws Exception {
    String key = service.kaydet(jpeg(640, 480));
    assertTrue(key.matches("[0-9a-f-]{36}\\.jpg"));
    assertTrue(Files.isRegularFile(tempDir.resolve(key)));
}

@Test
void buyukGorselEnBoyOraniKorunarakKucultulur() throws Exception {
    String key = service.kaydet(jpeg(1600, 800));
    BufferedImage stored = ImageIO.read(tempDir.resolve(key).toFile());
    assertEquals(1024, stored.getWidth());
    assertEquals(512, stored.getHeight());
}
```

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiDepolamaServiceTest -Djacoco.skip=true test
```

Expected: compilation failure because the storage service does not exist.

- [ ] **Step 3: Add configuration**

`application.properties`:

```properties
app.profil-fotografi.depolama-yolu=${PROFILE_PHOTO_STORAGE_PATH:C:/intranet-portal-data/profil-fotograflari}
spring.servlet.multipart.max-file-size=2MB
spring.servlet.multipart.max-request-size=3MB
```

`application-test.properties`:

```properties
app.profil-fotografi.depolama-yolu=${java.io.tmpdir}/intranet-portal-test/profil-fotograflari
```

The service constructor receives the path through `@Value`, normalizes it to an absolute `Path`, and `@PostConstruct` creates/checks the directory. Tests instantiate it directly with `tempDir.toString()`.

- [ ] **Step 4: Implement validation and rewrite pipeline**

```java
public String kaydet(MultipartFile dosya) {
    dosyaVarliginiVeBoyutunuDogrula(dosya);
    GorselFormati format = formatiDogrula(dosya);
    BufferedImage source = gorseliDogrulaVeOku(dosya, format);
    BufferedImage normalized = gerekirseKucult(source, format);
    byte[] clean = temizGorselUret(normalized, format);
    return guvenliKaydet(clean, format.uzanti());
}
```

Apply in this order:

1. Missing/empty → `GecersizIstekException`.
2. More than `2L * 1024 * 1024` bytes → `DosyaCokBuyukException`.
3. Extension allowlist: `.jpg`, `.jpeg`, `.png`.
4. MIME allowlist: `image/jpeg`, `image/png`.
5. Signatures: JPEG `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`.
6. Extension, MIME and signature must identify the same format.
7. Use `ImageInputStream` and `ImageReader.getWidth(0)/getHeight(0)` before decode.
8. Each dimension must be `1..2048`; `(long) width * height <= 4_194_304L`.
9. Decode must return a `BufferedImage`.
10. Resize ratio:

```java
double ratio = Math.min(1.0, 1024.0 / Math.max(width, height));
int targetWidth = Math.max(1, (int) Math.round(width * ratio));
int targetHeight = Math.max(1, (int) Math.round(height * ratio));
```

Use `TYPE_INT_RGB` for JPEG, `TYPE_INT_ARGB` for PNG. Dispose `ImageReader` and `Graphics2D`; close streams with try-with-resources. Re-encode only the new `BufferedImage`; never persist input bytes.

- [ ] **Step 5: Implement UUID/temp/atomic file handling**

```java
String key = UUID.randomUUID() + "." + extension;
Path target = storageRoot.resolve(key).normalize();
rootIcindeDogrula(target);
Path temporary = Files.createTempFile(storageRoot, ".profil-", ".tmp");
```

Write clean bytes to the temporary file, then move within the same root using `ATOMIC_MOVE`; on `AtomicMoveNotSupportedException`, use normal `Files.move` without overwrite. Every failure deletes only that call's temporary file.

`ozelFotografMi` returns true only for canonical UUID plus `.jpg/.png`. `getir` and `sil` reject `def_pfp.png`, null, malformed keys and normalized paths outside the root with controlled `404` behavior.

- [ ] **Step 6: Expand security unit tests**

Cover PNG alpha, images smaller than 1024, empty input, over 2 MB, 2049 px, unsupported extensions, MIME/signature mismatch, corrupt image, valid PNG with appended `<script>`, original name `../../hedef.jpg`, invalid GET/delete path and missing valid UUID.

The appended-payload test asserts the stored output does not contain the appended text bytes.

- [ ] **Step 7: Verify GREEN**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiDepolamaServiceTest -Djacoco.skip=true test
```

Expected: all storage tests pass and no file exists outside `@TempDir`.

---

### Task 2: Self-service upload and audit

**Files:** Response DTO, business service, `ProfilController`, audit enum, new integration test.

**Produces:**

```java
ProfilFotografiResponse profilFotografimiGuncelle(String personelId, MultipartFile dosya)
```

- [ ] **Step 1: Write RED integration test**

Use `AbstractIntegrationTest`, `@AutoConfigureMockMvc`, existing role/departman/personel setup, and an in-memory JPEG helper:

```java
mockMvc.perform(multipart("/api/profil/fotograf")
        .file(file)
        .with(request -> { request.setMethod("PUT"); return request; })
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.profilFoto").isString());
```

Reload the person, assert DB key equals response, is not `def_pfp.png`, matches UUID `.jpg`, and exists under the test storage root.

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiControllerIntegrationTest -Djacoco.skip=true test
```

Expected: `404` because upload endpoint does not exist.

- [ ] **Step 3: Add DTO, audit values and minimal service**

```java
public record ProfilFotografiResponse(String profilFoto) {}
```

Add:

```java
PROFIL_FOTOGRAFI_GUNCELLENDI,
PROFIL_FOTOGRAFI_SILINDI
```

Service method is `@Transactional` and `@PreAuthorize("isAuthenticated()")`. It loads the principal with `findById`, returns `404 Personel bulunamadı.` when absent, rejects inactive person with `AccessDeniedException`, asks storage to save, updates `Personel.profilFoto`, and writes this same-transaction audit:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.PROFIL_FOTOGRAFI_GUNCELLENDI,
        personel,
        personel.getKullaniciAdi(),
        "Profil fotoğrafı güncellendi.",
        personel
);
```

- [ ] **Step 4: Add PUT endpoint**

```java
@PutMapping(value = "/fotograf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<ProfilFotografiResponse> profilFotografimiGuncelle(
        @RequestPart("dosya") MultipartFile dosya,
        Authentication authentication
) {
    return ResponseEntity.ok(service.profilFotografimiGuncelle(
            authentication.getName(), dosya));
}
```

- [ ] **Step 5: Complete happy-path and audit tests**

Parameterize all four roles. Assert one audit row, actor=target=principal, safe action type, and detail contains no original name, UUID or physical path.

- [ ] **Step 6: Verify GREEN**

```powershell
.\mvnw.cmd "-Dtest=ProfilFotografiDepolamaServiceTest,ProfilFotografiControllerIntegrationTest" -Djacoco.skip=true test
```

---

### Task 3: Controlled upload errors and security negatives

**Files:** `GlobalExceptionHandler`, upload integration test.

- [ ] **Step 1: Write failing HTTP tests**

Cover:

```text
missing multipart part -> 400
empty/corrupt image -> 400
unsupported or mismatched type -> 415
more than 2 MB -> 413
dimensions over limit -> 400
no token -> 401
inactive principal -> 403
missing principal row -> 404 Personel bulunamadı.
```

Each failure must preserve `def_pfp.png`, create no audit, and leave test storage empty.

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiControllerIntegrationTest -Djacoco.skip=true test
```

Expected: missing part, 413 and 415 cases fail under the current generic handler.

- [ ] **Step 3: Add explicit handlers**

Map `DosyaCokBuyukException` and `MaxUploadSizeExceededException` to `413` with `Profil fotoğrafı en fazla 2 MB olabilir.` Map `DesteklenmeyenDosyaTuruException` to `415` with `Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir.` Map `MissingServletRequestPartException` to the existing controlled `400 Geçersiz istek` shape. Never return `ex.getMessage()` for framework/decoder/storage failures.

- [ ] **Step 4: Verify GREEN**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiControllerIntegrationTest -Djacoco.skip=true test
```

---

### Task 4: Authenticated binary GET

**Files:** New `ProfilFotografiController`, business service, integration test.

**Produces:**

```java
ProfilFotografiDosyasi profilFotografiniGetir(String aktorId, String anahtar)
GET /api/profil-fotograflari/{dosyaAnahtari}
```

- [ ] **Step 1: Write RED GET tests**

After uploading a photo, fetch it using another active user's token. Assert exact stored bytes, correct `image/jpeg` or `image/png`, `X-Content-Type-Options: nosniff`, private cache control, no audit and no DB mutation.

Also test no token `401`, inactive principal `403`, absent principal row `404`, and malformed/missing key plus `def_pfp.png` as controlled `404 Profil fotoğrafı bulunamadı.`.

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiControllerIntegrationTest -Djacoco.skip=true test
```

Expected: GET endpoint returns `404`.

- [ ] **Step 3: Implement read-only service and controller**

```java
@Transactional(readOnly = true)
@PreAuthorize("isAuthenticated()")
public ProfilFotografiDosyasi profilFotografiniGetir(String actorId, String key) {
    aktifPersoneliGetir(actorId);
    return storage.getir(key);
}
```

Controller response:

```java
return ResponseEntity.ok()
        .contentType(file.mediaType())
        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
        .header("X-Content-Type-Options", "nosniff")
        .body(file.kaynak());
```

Do not add `permitAll`, a static resource mapping or attachment content disposition.

- [ ] **Step 4: Verify GET and security GREEN**

```powershell
.\mvnw.cmd "-Dtest=ProfilFotografiControllerIntegrationTest,SecurityHttpIntegrationTest" -Djacoco.skip=true test
```

---

### Task 5: Replacement, rollback compensation and DELETE

**Files:** Business service, `ProfilController`, integration test.

- [ ] **Step 1: Write replacement and rollback RED tests**

Assert second upload changes DB to a new key, keeps the new file and deletes the old file only after commit. Add a test-only outer `@Transactional` service that calls upload then throws. After rollback, DB and old file must remain, the new file and audit must be absent. Invalid second upload must also preserve the old photo.

- [ ] **Step 2: Verify RED**

```powershell
.\mvnw.cmd -Dtest=ProfilFotografiControllerIntegrationTest -Djacoco.skip=true test
```

- [ ] **Step 3: Register transaction compensation**

Immediately after a new file is created, register one `TransactionSynchronization`:

```java
@Override
public void afterCommit() {
    if (storage.ozelFotografMi(oldKey)) safeDelete(oldKey);
}

@Override
public void afterCompletion(int status) {
    if (status != STATUS_COMMITTED) safeDelete(newKey);
}
```

If an exception occurs before synchronization registration, delete the newly created key and rethrow. `safeDelete` catches cleanup failure and logs a generic message without key/path. Never delete the old file before commit.

- [ ] **Step 4: Write DELETE RED tests**

Cover real delete (`204`, DB sentinel, old file removed after commit, one safe audit), no-op delete (`204`, no persistence/file/audit), inactive `403`, missing principal `404`, unauthenticated `401`, and outer transaction rollback retaining DB/file with no audit.

- [ ] **Step 5: Implement DELETE**

```java
@Transactional
@PreAuthorize("isAuthenticated()")
public void profilFotografimiSil(String personelId) {
    Personel personel = aktifPersoneliGetir(personelId);
    String oldKey = personel.getProfilFoto();
    if (!storage.ozelFotografMi(oldKey)) return;
    personel.setProfilFoto("def_pfp.png");
    auditService.logKaydetOrtakTransaction(
            AuditEylem.PROFIL_FOTOGRAFI_SILINDI,
            personel, personel.getKullaniciAdi(),
            "Profil fotoğrafı silindi.", personel);
    // Register oldKey cleanup only in afterCommit.
}
```

```java
@DeleteMapping("/fotograf")
public ResponseEntity<Void> profilFotografimiSil(Authentication authentication) {
    service.profilFotografimiSil(authentication.getName());
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 6: Verify compensation and DELETE GREEN**

```powershell
.\mvnw.cmd "-Dtest=ProfilFotografiDepolamaServiceTest,ProfilFotografiControllerIntegrationTest" -Djacoco.skip=true test
```

---

### Task 6: Existing response regression

**Files:** Existing profile, directory, dashboard and personnel-management integration tests.

- [ ] **Step 1: Add response propagation assertions**

After upload, assert the same UUID key is returned by `GET /api/profil`, visible `GET /api/ekip-dizini`, `GET /api/dashboard/ozet` and applicable HR/Admin personnel-management DTOs. Preserve existing visibility, pagination, filters and `yoneticinMi`. Also assert records without custom photos still return `def_pfp.png`.

- [ ] **Step 2: Run focused regression**

```powershell
.\mvnw.cmd "-Dtest=ProfilFotografiDepolamaServiceTest,ProfilFotografiControllerIntegrationTest,ProfilControllerIntegrationTest,EkipDiziniControllerIntegrationTest,DashboardControllerIntegrationTest,PersonelYonetimControllerIntegrationTest,SecurityHttpIntegrationTest" -Djacoco.skip=true test
```

Expected: zero failures/errors. Quoting the comma-separated `-Dtest` value avoids PowerShell parsing errors.

---

### Task 7: Documentation with renewed approval

**Files:** `API_SPEC.md`, `SECURITY.md`, `ROADMAP.md`, `PROGRESS.md`.

- [ ] **Step 1: Ask explicit permission immediately before Markdown edits**

State all four target files. Do not edit before approval.

- [ ] **Step 2: Update all approved documents atomically**

First try `apply_patch`. If it fails on Windows, do not retry it; use the approved PowerShell UTF-8 Python procedure. Validate every expected match and prepare every new document in memory before writing any file.

Document exact endpoints/statuses/headers/sentinel in `API_SPEC.md`; implemented defense layers and remaining antivirus/multi-instance risk in `SECURITY.md`; check only actually completed roadmap items; record real file/test results in `PROGRESS.md`.

- [ ] **Step 3: Verify strict UTF-8 and uniqueness**

For each file use `read_bytes().decode("utf-8")`; require U+FFFD count zero, each new heading once, old planned-only wording removed, no duplicate section and no accidental Turkish `?` replacement.

---

### Task 8: Final verification and handoff

- [ ] **Step 1: Re-run focused suite**

```powershell
.\mvnw.cmd "-Dtest=ProfilFotografiDepolamaServiceTest,ProfilFotografiControllerIntegrationTest,ProfilControllerIntegrationTest,EkipDiziniControllerIntegrationTest,DashboardControllerIntegrationTest,PersonelYonetimControllerIntegrationTest,SecurityHttpIntegrationTest" -Djacoco.skip=true test
```

Expected: `Failures: 0`, `Errors: 0`, `BUILD SUCCESS`.

- [ ] **Step 2: Run full backend suite and JaCoCo**

```powershell
.\mvnw.cmd test
```

Expected: zero failures/errors, `All coverage checks have been met.`, `BUILD SUCCESS`. If Docker/Testcontainers is unavailable, report it as an environment blocker and rerun after Docker is restored.

- [ ] **Step 3: Verify filesystem boundaries**

Confirm no upload under the repository, no test photo outside the configured OS temp root, no use of the development storage path by tests, no orphan from failed/rollback tests, and no physical `def_pfp.png` requirement.

- [ ] **Step 4: Report delivery**

Report changed backend files, request→validation→rewrite→disk→DB→audit flow, storage path, DB key semantics, GET/DELETE contracts, security controls, focused/full test counts, unrun checks, remaining antivirus/S3 risks, and frontend note: sentinel means initials; UUID means authenticated blob GET plus object URL cleanup. Do not imply commit or push.
