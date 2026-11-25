# HR Personel Pasifleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR/Admin tarafından rol kurallarına uygun personel pasifleştirme, yönetici ilişkilerini temizleme, refresh token iptali ve transaction içi audit akışını eklemek.

**Architecture:** `HrPersonelController` yalnız hedef ID ve JWT principal bilgisini `PersonelService` katmanına aktarır. Service persisted roller üzerinden yetki kontrollerini yapar; JPA managed entity dirty checking ile hedefi ve bağlı çalışanları günceller, token silme ve audit kaydını aynı transaction içinde yürütür.

**Tech Stack:** Java 21, Spring Boot, Spring Security Method Security, Spring Data JPA/Hibernate, MySQL 8, JUnit 5, Mockito, MockMvc, Testcontainers

## Global Constraints

- Backend kodunu öncelikle kullanıcı yazar; agent yazılan her anlamlı grubu inceler.
- Agent git komutu çalıştırmaz; plandaki git adımlarını yalnız kullanıcı uygular.
- Endpoint `PATCH /api/hr/personel/{personelId}/pasiflestir`, body yok, başarı `204 No Content`.
- HR/Admin `PERSONEL` ve `HR`; yalnız Admin `YONETICI` hedefi pasifleştirebilir.
- `ADMIN` hedef ve öz-hedef işlemleri daima yasaktır.
- Yetkili aktör için zaten pasif hedef idempotenttir; hiçbir yan etki oluşmaz.
- Yönetici pasifleştirilince aktif/pasif ayrımı olmadan bütün doğrudan çalışanlarda `yonetici=null` yapılır.
- Personel, çalışan ilişkileri, token silme ve audit aynı transaction içinde rollback olur.
- `SECURITY.md` kullanıcı tercihiyle local kalır ve commit kapsamına alınmaz.

---

## File Map

- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`: yeni audit olayı.
- `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`: doğrudan çalışan sorgusu.
- `backend/src/main/java/com/jforce/backend/service/PersonelService.java`: yetki ve transaction akışı.
- `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`: body’siz endpoint.
- `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`: iş kuralı unit testleri.
- `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`: HTTP/MySQL/rollback testleri.
- Doğrulama sonrası `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` ve local `SECURITY.md`.

---

### Task 1: Service sözleşmesi ve başarılı en küçük dilim

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`

**Interfaces:**
- Consumes: `RefreshTokenService.revokeAllForPersonel(Personel)` ve `AuditService.logKaydetOrtakTransaction(...)`
- Produces: `void personelPasiflestir(String aktorId, String hedefPersonelId)` ve `PERSONEL_PASIFE_ALINDI`

- [ ] **Step 1: İlk kırmızı unit testi yaz**

`PersonelServiceTest` içine HR’nin normal personeli pasifleştirdiğini doğrulayan test ekle:

```java
@Test
void hrNormalPersoneliPasiflestirirTokenlariIptalEderVeAuditKaydeder() {
    Personel hr = personelHazirla("hr-id", "hr", RolAdi.HR, true);
    Personel hedef = personelHazirla("hedef-id", "hedef", RolAdi.PERSONEL, true);
    when(personelRepository.findById("hr-id")).thenReturn(Optional.of(hr));
    when(personelRepository.findById("hedef-id")).thenReturn(Optional.of(hedef));

    personelService.personelPasiflestir("hr-id", "hedef-id");

    assertFalse(hedef.getCalisiyorMu());
    assertEquals(LocalDate.now(), hedef.getIsCikisTarihi());
    verify(refreshTokenService).revokeAllForPersonel(hedef);
    verify(auditService).logKaydetOrtakTransaction(
            eq(AuditEylem.PERSONEL_PASIFE_ALINDI), same(hr), eq("hr"),
            eq("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 0"),
            same(hedef));
}

private Personel personelHazirla(String id, String kullaniciAdi, RolAdi rolAdi, boolean aktif) {
    Rol rol = new Rol();
    rol.setRolAdi(rolAdi);
    Personel personel = new Personel();
    personel.setPersonelId(id);
    personel.setKullaniciAdi(kullaniciAdi);
    personel.setRol(rol);
    personel.setCalisiyorMu(aktif);
    return personel;
}
```

Gerekli importlar: `java.time.LocalDate`, `assertEquals`, `assertFalse`.

- [ ] **Step 2: Testin beklenen nedenle kırıldığını gör**

```powershell
cd backend
.\mvnw.cmd -Dtest=PersonelServiceTest#hrNormalPersoneliPasiflestirirTokenlariIptalEderVeAuditKaydeder test
```

Beklenen: metot ve enum henüz olmadığı için derleme başarısızdır.

- [ ] **Step 3: Audit enum değerini ekle**

```java
PERSONEL_GUNCELLENDI,
PERSONEL_PASIFE_ALINDI
```

