# HR Personel Güncelleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR ve Admin’in bir personelin yalnız request’te gönderilen alanlarını güvenli biçimde güncelleyebilmesini sağlamak.

**Architecture:** Alan varlığı `@JsonSetter` kullanan `PersonelGuncelleRequest` sınıfında izlenir. Controller yalnız HTTP sözleşmesini yönetir; doğrulama, ilişki kuralları, persistence ve audit `PersonelService` transaction’ında yürür.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, Jakarta Validation, Spring Security Method Security, JPA/Hibernate, MySQL 8 Testcontainers, JUnit 5, Mockito.

## Global Constraints

- Backend kodunu kullanıcı yazacaktır; agent her adımda kodu inceleyecektir.
- Agent git komutu çalıştırmayacaktır.
- Yeni kütüphane veya yeni servis katmanı eklenmeyecektir.
- Endpoint `PATCH /api/hr/personel/{personelId}` ve yetki `HR` veya `ADMIN` olacaktır.
- Gönderilmeyen alan korunacak; nullable alanın açık `null` değeri mevcut değeri temizleyecektir.
- Kullanıcı adı, personel ID, sicil, rol, parola, aktiflik ve işten çıkış tarihi bu akışta değişmeyecektir.

---

### Task 1: Alan varlığını izleyen request DTO

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/PersonelGuncelleRequest.java`
- Create: `backend/src/test/java/com/jforce/backend/PersonelGuncelleRequestTest.java`

**Interfaces:**
- Produces: `PersonelGuncelleRequest`
- Her alan için değer getter’ı, `@JsonSetter` setter’ı ve `is...GonderildiMi()` metodu üretir.
- Produces: `boolean herhangiBirAlanGonderildiMi()`

- [ ] DTO sınıfının üzerine şu açıklamayı yaz:

```java
// PATCH isteğinde JSON'da bulunmayan alan ile açıkça null gönderilen alanı
// ayırt edebilmek için record yerine alan varlığını takip eden sınıf kullanılır.
```

- [ ] Şu alanları ve karşılık gelen `...GonderildiMi` boolean değerlerini ekle:

```text
String ad
String soyad
LocalDate dogumTarihi
String ePosta
Integer departmanId
LocalDate isGirisTarihi
String yoneticiId
String telNo
String mezunOkul
String mezunBolum
Integer mezuniyetYili
```

- [ ] Her setter’da değeri ata ve ilgili gönderildi flag’ini `true` yap:

```java
@JsonSetter("telNo")
public void setTelNo(String telNo) {
    this.telNo = telNo;
    this.telNoGonderildiMi = true;
}
```

- [ ] `herhangiBirAlanGonderildiMi()` metodunda bütün flag’leri `||` ile birleştir.
- [ ] Null kabul eden biçim constraint’lerini alanlara uygula: isim uzunlukları, `@Email`, `@Past`, `@Positive`, `@PastOrPresent` ve entity kolon uzunlukları.
- [ ] Açık null ile eksik alan ayrımını kanıtlayan JSON testini yaz:

```java
PersonelGuncelleRequest request = objectMapper.readValue(
        """
        {"ad":"Yeni Ad","telNo":null}
        """,
        PersonelGuncelleRequest.class
);

assertTrue(request.isAdGonderildiMi());
assertEquals("Yeni Ad", request.getAd());
assertTrue(request.isTelNoGonderildiMi());
assertNull(request.getTelNo());
assertFalse(request.isSoyadGonderildiMi());
```

- [ ] DTO testini çalıştır ve sonucu kontrol et.

---

### Task 2: Audit ve repository sözleşmesi

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`

**Interfaces:**
- Produces: `AuditEylem.PERSONEL_GUNCELLENDI`
- Produces: `boolean existsByEPostaIgnoreCaseAndPersonelIdNot(String ePosta, String personelId)`

- [ ] Audit enum’una `PERSONEL_GUNCELLENDI` ekle.
- [ ] Hedef personelin kendi e-postasını conflict saymayan repository metodunu ekle:

```java
boolean existsByEPostaIgnoreCaseAndPersonelIdNot(
        String ePosta,
        String personelId
);
```

- [ ] Backend compile kontrolünü çalıştır.

---

