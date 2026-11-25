# Tatil Takvimi ve İş Günü Hesaplama Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Bu repository'de backend geliştirmesi varsayılan olarak kullanıcı tarafından yapılır; agent ancak kullanıcı ayrıca backend yazma izni verirse kodu uygular.

**Goal:** Şirket genelindeki tatil takvimini güvenli biçimde yönetmek, tam/yarım gün tatilleri hesaba katan ortak iş günü hesaplayıcısını kurmak ve PERSONEL/HR için salt-okunur önizleme endpoint'i sunmak.

**Architecture:** `Tatil` JPA entity'si ve `TatilRepository` takvim verisini tutar. `TatilTakvimiService` aktör/yetki, CRUD, transaction ve audit kurallarını; saf hesap sorumluluğuna yakın `IsGunuHesaplamaService` ise tarih aralığı ve gün katkılarını yönetir. HTTP katmanı iki ince controller ve use-case DTO'larından oluşur; entity doğrudan response yapılmaz.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring MVC, Spring Security method security, Spring Data JPA/Hibernate, MySQL 8 Testcontainers, JUnit 5, Mockito, Maven Wrapper.

**Spec:** `docs/superpowers/specs/2026-08-22-tatil-takvimi-is-gunu-hesaplama-design.md`

## Global Constraints

- Frontend dosyaları kapsam dışıdır.
- Git komutu çalıştırılmaz; her görev Git yerine kullanıcı inceleme kapısıyla biter.
- `API_SPEC.md`, `SECURITY.md`, `PROGRESS.md` ve `ROADMAP.md` ancak kullanıcı ayrıca belge yazma onayı verirse değiştirilir.
- Yeni dependency eklenmez; mevcut Java/Spring/JPA/Testcontainers araçları kullanılır.
- Controller yalnız HTTP sözleşmesini taşır; iş kuralları, DB aktörü ve rol doğrulaması service katmanında kalır.
- JWT rol kontrolüne ek olarak principal personel veritabanından yüklenir, aktifliği ve gerekli yerde DB rolü yeniden doğrulanır.
- Mutation ile audit aynı transaction içinde tamamlanır; read-only işlemler audit üretmez.
- Ham exception, SQL ayrıntısı, entity veya hassas personel alanı response'a çıkmaz.
- Tarih aralıkları başlangıç ve bitiş dahil en fazla 366 takvim günüdür; `ChronoUnit.DAYS.between(baslangic, bitis) > 365` geçersizdir.
- Testcontainers/Docker erişim hatası davranışsal RED sayılmaz; Docker düzeltildikten sonra aynı komut yeniden çalıştırılır.
- PowerShell'de virgüllü `-Dtest` değeri mutlaka tırnak içinde verilir.
- Gerçek izin talebi oluşturma, listeleme, yönetici onayı ve Admin kararı bu planın kapsamı değildir. Tasarım belgesinin 11.1 bölümündeki kararlar sonraki teslimde uygulanacaktır.

---

## File Responsibility Map

### Yeni production dosyaları

- `backend/src/main/java/com/jforce/backend/model/entity/Tatil.java`: tatil persistence modeli, tarih unique constraint'i ve optimistic `version`.
- `backend/src/main/java/com/jforce/backend/model/enums/TatilKategori.java`: `RESMI_SABIT`, `RESMI_DEGISKEN`, `SIRKET`.
- `backend/src/main/java/com/jforce/backend/model/enums/TatilGunSuresi.java`: `TAM_GUN`, `YARIM_GUN`.
- `backend/src/main/java/com/jforce/backend/model/dto/request/TatilKaydetRequest.java`: POST/PATCH için istemcinin belirleyebildiği dört alan.
- `backend/src/main/java/com/jforce/backend/model/dto/response/TatilResponse.java`: güvenli takvim response'u.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IsGunuOnizlemeResponse.java`: yalnız `BigDecimal isGunuSayisi`.
- `backend/src/main/java/com/jforce/backend/repository/TatilRepository.java`: aralık sorgusu ve tarih benzersizliği kontrolleri.
- `backend/src/main/java/com/jforce/backend/service/IsGunuHesaplamaService.java`: hafta sonu/tatil katkısı ve tarih aralığı doğrulaması.
- `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`: takvim okuma/yönetme, DB rolü, transaction ve audit.
- `backend/src/main/java/com/jforce/backend/service/IzinOnizlemeService.java`: principal/rol doğrulaması ve ortak hesaplayıcıya delegasyon.
- `backend/src/main/java/com/jforce/backend/controller/TatilController.java`: `/api/holidays` GET/POST/PATCH/DELETE.
- `backend/src/main/java/com/jforce/backend/controller/IzinHesaplamaController.java`: `/api/leave-requests/workday-preview` GET.
- `backend/src/main/java/com/jforce/backend/config/ZamanConfig.java`: test edilebilir `Clock` bean'i.

### Yeni migration ve test dosyaları

