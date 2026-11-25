# Admin Personel Yetenek Yönetimi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif Admin’in, aktif PERSONEL/HR/YONETICI hedeflerinin bütün yetenek kümesini güvenli, fark tabanlı ve auditli biçimde yönetmesini sağlamak.

**Architecture:** Mevcut `ProfilService` self-service yetenek algoritması private ortak metoda ayrılır ve yeni Admin hedefli public use-case tarafından yeniden kullanılır. `HrPersonelController` yalnız HTTP koordinasyonu yapar; DB tabanlı Admin/target yetkisi `PersonelYonetimYetkiService`, transaction ve audit `ProfilService` içinde kalır.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security method security, Spring Data JPA, MySQL 8 Testcontainers, JUnit 5, MockMvc.

## Global Constraints

- Yeni endpoint: `PUT /api/hr/personel/{personelId}/yetenekler`.
- Request yalnız `{ "yetenekIdleri": number[] }` taşır; mevcut `YetenekUpdateRequest` yeniden kullanılır.
- Yalnız DB’de aktif `ADMIN` aktör izinlidir; JWT rolü tek başına yeterli değildir.
- Hedef yalnız aktif PERSONEL, HR veya YONETICI olabilir; öz-hedef ve bütün ADMIN hedefler yasaktır.
- Pasif hedef `403` alır; hedef yetkisi no-op kontrolünden önce uygulanır.
- Mevcut `PUT /api/profil/yetenekler` sözleşmesi ve yetkisi değişmez.
- Gerçek değişiklik `PROFIL_GUNCELLENDI`, Admin aktör, path hedefi ve exact `Güncellenen alanlar: yetenekler` detayıyla aynı transaction’da audit edilir.
- Frontend kapsam dışıdır. Git komutu çalıştırılmaz.
- `API_SPEC.md`, `SECURITY.md` ve `PROGRESS.md` yalnız implementasyon tamamlandıktan ve kullanıcı yeniden açık onay verdikten sonra değiştirilir.

---

### Task 1: Happy-path ve temel endpoint

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/AdminPersonelYetenekControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimYetkiService.java`

**Interfaces:**
- Consumes: `YetenekUpdateRequest`, `PersonelYetenekRepository.findAllByPersonelIdWithYetenek`, `YetenekRepository.findAllById`, `AuditService.logKaydetOrtakTransaction`.
- Produces: `void personelYetenekleriniAdminOlarakGuncelle(String aktorId, String hedefPersonelId, YetenekUpdateRequest request)` ve `PUT /api/hr/personel/{personelId}/yetenekler` → `204`.

- [ ] **Step 1: PERSONEL hedef happy-path RED testini yaz**

Gerçek MySQL verisinde Admin, hedef Personel ve üç yetenek oluştur. Hedefe başlangıçta iki ilişki ver; request ile birini kaldırıp iki yeni yetenek ekle. MockMvc ile yeni endpoint’i çağır ve `204`, exact final ID kümesi ve yalnız farkın uygulandığını doğrula.

Testin yakalayacağı production kırığı: endpoint/controller/service use-case’i yok.

- [ ] **Step 2: Hedefli RED’i çalıştır**

```powershell
.\mvnw.cmd "-Dtest=AdminPersonelYetenekControllerIntegrationTest#adminPersonelHedefininYetenekKumesiniDegistirir" "-Djacoco.skip=true" test
```

Beklenen: eksik mapping nedeniyle `204` yerine framework error status’u; failure endpoint yokluğundan gelmelidir.

- [ ] **Step 3: Minimal controller ve service iskeletini ekle**

Controller:

```java
@PutMapping("/{personelId}/yetenekler")
public ResponseEntity<Void> personelYetenekleriniGuncelle(
        @PathVariable String personelId,
        @Valid @RequestBody YetenekUpdateRequest request,
        Authentication authentication
) {
    profilService.personelYetenekleriniAdminOlarakGuncelle(
            authentication.getName(), personelId, request);
    return ResponseEntity.noContent().build();
}
```

`HrPersonelController` constructor’ına mevcut `ProfilService` bean’ini ekle. Service public metodunu `@Transactional` ve `@PreAuthorize("hasRole('ADMIN')")` ile ekle.

- [ ] **Step 4: DB tabanlı Admin ve hedef doğrulamasını ekle**

`PersonelYonetimYetkiService` içinde:

```java
public Personel aktifAdminAktoruGetir(String aktorId)
public void adminYetenekHedefiniDogrula(Personel aktor, Personel hedef)
```

İlk metot bulunamayan aktörde `404`, pasifte `403`, persisted rol Admin değilse `403` üretir. İkinci metot öz-hedefi, ADMIN hedefi ve pasif hedefi reddeder; yalnız PERSONEL/HR/YONETICI hedeflerini kabul eder.

- [ ] **Step 5: Mevcut set algoritmasını private ortak metoda ayır**

`ProfilService` içinde mevcut self-service metot aktifi yükledikten sonra ortak metoda `aktor=hedef=personel` ile gider. Admin metodu aktör/hedef doğrulamasından sonra aynı metoda farklı aktör/hedef ile gider.

Ortak metodun sonucu no-op ise audit yazma; gerçek değişiklikte:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.PROFIL_GUNCELLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Güncellenen alanlar: yetenekler",
        hedef
);
```