- [ ] **Step 4: Service metodunun ilk sürümünü yaz**

```java
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public void personelPasiflestir(String aktorId, String hedefPersonelId) {
    Personel aktor = personelRepository.findById(aktorId)
            .orElseThrow(() -> new KaynakBulunamadiException("İşlemi yapan personel bulunamadı."));
    Personel hedef = personelRepository.findById(hedefPersonelId)
            .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

    hedef.setCalisiyorMu(false);
    hedef.setIsCikisTarihi(LocalDate.now());
    refreshTokenService.revokeAllForPersonel(hedef);
    auditService.logKaydetOrtakTransaction(
            AuditEylem.PERSONEL_PASIFE_ALINDI, aktor, aktor.getKullaniciAdi(),
            "Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 0", hedef);
}
```

`java.time.LocalDate` importunu ekle. Explicit `save` gerekmez; hedef transaction içindeki managed entity’dir.

- [ ] **Step 5: Odak testi yeniden çalıştır**

Aynı komutun sonucu PASS olmalıdır.

---

### Task 2: Rol kuralları, idempotency ve yönetici temizliği

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`

**Interfaces:**
- Consumes: Task 1 service metodu
- Produces: `List<Personel> findAllByYonetici(Personel yonetici)` ve tamamlanmış kurallar

- [ ] **Step 1: Şu unit testleri gerçek entity değişimleri ve interaction assertion’larıyla ekle**

```text
adminYoneticiyiPasiflestirinceAktifVePasifDogrudanCalisanlariTemizler
adminHrPersoneliniPasiflestirebilir
hrYoneticiyiPasiflestiremez
adminRolundekiHedefPasiflestirilemez
hrKendisiniPasiflestiremez
adminKendisiniPasiflestiremez
zatenPasifHedefCikisTarihiniKorurVeYanEtkiOlusturmaz
olmayanHedefKontrolluExceptionUretir
```

Yönetici başarı testinin çekirdeği:

```java
Personel admin = personelHazirla("admin-id", "admin", RolAdi.ADMIN, true);
Personel yonetici = personelHazirla("yonetici-id", "yonetici", RolAdi.YONETICI, true);
Personel aktif = personelHazirla("aktif-id", "aktif", RolAdi.PERSONEL, true);
Personel pasif = personelHazirla("pasif-id", "pasif", RolAdi.PERSONEL, false);
aktif.setYonetici(yonetici);
pasif.setYonetici(yonetici);
when(personelRepository.findById("admin-id")).thenReturn(Optional.of(admin));
when(personelRepository.findById("yonetici-id")).thenReturn(Optional.of(yonetici));
when(personelRepository.findAllByYonetici(yonetici)).thenReturn(List.of(aktif, pasif));

personelService.personelPasiflestir("admin-id", "yonetici-id");

assertNull(aktif.getYonetici());
assertNull(pasif.getYonetici());
assertFalse(yonetici.getCalisiyorMu());
verify(auditService).logKaydetOrtakTransaction(
        eq(AuditEylem.PERSONEL_PASIFE_ALINDI), same(admin), eq("admin"),
        eq("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 2"),
        same(yonetici));
```

Yasak testlerinde `AccessDeniedException`, eksik hedefte `KaynakBulunamadiException` bekle; token ve audit için `verifyNoInteractions` kullan. İdempotent testte eski çıkış tarihinin korunduğunu ve `findAllByYonetici`, token, audit çağrılarının olmadığını doğrula.

- [ ] **Step 2: Unit test grubunun kırmızı olduğunu doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelServiceTest test
```

Beklenen: eksik repository metodu/iş kuralları nedeniyle başarısızdır.

- [ ] **Step 3: Repository metodunu ekle**

```java
List<Personel> findAllByYonetici(Personel yonetici);
```

- [ ] **Step 4: Service metodunu tamamla**

Task 1 metodundaki entity yüklemelerinden sonra ve yan etkilerden önce şu sırayı uygula:

```java
if (Objects.equals(aktorId, hedefPersonelId)) {
    throw new AccessDeniedException("Kullanıcı kendisini pasife alamaz.");
}

RolAdi hedefRol = hedefPersonel.getRol().getRolAdi();
if (hedefRol == RolAdi.ADMIN) {
    throw new AccessDeniedException("Admin rolündeki personel pasife alınamaz.");
}
if (hedefRol == RolAdi.YONETICI && aktor.getRol().getRolAdi() != RolAdi.ADMIN) {
    throw new AccessDeniedException("Yöneticiyi yalnız Admin pasife alabilir.");
}
if (!Boolean.TRUE.equals(hedefPersonel.getCalisiyorMu())) {
    return;
}

int kaldirilanYoneticiSayisi = 0;
if (hedefRol == RolAdi.YONETICI) {
    List<Personel> dogrudanCalisanlar = personelRepository.findAllByYonetici(hedefPersonel);
    dogrudanCalisanlar.forEach(personel -> personel.setYonetici(null));
    kaldirilanYoneticiSayisi = dogrudanCalisanlar.size();
}

hedefPersonel.setCalisiyorMu(false);
hedefPersonel.setIsCikisTarihi(LocalDate.now());
refreshTokenService.revokeAllForPersonel(hedefPersonel);
auditService.logKaydetOrtakTransaction(
        AuditEylem.PERSONEL_PASIFE_ALINDI, aktor, aktor.getKullaniciAdi(),
        "Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: "
                + kaldirilanYoneticiSayisi,
        hedefPersonel);
```