- `backend/db/manual-migrations/2026-08-22-change-izin-is-gunu-to-decimal.sql`: mevcut izin günlerini `DECIMAL(4,1)` yapar.
- `backend/db/manual-migrations/2026-08-22-create-tatil-table.sql`: tatil tablosunu unique tarih ve version ile oluşturur.
- `backend/src/test/java/com/jforce/backend/IzinIsGunuMigrationIntegrationTest.java`: tamsayı verinin `x.0` olarak korunmasını doğrular.
- `backend/src/test/java/com/jforce/backend/TatilTableMigrationIntegrationTest.java`: tablo, unique constraint, version ve tekrar çalıştırılabilirlik kontrolü.
- `backend/src/test/java/com/jforce/backend/IsGunuHesaplamaServiceTest.java`: saf hesaplama ve tek repository sorgusu.
- `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`: listeleme ve CRUD HTTP/güvenlik/transaction matrisi.
- `backend/src/test/java/com/jforce/backend/TatilTakvimiServiceTest.java`: unique constraint yarışlarının kontrollü 409'a çevrilmesi.
- `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java`: önizleme, rol matrisi, doğrulama ve auditsizlik.

### Değişecek production/test dosyaları

- `backend/src/main/java/com/jforce/backend/model/entity/IzinBilgileri.java`: `isGunuSayisi` `Integer` → `BigDecimal`, `precision=4, scale=1`.
- `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`: toplam dönüşü `BigDecimal`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java`: iş günü response'u `BigDecimal`.
- `backend/src/main/java/com/jforce/backend/model/dto/response/ProfilBilgileriResponse.java`: `kalanIzin` `BigDecimal`.
- `backend/src/main/java/com/jforce/backend/service/ProfilService.java`: izin hakkı eksi kullanılan izin hesabı ondalıklı yapılır.
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`: üç tatil mutation olayı.
- `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java`: yarım gün kullanılan izin regresyonu.

`SecurityConfig.java` değiştirilmez: mevcut `anyRequest().authenticated()` dış sınırı yeterlidir; ayrıntılı rol ve DB aktörü kontrolleri service method security içinde uygulanır.

---

### Task 1: İzin Günlerini Ondalıklı Veri Tipine Geçir

**Files:**
- Create: `backend/db/manual-migrations/2026-08-22-change-izin-is-gunu-to-decimal.sql`
- Create: `backend/src/test/java/com/jforce/backend/IzinIsGunuMigrationIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/entity/IzinBilgileri.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/IzinBilgileriRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/IzinTalepResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/response/ProfilBilgileriResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`
- Modify/Test: `backend/src/test/java/com/jforce/backend/ProfilControllerIntegrationTest.java`

**Interfaces:**
- Produces: `BigDecimal IzinBilgileri.getIsGunuSayisi()`, `void setIsGunuSayisi(BigDecimal)`, `BigDecimal onaylanmisIzinIsGunuToplami(String, TalepDurum)`.
- Produces: profil JSON'unda sayısal `kalanIzin`; örnek `15.5`.

- [ ] **Step 1: Migration ve profil regresyon testlerini önce RED olarak yaz**

`IzinIsGunuMigrationIntegrationTest` bağımsız MySQL container'ında önce `INT` kolonlu tablo ve `14` değerini oluşturmalı; scripti çalıştırdıktan sonra şu üç assertion'ı yapmalıdır:

```java
assertEquals("decimal", kolonTipi(statement, "izin_bilgileri", "is_gunu_sayisi"));
assertEquals(4, numericPrecision(statement, "izin_bilgileri", "is_gunu_sayisi"));
assertEquals(1, numericScale(statement, "izin_bilgileri", "is_gunu_sayisi"));
assertEquals(new BigDecimal("14.0"), tekDecimal(statement,
        "SELECT is_gunu_sayisi FROM izin_bilgileri WHERE izin_id = 'izin-1'"));
```

Script henüz olmadığı için ilk çalışma script bulunamadı hatasıyla RED olmalıdır. `ProfilControllerIntegrationTest` içine ayrıca onaylı `3.5` gün için `$.kalanIzin == 16.5` testi eklenmelidir.

- [ ] **Step 2: Hedefli RED komutlarını çalıştır**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd "-Dtest=IzinIsGunuMigrationIntegrationTest,ProfilControllerIntegrationTest#yarimGunOnayliIzinKalanBakiyeyiOndalikliAzaltir" "-Djacoco.skip=true" test
```

Expected: migration dosyası yokluğu ve/veya `Integer` sözleşmesi nedeniyle test/compile başarısızlığı; Docker erişim hatası geçerli RED değildir.

- [ ] **Step 3: Kontrollü SQL dönüşümünü ekle**

Migration'ın veri değiştiren çekirdeği şu olmalıdır:

```sql
ALTER TABLE izin_bilgileri
    MODIFY COLUMN is_gunu_sayisi DECIMAL(4,1) NOT NULL;