- [ ] **Step 6: Happy-path GREEN’i doğrula**

Aynı hedefli komutta `1/1`, sıfır failure/error ve `BUILD SUCCESS` beklenir.

---

### Task 2: Rol, hedef ve principal güvenliği

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/AdminPersonelYetenekControllerIntegrationTest.java`
- Modify when a RED proves necessary: `PersonelYonetimYetkiService.java`, `ProfilService.java`

**Interfaces:** Task 1’de eklenen public service ve endpoint.

- [ ] **Step 1: İzinli hedef rol matrisini test et**

Parameterized integration testte aktif Admin’in PERSONEL, HR ve YONETICI hedeflerine gerçek değişiklik uygulayabildiğini doğrula. Boş listeyle bütün ilişkilerin temizlenmesini ayrı davranış assertion’ı olarak kapsa.

- [ ] **Step 2: Yasak aktör rollerini test et**

HR, YONETICI ve PERSONEL tokenları endpoint’e çağrı yaptığında `403`; hedef ilişkileri ve audit sayısı değişmemeli.

- [ ] **Step 3: Öz-hedef, ADMIN hedef ve pasif hedef sırasını test et**

Her senaryoda request mevcut kümeyle aynı olmalı. Beklenen `403`; böylece yetki kontrolünün no-op karşılaştırmasından önce olduğu kanıtlanır. DB ilişkisi ve audit değişmemeli.

- [ ] **Step 4: Pasif/missing Admin, missing hedef ve tokensız isteği test et**

- Pasif Admin: `403`
- JWT principal DB’de yok: `404 Personel bulunamadı.`
- Hedef yok: `404 Personel bulunamadı.`
- Token yok: `401`

Her durumda ilişki/audit yan etkisizliğini doğrula.

- [ ] **Step 5: Güvenlik testlerini çalıştır ve yalnız gerçek RED’leri düzelt**

```powershell
.\mvnw.cmd "-Dtest=AdminPersonelYetenekControllerIntegrationTest" "-Djacoco.skip=true" test
```

Her yeni davranış için önce doğru failure gözlenir; production yalnız o failure’ı kapatacak kadar değiştirilir.

---

### Task 3: Validation, atomiklik ve no-op

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/AdminPersonelYetenekControllerIntegrationTest.java`
- Modify only if required by RED: `ProfilService.java`, `GlobalExceptionHandler.java`

**Interfaces:** Mevcut `YetenekUpdateRequest` Jakarta Validation ve ortak set algoritması.

- [ ] **Step 1: Biçimsel request validation testlerini ekle**

Literal JSON gövdeleriyle şunları doğrula:

- `yetenekIdleri: null` → `400`
- null eleman → `400`
- `0` ve negatif → `400`
- eksik/bozuk body → `400`

- [ ] **Step 2: Duplicate business validation testini ekle**

`[1, 1]` → `400`; mevcut hedef ilişkileri ve audit değişmemeli.

- [ ] **Step 3: Bilinmeyen ID atomiklik testini ekle**

Request bir mevcut ve bir bulunmayan ID içersin. Beklenen `404 Yetenek bulunamadı.`; başlangıç ilişkileri aynen kalmalı ve audit eklenmemeli.

- [ ] **Step 4: No-op testini ekle**

Aynı küme farklı sırada gönderildiğinde `204`; ilişki satırları ve audit sayısı değişmemeli.

- [ ] **Step 5: Validation/no-op suite’ini GREEN yap**