Öz-hedef ve rol kontrolleri idempotent dönüşten önce kalmalıdır.

- [ ] **Step 5: Bütün service unit testlerini çalıştır**

```powershell
.\mvnw.cmd -Dtest=PersonelServiceTest test
```

Beklenen: PASS.

---

### Task 3: Controller ve HTTP/MySQL davranışı

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `personelPasiflestir(String, String)`
- Produces: body’siz `PATCH /api/hr/personel/{personelId}/pasiflestir`

- [ ] **Step 1: İlk HTTP testini yaz ve 404 ile kırıldığını gör**

```java
@Test
void hrNormalPersoneliPasiflestirir() throws Exception {
    Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
    Personel hedef = testPersoneliKaydet(
            "pasif.hedef", "pasif.hedef@test.local", "Hedef", personelRolu, departman);
    String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

    mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", hedef.getPersonelId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isNoContent());

    Personel kayit = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
    assertFalse(kayit.getCalisiyorMu());
    assertEquals(LocalDate.now(), kayit.getIsCikisTarihi());
    assertEquals(AuditEylem.PERSONEL_PASIFE_ALINDI,
            auditLogRepository.findAll().getFirst().getEylemTipi());
}
```

```powershell
.\mvnw.cmd -Dtest=HrPersonelControllerIntegrationTest#hrNormalPersoneliPasiflestirir test
```

- [ ] **Step 2: Controller metodunu ekle**

```java
@PatchMapping("/{personelId}/pasiflestir")
public ResponseEntity<Void> personelPasiflestir(
        @PathVariable String personelId,
        Authentication authentication) {
    personelService.personelPasiflestir(authentication.getName(), personelId);
    return ResponseEntity.noContent().build();
}
```

Request body ve DTO ekleme.

- [ ] **Step 3: İlk HTTP testini yeşile getir**

Aynı odak komutun sonucu PASS olmalıdır.

- [ ] **Step 4: HTTP rol matrisi ve idempotency testlerini ekle**

Aşağıdaki her metotta hem status hem DB yan etkisini doğrula:

```text
adminYoneticiyiPasiflestirinceCalisanTokenVeAuditBirlikteGuncellenir -> 204
hrYoneticiyiPasiflestiremez -> 403, veri değişmez
adminRolundekiHedefPasiflestirilemez -> 403, veri değişmez
hrKendisiniPasiflestiremez -> 403
adminKendisiniPasiflestiremez -> 403
personelRoluEndpointErisemez -> 403
zatenPasifHedef204DonerVeYanEtkiOlusturmaz -> eski tarih/token korunur, audit yok
olmayanHedef404Doner -> 404, audit yok
```

Admin-yönetici başarı testinde bir aktif ve bir pasif çalışanı hedef yöneticiye bağla; `RefreshTokenService.createToken(yonetici)` ile token üret. Request sonrasında iki çalışanın da yöneticisinin null, token sayısının 0, audit detay sayısının 2 olduğunu doğrula:

```java
assertNull(veritabanindakiAktifCalisan.getYonetici());
assertNull(veritabanindakiPasifCalisan.getYonetici());
assertEquals(0L, refreshTokenRepository.count());
AuditLog log = auditLogRepository.findAll().getFirst();
assertEquals(AuditEylem.PERSONEL_PASIFE_ALINDI, log.getEylemTipi());
assertEquals(admin.getPersonelId(), log.getPersonel().getPersonelId());
assertEquals(yonetici.getPersonelId(), log.getHedefPersonel().getPersonelId());
assertEquals("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 2", log.getDetay());
```

İdempotent testte tokenı hedef aktifken üret, sonra hedefi eski çıkış tarihiyle pasif kaydet. Request sonrasında token sayısı 1, audit sayısı 0 ve tarih eski değer olmalıdır.

- [ ] **Step 5: HR integration test sınıfını çalıştır**

```powershell
.\mvnw.cmd -Dtest=HrPersonelControllerIntegrationTest test
```

Beklenen: PASS.

---