### Task 3: Güncelleme service akışı

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`

**Interfaces:**
- Consumes: `PersonelGuncelleRequest`
- Produces: `void personelGuncelle(String aktorId, String hedefPersonelId, PersonelGuncelleRequest request)`

- [ ] Önce şu temel unit testleri yaz:
  - yalnız ad değişir ve diğer alanlar korunur,
  - boş request `GecersizIstekException` üretir,
  - `telNo: null` telefonu temizler,
  - aynı değerler audit üretmez,
  - başka personele ait e-posta `KaynakCakismasiException` üretir.

- [ ] Service metodunun güvenlik ve transaction sınırını ekle:

```java
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public void personelGuncelle(
        String aktorId,
        String hedefPersonelId,
        PersonelGuncelleRequest request
) {
    // Akış aşağıdaki adımlarla tamamlanır.
}
```

- [ ] Aktörü ve hedefi repository’den bul; yoklukları kontrollü `404` exception’ına çevir.
- [ ] `herhangiBirAlanGonderildiMi()` false ise `GecersizIstekException` fırlat.
- [ ] Yalnız gönderilmiş alanlarda explicit-null, trim, tarih ve biçim kurallarını uygula.
- [ ] E-postayı mevcut helper ile normalize et; hedef dışındaki conflict’i repository metoduyla kontrol et.
- [ ] Değişen alan adlarını `List<String>` içinde topla ve yalnız gerçekten farklı değerleri entity’ye yaz.
- [ ] İlişki alanlarından biri gönderilmişse etkili departmanı hesapla.
- [ ] Departman gerçekten değişmiş ve `yoneticiId` gönderilmemişse `400` üret.
- [ ] `yoneticiId: null` gönderilmişse yöneticiyi kaldır.
- [ ] Yönetici ID’si gönderilmişse yöneticinin:
  - hedefin kendisi olmadığını,
  - `calisiyorMu=true` olduğunu,
  - rolünün `YONETICI` olduğunu,
  - etkili departmanla aynı departmanda olduğunu doğrula.
- [ ] Hiç gerçek değişiklik yoksa audit ve flush yapmadan return et.
- [ ] `saveAndFlush` sırasında e-posta unique yarışını kontrollü `409` olarak çevir.
- [ ] Değişiklik varsa ortak transaction audit metodunu çağır:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.PERSONEL_GUNCELLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Güncellenen alanlar: " + String.join(", ", degisenAlanlar),
        hedef
);
```

- [ ] PersonelService unit test grubunu çalıştır.

---

### Task 4: Ortak yönetici doğrulamasını oluşturma akışına uygula

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`

**Interfaces:**
- Produces: Service içinde yalnız oluşturma ve güncellemenin kullandığı private yönetici doğrulama helper’ı.

- [ ] Güncellemedeki yönetici kurallarını private helper’a çıkar.
- [ ] `personelOlustur` içinde seçilen yöneticinin aktif ve yeni personelle aynı departmanda olduğunu aynı helper ile doğrula.
- [ ] Farklı departmandaki yöneticinin oluşturma sırasında reddedildiğini test et.
- [ ] Pasif yöneticinin oluşturma sırasında reddedildiğini test et.

---

### Task 5: Controller ve HTTP/MySQL doğrulaması

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `PersonelService.personelGuncelle(...)`
- Produces: `PATCH /api/hr/personel/{personelId}` → `204 No Content`

- [ ] Controller endpoint’ini ekle:

```java
@PatchMapping("/{personelId}")
public ResponseEntity<Void> personelGuncelle(
        @PathVariable String personelId,
        @Valid @RequestBody PersonelGuncelleRequest request,
        Authentication authentication
) {
    personelService.personelGuncelle(
            authentication.getName(),
            personelId,
            request
    );
    return ResponseEntity.noContent().build();
}
```

- [ ] HTTP/MySQL testlerinde şunları doğrula:
  - HR yalnız adı günceller; diğer DB alanları korunur.
  - `telNo: null` değeri temizler.
  - Departman değişikliği yönetici alanı yokken `400` döner.
  - Aynı departmandaki aktif yöneticiyle departman değişikliği kalıcı olur.
  - PERSONEL rolü `403` alır.
  - E-posta çakışması `409` döner.
  - Başarılı işlem doğru aktör/hedef ve alan adlarıyla audit oluşturur.
  - No-op istek `204` döner ve audit oluşturmaz.

- [ ] `HrPersonelControllerIntegrationTest` sınıfını çalıştır.

---

### Task 6: Sözleşme ve tam doğrulama

**Files:**
- Modify: `API_SPEC.md`
- Modify: `SECURITY.md`
- Modify: `ROADMAP.md`
- Modify: `PROGRESS.md`

- [ ] Endpoint, PATCH/null semantiği, departman-yönetici kuralı ve audit olayını belgelere işle.
- [ ] Tam backend test paketini çalıştır.
- [ ] Test sayısı, failures/errors/skipped ve build sonucunu `PROGRESS.md` içine yalnız gerçek çıktıdan sonra yaz.
- [ ] Kullanılmayan importları IDE/compiler uyarılarıyla kontrol et; yalnız bu çalışma sırasında oluşanları temizle.