```

Dosya ayrıca çalıştırma öncesi tablo/kolon kontrol sorgularını, dönüşüm sonrası `information_schema.columns` doğrulamasını ve geri dönüşün ondalık veriyi kaybettireceğine dair açık rollback uyarısını içermelidir. Test scripti iki defa çalıştırıp `14.0` değerinin korunduğunu doğrulamalıdır.

- [ ] **Step 4: Java tip zincirini uçtan uca `BigDecimal` yap**

```java
@Column(name = "is_gunu_sayisi", nullable = false, precision = 4, scale = 1)
private BigDecimal isGunuSayisi;
```

Repository imzası:

```java
BigDecimal onaylanmisIzinIsGunuToplami(
        @Param("personelId") String personelId,
        @Param("durumKodu") TalepDurum durumKodu
);
```

JPQL `COALESCE(SUM(i.isGunuSayisi), 0.0)` kullanmalıdır. `IzinTalepResponse.isGunuSayisi` ve `ProfilBilgileriResponse.kalanIzin` `BigDecimal` olmalıdır. Profil hesabı:

```java
BigDecimal kalanIzin = null;
if (rolAdi == RolAdi.PERSONEL || rolAdi == RolAdi.HR) {
    BigDecimal kullanilanIzin = izinBilgileriRepository
            .onaylanmisIzinIsGunuToplami(personelId, TalepDurum.ONAYLANDI);
    kalanIzin = BigDecimal.valueOf(personel.getYillikIzinHakki())
            .subtract(kullanilanIzin)
            .setScale(1);
}
```

- [ ] **Step 5: Hedefli testleri GREEN yap ve mevcut profil beklentilerini güncelle**

Mevcut `20`/`16` JSON assertion'ları sayısal olarak korunabilir; yeni assertion kesin olarak `16.5` istemelidir. Ardından:

```powershell
.\mvnw.cmd "-Dtest=IzinIsGunuMigrationIntegrationTest,ProfilControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: iki sınıfta `Failures: 0, Errors: 0`.

- [ ] **Step 6: İnceleme kapısı**

Kullanıcıya migration'ın mevcut `14` değerini `14.0` koruduğu, Java zincirinde hiçbir `Math.toIntExact`/`intValue` kalmadığı ve değişen dosyalar bildirilir. Git çalıştırılmaz.

---

### Task 2: Tatil Persistence Modeli ve Ortak İş Günü Hesaplayıcısı

**Files:**
- Create: `backend/db/manual-migrations/2026-08-22-create-tatil-table.sql`
- Create: `backend/src/test/java/com/jforce/backend/TatilTableMigrationIntegrationTest.java`
- Create: `backend/src/main/java/com/jforce/backend/model/entity/Tatil.java`
- Create: `backend/src/main/java/com/jforce/backend/model/enums/TatilKategori.java`
- Create: `backend/src/main/java/com/jforce/backend/model/enums/TatilGunSuresi.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/TatilRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/service/IsGunuHesaplamaService.java`
- Test: `backend/src/test/java/com/jforce/backend/IsGunuHesaplamaServiceTest.java`

**Interfaces:**
- Produces: `List<Tatil> findAllByTarihBetweenOrderByTarihAscTatilIdAsc(LocalDate, LocalDate)`.
- Produces: `BigDecimal isGunuSayisiniHesapla(LocalDate baslangicTarihi, LocalDate bitisTarihi)`.

- [ ] **Step 1: Hesaplama testlerini eksik sınıflar nedeniyle RED yaz**

`IsGunuHesaplamaServiceTest` Mockito ile repository'yi mock'lamalı ve şu kesin sonuçları kapsamalıdır:

```java
assertEquals(new BigDecimal("5.0"), service.isGunuSayisiniHesapla(
        LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 28)));
assertEquals(new BigDecimal("0.0"), service.isGunuSayisiniHesapla(
        LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 30)));
```

Çarşamba tam gün ve perşembe yarım gün tatil verildiğinde pazartesi-cuma toplamı `3.5`; hafta sonu yarım gün tatili toplamı değiştirmemelidir. Her başarılı hesapta:

```java
verify(tatilRepository, times(1))
        .findAllByTarihBetweenOrderByTarihAscTatilIdAsc(baslangic, bitis);
verifyNoMoreInteractions(tatilRepository);
```

Ters aralık ve 367 günlük dahil aralık `GecersizIstekException` atmalı, repository hiç çağrılmamalıdır.

- [ ] **Step 2: RED komutunu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=IsGunuHesaplamaServiceTest" "-Djacoco.skip=true" test
```

Expected: `Tatil`, enum, repository ve service bulunamadığı için test compile RED.

- [ ] **Step 3: Enum, entity ve repository'yi minimal biçimde ekle**

Entity çekirdeği:

```java
@Entity
@Table(name = "tatil", uniqueConstraints =
        @UniqueConstraint(name = "uk_tatil_tarih", columnNames = "tarih"))
public class Tatil {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tatil_id")
    private Integer tatilId;

    @Column(name = "ad", nullable = false, length = 100)
    private String ad;

    @Column(name = "tarih", nullable = false)
    private LocalDate tarih;

    @Enumerated(EnumType.STRING)
    @Column(name = "kategori", nullable = false, length = 30)
    private TatilKategori kategori;