### Task 4: Ortak transaction rollback kanıtı

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`

**Interfaces:**
- Consumes: service transaction’ı
- Produces: hedef, çalışan, token ve audit için birlikte rollback kanıtı

- [ ] **Step 1: Test-only Spring bean ekle**

Sınıfa `@Import(HrPersonelControllerIntegrationTest.RollbackTestConfiguration.class)` ekle ve aşağıdaki bean’i test sınıfının içine yaz:

```java
@TestConfiguration(proxyBeanMethods = false)
static class RollbackTestConfiguration {
    @Bean
    PersonelPasiflestirmeRollbackTestService rollbackTestService(PersonelService service) {
        return new PersonelPasiflestirmeRollbackTestService(service);
    }
}

static class PersonelPasiflestirmeRollbackTestService {
    private final PersonelService service;

    PersonelPasiflestirmeRollbackTestService(PersonelService service) {
        this.service = service;
    }

    @Transactional
    public void pasiflestirVeHataFirlat(String aktorId, String hedefId) {
        service.personelPasiflestir(aktorId, hedefId);
        throw new IllegalStateException("Rollback davranışını doğrulamak için test hatası.");
    }
}
```

Gerekli importlar: `TestConfiguration`, `Bean`, `Import`, `Transactional`, `PersonelService`, `WithMockUser`.

- [ ] **Step 2: Rollback testini yaz**

`@WithMockUser(roles = "ADMIN")` ile admin aktör, yönetici hedef, ona bağlı çalışan ve bir refresh token hazırla. Yardımcı metodun `IllegalStateException` attığını doğruladıktan sonra DB’den tekrar yükleyip şu assertion’ları yap:

```java
assertTrue(veritabanindakiYonetici.getCalisiyorMu());
assertNull(veritabanindakiYonetici.getIsCikisTarihi());
assertEquals(yonetici.getPersonelId(),
        veritabanindakiCalisan.getYonetici().getPersonelId());
assertEquals(1L, refreshTokenRepository.count());
assertEquals(0L, auditLogRepository.count());
```

- [ ] **Step 3: Rollback testini çalıştır**

```powershell
.\mvnw.cmd -Dtest=HrPersonelControllerIntegrationTest#disTransactionHataVerirsePasiflikCalisanTokenVeAuditBirlikteRollbackOlur test
```

Beklenen: PASS.

---

### Task 5: Tam doğrulama, belgeler ve kullanıcı commitleri

**Files:**
- Modify: `API_SPEC.md`
- Modify: `ROADMAP.md`
- Modify: `PROGRESS.md`
- Modify locally only: `SECURITY.md`

**Interfaces:**
- Consumes: doğrulanmış backend davranışı
- Produces: kodla tutarlı proje belgeleri

- [ ] **Step 1: Bütün backend testlerini çalıştır**

```powershell
cd backend
.\mvnw.cmd test
```

Beklenen: `BUILD SUCCESS`, failures/errors/skips sıfır. Gerçek test sayısını çıktıdan kaydet.

- [ ] **Step 2: `API_SPEC.md` Personel tablosuna endpoint’i ekle**

```markdown
| PATCH  | `/api/hr/personel/{personelId}/pasiflestir` | HR, admin; hedef rol kuralları uygulanır |
```

Aynı bölüme rol matrisi, öz-hedef/Admin yasağı, 204 idempotency, yönetici temizliği, token iptali, audit ve 15 dakikalık access-token penceresini ekle.

- [ ] **Step 3: `ROADMAP.md` ve `PROGRESS.md` güncelle**

`HR personeli pasife alma` maddesini `[x]` yap; Aşama 1 durumundan pasifleştirme bekliyor ifadesini çıkar. Ekip dizini uygulanmadığı için ilgili dizin maddesini açık bırak. `PROGRESS.md` yeni gün kaydında uygulanan akışı ve Step 1’deki gerçek test sayısını yaz.

- [ ] **Step 4: Local `SECURITY.md` güncelle**

Hedef rol kuralları, refresh-token iptali, en fazla 15 dakikalık access-token penceresi ve ortak rollback davranışını planlanan bölümden uygulanmış güvenlik davranışına taşı. Dosyayı commit kapsamına alma.

- [ ] **Step 5: Belge tutarlılığını kontrol et**

```powershell
rg -n "pasif|pasifleştir|PERSONEL_PASIFE_ALINDI" API_SPEC.md ROADMAP.md PROGRESS.md SECURITY.md
```

Beklenen: dört belge aynı endpoint ve rol matrisini anlatır; ekip dizini hâlâ beklemededir.

- [ ] **Step 6: Kullanıcı commitleri**

Agent git çalıştırmaz. Kullanıcı isterse Task 1–4’ü anlamlı kod/test commitlerine, doğrulanmış belgeleri ayrı docs commitine böler; local `SECURITY.md` hiçbir staging komutuna eklenmez.