Ortak algoritma doğru ayrıldıysa production değişikliği gerekmemelidir. Bir test RED olursa yalnız ilgili doğrulama/hata mapping’i minimal düzeltilir.

---

### Task 4: Audit ve transaction rollback

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/AdminPersonelYetenekControllerIntegrationTest.java`
- Modify only if required by RED: `ProfilService.java`

**Interfaces:** `AuditService.logKaydetOrtakTransaction`, gerçek MySQL transaction.

- [ ] **Step 1: Güvenli audit testini ekle**

Gerçek değişiklikten sonra yalnız bir yeni audit satırı bekle:

- `eylemTipi = PROFIL_GUNCELLENDI`
- actor = Admin
- hedef = path personeli
- `kullaniciAdi = Admin.kullaniciAdi`
- `detay = Güncellenen alanlar: yetenekler`

Detayda yetenek adı/ID, token veya parola bulunmadığını doğrula.

- [ ] **Step 2: Dış transaction rollback test helper’ını test paketinde oluştur**

Ayrı public `@Transactional` test bean’i proxied Admin service metodunu çağırıp bilinçli `IllegalStateException` atsın. Exception sonrası hedef ilişkileri ve audit başlangıç durumuna dönmüş olmalı.

- [ ] **Step 3: Rollback testini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=AdminPersonelYetenekControllerIntegrationTest#disTransactionHataVerirseYeteneklerVeAuditBirlikteRollbackOlur" "-Djacoco.skip=true" test
```

Beklenen GREEN: gerçek MySQL’de ilişkiler ve audit birlikte rollback olur.

- [ ] **Step 4: Admin endpoint sınıfının tamamını çalıştır**

```powershell
.\mvnw.cmd "-Dtest=AdminPersonelYetenekControllerIntegrationTest" "-Djacoco.skip=true" test
```

Sıfır failure/error beklenir.

---

### Task 5: Regresyon ve bağımsız review

**Files:**
- Test only unless a regression exposes a feature bug.

- [ ] **Step 1: Odaklı regresyonu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=AdminPersonelYetenekControllerIntegrationTest,ProfilYetenekControllerIntegrationTest,PersonelYonetimControllerIntegrationTest,HrPersonelControllerIntegrationTest,EkipDiziniControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Mevcut self-service, personel yönetimi, ekip dizini ve security davranışlarında sıfır failure/error beklenir.

- [ ] **Step 2: Bağımsız read-only review yap**

Yetki sırası, DB rolü, pasif hedef, audit aktör/hedefi, no-op ve transaction atomikliği incelenir. Critical/Important bulgu varsa yeni RED testiyle düzeltilir.

---

### Task 6: Ürün belgeleri ve final doğrulama

**Files:**
- Modify after renewed user approval: `API_SPEC.md`
- Modify after renewed user approval: `SECURITY.md`
- Modify after renewed user approval: `PROGRESS.md`

- [ ] **Step 1: Kullanıcıdan üç ürün Markdown dosyası için açık onay al**

Onaydan önce hiçbir ürün belgesi değiştirilmez.

- [ ] **Step 2: Belgeleri kesin sözleşmeyle güncelle**

- API: endpoint, request, `204`, rol/hedef matrisi, validation/no-op/audit.
- Security: DB tabanlı aktif Admin, öz/ADMIN/pasif hedef koruması ve frontend’e güvenmeme.
- Progress: gerçek TDD ve test sonuçları.

Önce `apply_patch`; Windows satır sonu sorunu olursa kullanıcı onayına dayanarak yalnız üç hedef dosyada strict UTF-8 Python ile bütün içerikleri bellekte doğrulayıp birlikte yaz.

- [ ] **Step 3: Strict UTF-8/CRLF/tekillik kontrolü yap**

U+FFFD, mojibake, Türkçe harflerin `?` olması, duplicate bölüm ve eski metinler kontrol edilir.

- [ ] **Step 4: Tam backend ve JaCoCo doğrulamasını kullanıcı çalıştırır**

```powershell
.\mvnw.cmd test
```

Beklenen: sıfır failure/error, bütün JaCoCo eşikleri ve `BUILD SUCCESS`.

- [ ] **Step 5: Final read-only review ve teslim özeti**

Değişen dosyalar, controller → service → repository → audit akışı, test sayıları, çalıştırılamayan kontroller ve kalan riskler raporlanır.