    @Enumerated(EnumType.STRING)
    @Column(name = "gun_suresi", nullable = false, length = 20)
    private TatilGunSuresi gunSuresi;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
```

`TatilRepository` yalnız şu an gereken metotları taşımalıdır:

```java
List<Tatil> findAllByTarihBetweenOrderByTarihAscTatilIdAsc(
        LocalDate baslangicTarihi, LocalDate bitisTarihi);
boolean existsByTarih(LocalDate tarih);
boolean existsByTarihAndTatilIdNot(LocalDate tarih, Integer tatilId);
```

- [ ] **Step 4: Tatil tablosu migration'ını ve testini ekle**

```sql
CREATE TABLE IF NOT EXISTS tatil (
    tatil_id INT NOT NULL AUTO_INCREMENT,
    ad VARCHAR(100) NOT NULL,
    tarih DATE NOT NULL,
    kategori VARCHAR(30) NOT NULL,
    gun_suresi VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (tatil_id),
    CONSTRAINT uk_tatil_tarih UNIQUE (tarih)
);
```

Migration testi tablo/kolon tiplerini, `version NOT NULL DEFAULT 0`, aynı tarihte ikinci insert'in constraint hatası verdiğini ve scriptin ikinci çalışmasında ilk satırın korunduğunu doğrulamalıdır.

- [ ] **Step 5: Hesaplayıcıyı minimal algoritmayla uygula**

```java
@Service
public class IsGunuHesaplamaService {
    private static final BigDecimal TAM_GUN = new BigDecimal("1.0");
    private static final BigDecimal YARIM_GUN = new BigDecimal("0.5");
    private final TatilRepository tatilRepository;

    @Transactional(readOnly = true)
    public BigDecimal isGunuSayisiniHesapla(LocalDate baslangic, LocalDate bitis) {
        tarihAraliginiDogrula(baslangic, bitis);
        Map<LocalDate, TatilGunSuresi> tatiller = tatilRepository
                .findAllByTarihBetweenOrderByTarihAscTatilIdAsc(baslangic, bitis)
                .stream().collect(Collectors.toMap(Tatil::getTarih, Tatil::getGunSuresi));

        BigDecimal toplam = BigDecimal.ZERO.setScale(1);
        for (LocalDate gun = baslangic; !gun.isAfter(bitis); gun = gun.plusDays(1)) {
            if (gun.getDayOfWeek() == DayOfWeek.SATURDAY
                    || gun.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            TatilGunSuresi sure = tatiller.get(gun);
            if (sure == TatilGunSuresi.TAM_GUN) continue;
            toplam = toplam.add(sure == TatilGunSuresi.YARIM_GUN ? YARIM_GUN : TAM_GUN);
        }
        return toplam.setScale(1);
    }
}
```

Doğrulama null, ters aralık ve `ChronoUnit.DAYS.between(baslangic, bitis) > 365` için kontrollü `GecersizIstekException` üretmelidir.

- [ ] **Step 6: Unit ve migration testlerini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=IsGunuHesaplamaServiceTest,TatilTableMigrationIntegrationTest" "-Djacoco.skip=true" test
```

Expected: tüm hesap ve schema testleri geçer; repository başarılı hesap başına tam bir kez çağrılır.

- [ ] **Step 7: İnceleme kapısı**

Entity'nin response olarak kullanılmadığı, N+1/gün başına sorgu bulunmadığı ve yeni dependency eklenmediği raporlanır.

---

### Task 3: Tüm Aktif Roller İçin Tatil Listeleme Endpoint'i

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/TatilResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/TatilController.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`

**Interfaces:**
- Produces: `List<TatilResponse> tatilleriListele(String aktorId, LocalDate baslangicTarihi, LocalDate bitisTarihi)`.
- HTTP: `GET /api/holidays?baslangicTarihi=2026-01-01&bitisTarihi=2026-12-31` → `200` JSON array.

- [ ] **Step 1: Listeleme HTTP testlerini RED yaz**

Test verisi karışık sırada kaydedilmeli; response tarih ve ID sırasıyla gelmelidir:

```java
mockMvc.perform(get("/api/holidays")
        .param("baslangicTarihi", "2026-08-01")
        .param("bitisTarihi", "2026-08-31")
        .header(AUTHORIZATION, "Bearer " + token(aktifAktor, rolAdi)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$[0].ad").value("Zafer Bayramı"))
    .andExpect(jsonPath("$[0].kategori").value("RESMI_SABIT"))
    .andExpect(jsonPath("$[0].gunSuresi").value("TAM_GUN"))
    .andExpect(jsonPath("$[0].version").doesNotExist());
```

`@EnumSource(RolAdi.class)` ile dört aktif rol `200`; pasif aktör `403`; missing principal `404`; token yok `401`; eksik/bozuk/ters/367 günlük aralık `400`; boş aralık `[]`; audit sayısı her read için `0` olmalıdır.

- [ ] **Step 2: 404 RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#tumAktifRollerTarihAraligindakiTatilleriSiraliGorur" "-Djacoco.skip=true" test
```

Expected: endpoint olmadığı için `404`.

- [ ] **Step 3: DTO, service ve ince controller'ı ekle**

```java
public record TatilResponse(
        Integer tatilId,
        String ad,
        LocalDate tarih,
        TatilKategori kategori,
        TatilGunSuresi gunSuresi
) {}
```

Controller imzası:

```java
@GetMapping
public ResponseEntity<List<TatilResponse>> tatilleriListele(
        @RequestParam LocalDate baslangicTarihi,
        @RequestParam LocalDate bitisTarihi,
        Authentication authentication) {
    return ResponseEntity.ok(tatilTakvimiService.tatilleriListele(
            authentication.getName(), baslangicTarihi, bitisTarihi));
}
```

Service metodu `@Transactional(readOnly=true)` ve `@PreAuthorize("isAuthenticated()")` olmalı; aktörü `PersonelRepository.findById` ile yükleyip pasifse `AccessDeniedException` atmalı, tarih aralığını Task 2 ile aynı kuralla doğrulamalı ve entity'leri DTO'ya çevirmelidir. Read audit çağırmamalıdır.

- [ ] **Step 4: Listeleme matrisini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest" "-Djacoco.skip=true" test
```

Expected: o ana kadar yazılan listeleme testlerinin tamamı geçer.

- [ ] **Step 5: İnceleme kapısı**

Response'ta yalnız beş güvenli alan bulunduğu, ID/date parsing hatalarının mevcut global handler ile `400` olduğu ve audit oluşmadığı raporlanır.

---

### Task 4: HR/Admin Tatil Oluşturma

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/TatilKaydetRequest.java`
- Create: `backend/src/main/java/com/jforce/backend/config/ZamanConfig.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/TatilController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiServiceTest.java`

**Interfaces:**
- Produces: `TatilResponse tatilOlustur(String aktorId, TatilKaydetRequest request)`.
- HTTP: `POST /api/holidays` → `201` ve `TatilResponse`.

- [ ] **Step 1: Happy-path, yetki ve validation testlerini RED yaz**

Request DTO sözleşmesi testte şu body ile sabitlenmelidir:

```json
{
  "ad": "  Cumhuriyet Bayramı  ",
  "tarih": "2026-10-29",
  "kategori": "RESMI_SABIT",
  "gunSuresi": "TAM_GUN"
}
```

Aktif HR ve ADMIN `201`, trim edilmiş ad ve güvenli DTO görmelidir. PERSONEL/YONETICI `403`; JWT HR fakat DB rolü PERSONEL olan aktör `403`; pasif HR/Admin `403`; missing principal `404`; token yok `401`. Boş/100 karakteri aşan ad, null alan, bozuk enum/tarih `400`; `Clock` tarihinden önceki create `400`; duplicate tarih `409` ve tek kayıt/audit; başarılı create tek audit üretmelidir.

- [ ] **Step 2: POST için 404 RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#hrGelecekTatiliniOlustururVeTrimEdilmisDtoDoner" "-Djacoco.skip=true" test
```

Expected: `POST /api/holidays` mapping'i olmadığı için `404`.

- [ ] **Step 3: Request DTO ve `Clock` bean'ini ekle**

```java
public record TatilKaydetRequest(
        @NotBlank @Size(max = 100) String ad,
        @NotNull LocalDate tarih,
        @NotNull TatilKategori kategori,
        @NotNull TatilGunSuresi gunSuresi
) {}
```

```java
@Configuration
public class ZamanConfig {
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

Integration test sınıfı `@Import` edilen `@TestConfiguration` içinde `@Primary Clock.fixed(Instant.parse("2026-08-22T09:00:00Z"), ZoneId.of("Europe/Istanbul"))` kullanmalı; testler makinenin gerçek tarihine bağlı olmamalıdır.

- [ ] **Step 4: Service create akışını ve DB rol kontrolünü uygula**

Akış sırası: principal yükle → aktiflik → DB rolü HR/ADMIN → normalize/iş kuralı → duplicate precheck → `saveAndFlush` → aynı transaction audit → DTO.

```java
@Transactional
@PreAuthorize("hasAnyRole('HR','ADMIN')")
public TatilResponse tatilOlustur(String aktorId, TatilKaydetRequest request) {
    Personel aktor = aktifAktoruGetir(aktorId);
    tatilYonetimYetkisiniDogrula(aktor);
    String ad = request.ad().trim();
    if (request.tarih().isBefore(LocalDate.now(clock)))
        throw new GecersizIstekException("Geçmiş tarih için tatil oluşturulamaz.");
    if (tatilRepository.existsByTarih(request.tarih()))
        throw new KaynakCakismasiException("Bu tarih için tatil zaten mevcut.");

    Tatil tatil = new Tatil();
    tatil.setAd(ad);
    tatil.setTarih(request.tarih());
    tatil.setKategori(request.kategori());
    tatil.setGunSuresi(request.gunSuresi());
    Tatil kaydedilen = tatiliGuvenliKaydet(tatil);

    auditService.logKaydetOrtakTransaction(
            AuditEylem.TATIL_EKLENDI, aktor, aktor.getKullaniciAdi(),
            "Tatil oluşturuldu.", null);
    return responseOlustur(kaydedilen);
}
```

`saveAndFlush` içindeki `DataIntegrityViolationException` aynı güvenli `KaynakCakismasiException` mesajına çevrilmelidir. Audit:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.TATIL_EKLENDI, aktor, aktor.getKullaniciAdi(),
        "Tatil oluşturuldu.", null);
```

- [ ] **Step 5: Unique yarış ve rollback testini ekle**

`TatilTakvimiServiceTest`, `tatilRepository.saveAndFlush(any(Tatil.class))` için `DataIntegrityViolationException` fırlatıp `KaynakCakismasiException` doğrulamalı ve audit'in çağrılmadığını kontrol etmelidir. Integration testte dış `@Transactional` test servisi create sonrası `IllegalStateException` atmalı; tatil ve audit birlikte rollback olmalıdır.

- [ ] **Step 6: Create testlerini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest,TatilTakvimiServiceTest" "-Djacoco.skip=true" test
```

Expected: create davranışı, güvenlik matrisi, audit ve rollback testleri geçer.

- [ ] **Step 7: İnceleme kapısı**

JWT ile DB rolünün birlikte doğrulandığı, geçmiş create'in `400`, duplicate/race'in `409`, audit hedefinin `null` ve detayın sabit olduğu raporlanır.

---

### Task 5: HR/Admin Tatil Güncelleme ve No-op

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/TatilController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiServiceTest.java`

**Interfaces:**
- Produces: `void tatilGuncelle(String aktorId, Integer tatilId, TatilKaydetRequest request)`.
- HTTP: `PATCH /api/holidays/{tatilId}` → `204` body yok.

- [ ] **Step 1: Güncelleme testlerini RED yaz**

Şu davranışlar ayrı test metotları olmalıdır: gerçek değişiklik `204` ve tek güvenli audit; aynı normalized dört alan `204` no-op ve audit/persistence version değişimi yok; geçmiş kayıt aynı body ile gönderilse bile no-op'tan önce `409`; gelecekteki kaydı geçmiş tarihe taşıma `400`; başka kaydın tarihine taşıma `409`; pozitif olmayan ID `400`; metin ID `400`; missing pozitif ID `404`; role/aktif/missing/unauthenticated matrisi; optimistic conflict kontrollü `409`; dış transaction hatasında kayıt ve audit rollback.

- [ ] **Step 2: PATCH için 404 RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#hrGelecekTatiliniGuncellerVeGuvenliAuditOlusturur" "-Djacoco.skip=true" test
```

- [ ] **Step 3: Controller ve service akışını minimal uygula**

Kontrol sırası kesin olarak şu olmalıdır:

```text
aktör/aktif/DB rolü → pozitif ID → kayıt 404 → mevcut kayıt geçmiş mi 409
→ request tarih/ad doğrulaması → normalized no-op karşılaştırması
→ başka kayıtla tarih çakışması → saveAndFlush → audit
```

No-op karşılaştırması:

```java
boolean ayni = tatil.getAd().equals(normalizeAd)
        && tatil.getTarih().equals(request.tarih())
        && tatil.getKategori() == request.kategori()
        && tatil.getGunSuresi() == request.gunSuresi();
if (ayni) return;
```

Audit olayı `TATIL_GUNCELLENDI`, detay tam olarak `Tatil güncellendi.`, hedef `null` olmalıdır. `DataIntegrityViolationException` duplicate `409`'a çevrilmeli; JPA `@Version` kaynaklı `OptimisticLockingFailureException` mevcut global handler ile kontrollü `409` kalmalıdır.

- [ ] **Step 4: Güncelleme ve yarış testlerini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest,TatilTakvimiServiceTest,GlobalExceptionHandlerTest" "-Djacoco.skip=true" test
```

- [ ] **Step 5: İnceleme kapısı**

Özellikle geçmiş kayıt kontrolünün no-op'tan önce olduğu ve başarısız/no-op isteklerde audit/version değişmediği kullanıcıya gösterilir.

---

### Task 6: HR/Admin Tatil Silme

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/controller/TatilController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/TatilTakvimiService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Test: `backend/src/test/java/com/jforce/backend/TatilTakvimiControllerIntegrationTest.java`

**Interfaces:**
- Produces: `void tatilSil(String aktorId, Integer tatilId)`.
- HTTP: `DELETE /api/holidays/{tatilId}` → `204` body yok.

- [ ] **Step 1: Silme testlerini RED yaz**

Bugün/gelecek unused kayıt HR ve Admin tarafından `204` ile silinir; geçmiş kayıt `409`; sıfır/negatif ID `400`; metin ID `400`; missing pozitif ID `404`; PERSONEL/YONETICI `403`; JWT/DB rol uyuşmazlığı `403`; pasif HR/Admin `403`; missing principal `404`; token yok `401`. Başarılı silme `TATIL_SILINDI`, gerçek aktör, `null` hedef ve `Tatil silindi.` detayıyla tek audit üretir. Dış transaction hatası tatili ve auditi geri getirir; optimistic delete yarışı `409` döner.

- [ ] **Step 2: DELETE için 404 RED'i doğrula**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest#hrGelecekTatiliniSilerVeGuvenliAuditOlusturur" "-Djacoco.skip=true" test
```

- [ ] **Step 3: Delete mapping ve transaction akışını uygula**

```java
@DeleteMapping("/{tatilId}")
public ResponseEntity<Void> tatilSil(
        @PathVariable Integer tatilId, Authentication authentication) {
    tatilTakvimiService.tatilSil(authentication.getName(), tatilId);
    return ResponseEntity.noContent().build();
}
```

Service aktör/yetki ve ID kontrolünden sonra entity'yi yüklemeli, `tatil.getTarih().isBefore(LocalDate.now(clock))` ise `KaynakCakismasiException("Geçmiş tatil kaydı silinemez.")` atmalı, `delete` + `flush` yapmalı ve ardından aynı transaction auditi yazmalıdır.

- [ ] **Step 4: Silme ve tam CRUD testlerini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=TatilTakvimiControllerIntegrationTest,TatilTakvimiServiceTest" "-Djacoco.skip=true" test
```

- [ ] **Step 5: İnceleme kapısı**

CRUD endpoint/status matrisi, geçmiş kayıtların değişmezliği ve transaction rollback kanıtı raporlanır.

---

### Task 7: PERSONEL/HR İş Günü Önizleme Endpoint'i

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/IsGunuOnizlemeResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/IzinOnizlemeService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/IzinHesaplamaController.java`
- Test: `backend/src/test/java/com/jforce/backend/IzinHesaplamaControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `IsGunuHesaplamaService.isGunuSayisiniHesapla(LocalDate, LocalDate)`.
- Produces: `IsGunuOnizlemeResponse isGunuOnizle(String aktorId, LocalDate baslangicTarihi, LocalDate bitisTarihi)`.
- HTTP: `GET /api/leave-requests/workday-preview` → `200 {"isGunuSayisi": 4.5}`.

- [ ] **Step 1: Önizleme entegrasyon testlerini RED yaz**

```java
mockMvc.perform(get("/api/leave-requests/workday-preview")
        .param("baslangicTarihi", "2026-08-24")
        .param("bitisTarihi", "2026-08-30")
        .header(AUTHORIZATION, "Bearer " + token(personel, RolAdi.PERSONEL)))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.isGunuSayisi").value(4.5));
```

Hafta içi yarım tatil sonucu `.5`; yalnız hafta sonu/tam tatil aralığı `200/0.0`; PERSONEL ve HR `200`; YONETICI ve ADMIN `403`; JWT izinli fakat DB rolü yasak aktör `403`; pasif aktör `403`; missing principal `404`; token yok `401`; eksik/bozuk/ters/367 günlük aralık `400`; bütün başarılı/başarısız read çağrılarında audit `0` olmalıdır.

- [ ] **Step 2: Önizleme 404 RED'ini doğrula**

```powershell
.\mvnw.cmd "-Dtest=IzinHesaplamaControllerIntegrationTest#personelTatilVeHaftaSonunuDuserekOndalikliIsGunuOnizler" "-Djacoco.skip=true" test
```

- [ ] **Step 3: DTO, aktör service'i ve controller'ı ekle**

```java
public record IsGunuOnizlemeResponse(BigDecimal isGunuSayisi) {}
```

```java
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('PERSONEL','HR')")
public IsGunuOnizlemeResponse isGunuOnizle(
        String aktorId, LocalDate baslangic, LocalDate bitis) {
    Personel aktor = personelRepository.findById(aktorId)
            .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
    if (!Boolean.TRUE.equals(aktor.getCalisiyorMu()))
        throw new AccessDeniedException("Pasif personel izin iş günü önizlemesi yapamaz.");
    RolAdi dbRolu = aktor.getRol().getRolAdi();
    if (dbRolu != RolAdi.PERSONEL && dbRolu != RolAdi.HR)
        throw new AccessDeniedException("Bu rol izin iş günü önizlemesi yapamaz.");
    return new IsGunuOnizlemeResponse(
            isGunuHesaplamaService.isGunuSayisiniHesapla(baslangic, bitis));
}
```

Controller yalnız request parametreleri, `authentication.getName()` ve `200` response taşır. Hedef personel ID parametresi kabul edilmez.

- [ ] **Step 4: Önizleme matrisini GREEN yap**

```powershell
.\mvnw.cmd "-Dtest=IzinHesaplamaControllerIntegrationTest,IsGunuHesaplamaServiceTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

- [ ] **Step 5: İnceleme kapısı**

Frontend'in kullanacağı kesin alan `isGunuSayisi`, sıfır davranışı `200/0.0`, izinli roller PERSONEL/HR ve endpoint'in auditsiz olduğu raporlanır. Yönetici talebi daha sonraki gerçek talep tesliminin konusu olarak bırakılır.

---

### Task 8: Regresyon, Migration Sırası ve Dokümantasyon Onay Kapısı

**Files:**
- Verify: tüm yeni/değişen backend dosyaları
- Conditional Modify after explicit approval: `API_SPEC.md`
- Conditional Modify after explicit approval: `SECURITY.md`
- Conditional Modify after explicit approval: `PROGRESS.md`
- Conditional Modify after explicit approval: `ROADMAP.md`

**Interfaces:**
- Verifies: dört holiday endpoint'i, workday preview, `BigDecimal` profil/izin zinciri.
- Does not produce: gerçek izin talebi veya onay endpoint'i.

- [ ] **Step 1: Kapsamlı hedefli backend regresyonunu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=IzinIsGunuMigrationIntegrationTest,TatilTableMigrationIntegrationTest,IsGunuHesaplamaServiceTest,TatilTakvimiServiceTest,TatilTakvimiControllerIntegrationTest,IzinHesaplamaControllerIntegrationTest,ProfilControllerIntegrationTest,SecurityHttpIntegrationTest" "-Djacoco.skip=true" test
```

Expected: tüm sınıflar `Failures: 0, Errors: 0`.

- [ ] **Step 2: Tam backend suite ve coverage kontrolünü çalıştır**

```powershell
.\mvnw.cmd test
```

Expected: bütün testler başarılı ve `All coverage checks have been met.`. Docker veya dış ortam problemi olursa hata ayrı raporlanır; test başarısızlığı gibi yorumlanmaz.

- [ ] **Step 3: Manuel migration uygulama sırasını doğrula**

Yeni/veri içeren ortamlarda önce yedek ve bakım modu, sonra şu sıra kullanılmalıdır:

```text
1. backend/db/manual-migrations/2026-08-22-change-izin-is-gunu-to-decimal.sql
2. backend/db/manual-migrations/2026-08-22-create-tatil-table.sql
3. Yeni backend sürümünü başlat
```

Her iki migration testinin ikinci çalıştırmada veri kaybı üretmediği kanıtı teslim raporuna eklenmelidir.

- [ ] **Step 4: CONTRIBUTING backend modül kontrol listesini uygula**

Şu sonuçlar tek tek doğrulanmalıdır: entity response değil; request'te hesaplanmış alan yok; method security + DB rolü var; mutation/audit aynı transaction; read auditsiz; 400/401/403/404/409 ayrımı testli; unique/version yarışları kontrollü; MySQL constraint ve migration testli; frontend değişmedi; yeni dependency yok.

- [ ] **Step 5: Ürün Markdown'ları için kullanıcıdan açık yazma onayı iste**

Onay verilirse belgelerde yalnız şu kapsam güncellenir:

```text
API_SPEC.md: /api/holidays CRUD, /api/leave-requests/workday-preview,
             DTO alanları, rol/status/hata matrisi.
SECURITY.md: DB aktörü ve rol doğrulaması, read/mutation audit ayrımı,
             unique/optimistic conflict ve geçmiş kayıt kuralı.
PROGRESS.md: yalnız testleri gerçekten geçen teslim ve test sayıları.
ROADMAP.md: tatil takvimi/iş günü altyapısı tamamlandı; gerçek talep/onay,
            tahakkuk ve frontend hâlâ sonraki iş.
```

Onay verilmezse bu dört dosyaya dokunulmaz ve teslimde “dokümantasyon güncellemesi bekliyor” denir.

- [ ] **Step 6: Markdown değişikliği onaylandıysa UTF-8 bütünlük kontrolü yap**

Önce `apply_patch` kullanılır. Her hedef dosya `read_bytes().decode("utf-8")` ile okunur; `\uFFFD`, `U+00C3` ve `U+00C4` bozulma işaretlerinin sayısı sıfır olmalı, eklenen endpoint/başlık beklenen sayıda bulunmalı, eski çelişkili metin kaldırılmalı ve aynı bölüm iki kez eklenmemelidir. `apply_patch` Windows'ta başarısız olursa ancak mevcut kullanıcı onayına dayanarak, `$env:PYTHONUTF8='1'` ve UTF-8 `$OutputEncoding` ayarlarıyla atomik Python düzenlemesi kullanılır.

- [ ] **Step 7: Son teslim raporunu hazırla**

Rapor şu somut başlıkları içermelidir: değişen backend dosyaları; repository → service → DTO/controller veri akışı; kesin endpoint/rol matrisi; `400/401/403/404/409` davranışları; migration sırası; hedefli ve tam test sonuçları; çalıştırılamayan kontroller; frontend'in kullanacağı `GET /api/leave-requests/workday-preview` ve `isGunuSayisi`; kapsam dışındaki gerçek talep/onay kuralları.

---

## Self-Review Record

- Spec coverage: şirket genelinde tek takvim, HR/Admin CRUD, tüm aktif rollerin read'i, PERSONEL/HR preview'u, tam/yarım gün algoritması, `BigDecimal`, geçmiş kayıt, no-op, audit, unique/version yarışları ve rollback görevlerle eşlendi.
- Scope guard: gerçek izin talebi, onay/red, tahakkuk ve frontend hiçbir implementation görevine eklenmedi.
- Type consistency: hesaplayıcı, entity, repository toplamı, izin response'u ve profil kalan bakiye boyunca `BigDecimal`; holiday ID boyunca `Integer`; personel principal boyunca `String`.
- Endpoint consistency: `GET|POST /api/holidays`, `PATCH|DELETE /api/holidays/{tatilId}`, `GET /api/leave-requests/workday-preview`.
- Security consistency: JWT method security dış sınır; DB'den yüklenen aktif aktör ve DB rolü service sınırı; read auditsiz, mutation aynı transaction auditli.
- Placeholder scan: plan içinde doldurulmayı bekleyen işaret, belirsiz hata görevi veya tanımsız interface bırakılmadı.
- Repository constraints: Git yok; frontend yok; ürün Markdown'ları explicit approval kapısında.
