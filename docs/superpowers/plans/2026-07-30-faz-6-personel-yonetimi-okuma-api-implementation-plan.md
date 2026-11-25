# Faz 6 Personel Yönetimi Okuma API'leri Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** HR/Admin personel yönetimi ekranları için güvenli liste, detay ve atanabilir yönetici okuma sözleşmelerini eklemek; mevcut dört personel mutation'ını ortak aktif-aktör ve hedef matrisiyle sağlamlaştırmak.

**Architecture:** Mevcut `HrPersonelController` korunur. Okumalar yeni `PersonelYonetimSorguService`, ortak aktör/hedef kuralları yeni `PersonelYonetimYetkiService` üzerinden yürür; mevcut `PersonelService` aynı yetki bileşenini mutation'larda kullanır. İhtiyaca özel JPQL sorguları entity'leri service içinde DTO'lara dönüştürür.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security method security, Spring Data JPA, JUnit 5, Mockito, MockMvc, Testcontainers, MySQL 8.

## Global Constraints

- Java ve test dosyalarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Frontend dosyalarına dokunulmaz.
- Git komutu çalıştırılmaz.
- Entity doğrudan response yapılmaz ve hassas alanlar döndürülmez.
- Okuma endpoint'leri audit üretmez ve `@Transactional(readOnly = true)` çalışır.
- HR hedefleri `PERSONEL` ve başka `HR`; Admin hedefleri `PERSONEL`, `HR`, `YONETICI` ile sınırlıdır.
- Öz-hedef ve `ADMIN` hedef yasaktır.
- Üç okuma endpoint'i yalnız aktif HR/Admin aktörlere açıktır.
- Mevcut mutation endpoint yolları, başarı status'ları, audit, no-op, rollback ve refresh-token davranışları korunur.

---

## Dosya haritası

Yeni production dosyaları:

- `backend/src/main/java/com/jforce/backend/model/dto/response/PersonelYonetimListeResponse.java`
- `backend/src/main/java/com/jforce/backend/model/dto/response/PersonelYonetimDetayResponse.java`
- `backend/src/main/java/com/jforce/backend/model/dto/response/AtanabilirYoneticiResponse.java`
- `backend/src/main/java/com/jforce/backend/service/PersonelYonetimYetkiService.java`
- `backend/src/main/java/com/jforce/backend/service/PersonelYonetimSorguService.java`

Değişecek production dosyaları:

- `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`

Yeni test dosyaları:

- `backend/src/test/java/com/jforce/backend/PersonelYonetimYetkiServiceTest.java`
- `backend/src/test/java/com/jforce/backend/PersonelYonetimControllerIntegrationTest.java`

Değişecek test dosyaları:

- `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/AdminPersonelControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`

Doğrulama sonrasında değişecek belgeler:

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`
- `SECURITY.md`

---

### Task 1: Ortak personel yönetimi yetki bileşeni

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/PersonelYonetimYetkiServiceTest.java`
- Create: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimYetkiService.java`

**Interfaces:**

- Produces: `Personel aktifAktoruGetir(String aktorId)`
- Produces: `Set<RolAdi> izinliHedefRolleri(Personel aktor)`
- Produces: `void hedefiDogrula(Personel aktor, Personel hedef)`

- [ ] **Step 1: Failing unit testleri ekle**

Gerçek `Personel`/`Rol` nesneleri ve yalnız repository mock'u kullanarak şunları sabitle:

```java
assertEquals(SET_PERSONEL_HR, service.izinliHedefRolleri(hr));
assertEquals(SET_PERSONEL_HR_YONETICI, service.izinliHedefRolleri(admin));
assertThrows(AccessDeniedException.class, () -> service.aktifAktoruGetir(pasifId));
assertThrows(KaynakBulunamadiException.class, () -> service.aktifAktoruGetir(olmayanId));
assertThrows(AccessDeniedException.class, () -> service.hedefiDogrula(hr, hr));
assertThrows(AccessDeniedException.class, () -> service.hedefiDogrula(hr, yonetici));
assertThrows(AccessDeniedException.class, () -> service.hedefiDogrula(admin, baskaAdmin));
assertDoesNotThrow(() -> service.hedefiDogrula(hr, baskaHr));
assertDoesNotThrow(() -> service.hedefiDogrula(admin, yonetici));
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimYetkiServiceTest test
```

Beklenen: production sınıfı bulunmadığı için compilation failure.

- [ ] **Step 3: Minimum policy implementasyonunu ekle**

```java
@Service
public class PersonelYonetimYetkiService {
    private static final Set<RolAdi> HR_HEDEFLERI =
            Set.of(RolAdi.PERSONEL, RolAdi.HR);
    private static final Set<RolAdi> ADMIN_HEDEFLERI =
            Set.of(RolAdi.PERSONEL, RolAdi.HR, RolAdi.YONETICI);

    private final PersonelRepository personelRepository;

    public PersonelYonetimYetkiService(
            PersonelRepository personelRepository
    ) {
        this.personelRepository = personelRepository;
    }

    public Personel aktifAktoruGetir(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(
                        () -> new KaynakBulunamadiException(
                                "Personel bulunamadı."
                        )
                );
        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel yönetim işlemi yapamaz."
            );
        }
        izinliHedefRolleri(aktor);
        return aktor;
    }

    public Set<RolAdi> izinliHedefRolleri(Personel aktor) {
        return switch (aktor.getRol().getRolAdi()) {
            case HR -> HR_HEDEFLERI;
            case ADMIN -> ADMIN_HEDEFLERI;
            default -> throw new AccessDeniedException(
                    "Personel yönetimi için yetkiniz bulunmuyor."
            );
        };
    }

    public void hedefiDogrula(Personel aktor, Personel hedef) {
        if (Objects.equals(
                aktor.getPersonelId(),
                hedef.getPersonelId()
        )) {
            throw new AccessDeniedException(
                    "Personel kendi kaydını yönetemez."
            );
        }
        if (!izinliHedefRolleri(aktor).contains(
                hedef.getRol().getRolAdi()
        )) {
            throw new AccessDeniedException(
                    "Hedef personel üzerinde işlem yapılamaz."
            );
        }
    }
}
```

- [ ] **Step 4: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimYetkiServiceTest test
```

---

### Task 2: Mevcut mutation'ları ortak güvenlik matrisiyle sağlamlaştır

**Files:**

- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`
- Modify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/AdminPersonelControllerIntegrationTest.java`
- Modify: `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java`

**Interfaces:**

- Consumes: `PersonelYonetimYetkiService#aktifAktoruGetir`
- Consumes: `PersonelYonetimYetkiService#hedefiDogrula`

- [ ] **Step 1: Failing mutation güvenlik testlerini ekle**

Integration testlerle şu HTTP davranışlarını sabitle:

```text
pasif HR POST /api/hr/personel -> 403
pasif HR PATCH /api/hr/personel/{id} -> 403
pasif HR PATCH /api/hr/personel/{id}/pasiflestir -> 403
pasif Admin PATCH /api/admin/personel/{id}/rol -> 403
HR kendi kaydını günceller -> 403
Admin kendi kaydını günceller -> 403
HR YONETICI hedefini günceller -> 403
HR ADMIN hedefini günceller -> 403
Admin ADMIN hedefini günceller -> 403
HR başka HR hedefini günceller -> 204
Admin YONETICI hedefini günceller -> 204
```

Mevcut `PersonelServiceTest` constructor kurulumuna policy mock'u ekle ve mevcut no-op/audit testlerinde `hedefiDogrula` çağrısının exception üretmemesini sağla.

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest,PersonelServiceTest" test
```

Beklenen: yeni pasif-aktör ve güncelleme hedef-matrisi testleri failure verir.

- [ ] **Step 3: `PersonelService` constructor'ına policy ekle**

```java
private final PersonelYonetimYetkiService personelYonetimYetkiService;
```

Constructor'a aynı bağımlılığı ekle ve ata.

- [ ] **Step 4: Dört mutation'da aktörü ortak servisle yükle**

Şu doğrudan aktör repository okumalarını:

```java
Personel aktor = personelRepository.findById(aktorId)
        .orElseThrow(...);
```

şununla değiştir:

```java
Personel aktor =
        personelYonetimYetkiService.aktifAktoruGetir(aktorId);
```

`rolDegistir`, `personelGuncelle` ve `personelPasiflestir` hedefi yükledikten sonra, no-op kontrolünden önce:

```java
personelYonetimYetkiService.hedefiDogrula(aktor, hedefPersonel);
```

Mevcut tekrarlı öz-hedef/ADMIN/HR→YONETICI kontrollerini kaldır; geri kalan persistence, audit, refresh-token ve rollback akışını değiştirme.

- [ ] **Step 5: GREEN ve mevcut regresyonları doğrula**

```powershell
.\mvnw.cmd "-Dtest=HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest,PersonelServiceTest" test
```

---

### Task 3: Yönetim personel listesi

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/PersonelYonetimListeResponse.java`
- Create: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimSorguService.java`
- Create: `backend/src/test/java/com/jforce/backend/PersonelYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`

**Interfaces:**

- Produces: `GET /api/hr/personel`
- Produces: `SayfaliResponse<PersonelYonetimListeResponse>`

- [ ] **Step 1: Liste sözleşmesi için failing integration testleri ekle**

Testler gerçek MySQL ve security chain üzerinden şu davranışları ayrı ayrı doğrular:

```text
HR listesi: PERSONEL + başka HR; self/YONETICI/ADMIN yok
Admin listesi: PERSONEL + HR + YONETICI; self/ADMIN yok
PERSONEL/YONETICI -> 403; token yok -> 401
pasif principal -> 403; bulunmayan principal -> 404
arama: ad/soyad/tam ad/kullanıcı adı, case-insensitive, trim, boş=filtresiz
departmanId + rol + aktif + arama -> AND
rol=ADMIN -> boş 200
page/size metadata ve ad/soyad/id sıralaması
page<0, size<1, size>100 -> 400
sayısal olmayan parametre ve geçersiz rol -> 400
response güvenli alanları içerir; hassas alanlar yok
okuma audit oluşturmaz
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

Beklenen: GET mapping olmadığı için 404 failure'ları.

- [ ] **Step 3: Liste DTO'sunu ekle**

```java
public record PersonelYonetimListeResponse(
        String personelId,
        String ad,
        String soyad,
        String kullaniciAdi,
        RolAdi rol,
        Integer departmanId,
        String departmanAdi,
        Boolean aktif
) {
}
```

- [ ] **Step 4: Repository sayfa sorgusunu ekle**

```java
Page<Personel> findPersonelYonetimSayfasi(
        @Param("aktorId") String aktorId,
        @Param("izinliRoller") Set<RolAdi> izinliRoller,
        @Param("arama") String arama,
        @Param("departmanId") Integer departmanId,
        @Param("rol") RolAdi rol,
        @Param("aktif") Boolean aktif,
        Pageable pageable
);
```

Ana ve count JPQL koşulları birebir aynı olmalıdır:

```text
p.personelId <> :aktorId
p.rol.rolAdi IN :izinliRoller
arama null veya ad/soyad/tam ad/kullanıcı adı LIKE
departmanId null veya eşit
rol null veya eşit
aktif null veya eşit
```

Ana sorgu departman ve rolü fetch eder; sıralama `adi`, `soyadi`, `personelId` olur.

- [ ] **Step 5: Sorgu service liste metodunu ekle**

```java
public SayfaliResponse<PersonelYonetimListeResponse>
personelleriGetir(
        String aktorId,
        int page,
        int size,
        String arama,
        Integer departmanId,
        RolAdi rol,
        Boolean aktif
)
```

Service pagination'ı doğrular, policy ile aktif aktörü yükler, aramayı trim/null normalize eder, izinli rol kümesini sorguya geçirir ve entity'leri DTO'ya dönüştürür.

- [ ] **Step 6: Controller GET mapping'ini ekle**

```java
@GetMapping
public ResponseEntity<SayfaliResponse<PersonelYonetimListeResponse>>
personelleriGetir(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String arama,
        @RequestParam(required = false) Integer departmanId,
        @RequestParam(required = false) RolAdi rol,
        @RequestParam(required = false) Boolean aktif,
        Authentication authentication
)
```

- [ ] **Step 7: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

---

### Task 4: Tek personel yönetim detayı

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/PersonelYonetimDetayResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimSorguService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify: `backend/src/test/java/com/jforce/backend/PersonelYonetimControllerIntegrationTest.java`

**Interfaces:**

- Produces: `GET /api/hr/personel/{personelId}`
- Produces: `PersonelYonetimDetayResponse`

- [ ] **Step 1: Failing detay integration testlerini ekle**

```text
izinli hedef -> 200 ve onaylı form alanları
yöneticisiz hedef -> yoneticiId null
bulunmayan hedef -> 404 Personel bulunamadı.
HR YONETICI hedef -> 403
HR/Admin öz-hedef -> 403
ADMIN hedef -> 403
pasif principal -> 403
response parola/hash/flag/sicil/izin/profilFoto/isCikisTarihi içermez
okuma audit oluşturmaz
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

- [ ] **Step 3: Detay DTO'sunu ekle**

```java
public record PersonelYonetimDetayResponse(
        String personelId,
        String kullaniciAdi,
        String ad,
        String soyad,
        LocalDate dogumTarihi,
        String ePosta,
        Integer departmanId,
        String yoneticiId,
        LocalDate isGirisTarihi,
        String telNo,
        String mezunOkul,
        String mezunBolum,
        Integer mezuniyetYili,
        RolAdi rol,
        Boolean aktif
) {
}
```

- [ ] **Step 4: Repository detay sorgusunu ekle**

```java
@Query("""
        SELECT p
        FROM Personel p
        JOIN FETCH p.departman
        JOIN FETCH p.rol
        LEFT JOIN FETCH p.yonetici
        WHERE p.personelId = :personelId
        """)
Optional<Personel> findYonetimDetayiById(
        @Param("personelId") String personelId
);
```

- [ ] **Step 5: Service ve controller metodunu ekle**

```java
public PersonelYonetimDetayResponse personelDetayiniGetir(
        String aktorId,
        String hedefPersonelId
)
```

Service aktif aktörü policy ile alır, hedefi repository'den `Personel bulunamadı.` mesajıyla yükler, `hedefiDogrula` uygular ve yalnız DTO alanlarını map eder.

```java
@GetMapping("/{personelId}")
public ResponseEntity<PersonelYonetimDetayResponse>
personelDetayiniGetir(
        @PathVariable String personelId,
        Authentication authentication
)
```

- [ ] **Step 6: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

---

### Task 5: Departmana göre atanabilir yönetici lookup'u

**Files:**

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/AtanabilirYoneticiResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelYonetimSorguService.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`
- Modify: `backend/src/test/java/com/jforce/backend/PersonelYonetimControllerIntegrationTest.java`

**Interfaces:**

- Produces: `GET /api/hr/personel/atanabilir-yoneticiler`
- Produces: `List<AtanabilirYoneticiResponse>`

- [ ] **Step 1: Failing lookup integration testlerini ekle**

```text
yalnız aktif YONETICI + seçilen departman
haricPersonelId eşleşeni çıkarır
haricPersonelId null/boş/blank -> hariç tutma yok
olmayan haricPersonelId -> varlık kontrolü yok
ad/soyad/id sıralaması
eşleşme yok -> 200 []
departman yok -> 404 Departman bulunamadı.
departmanId eksik/sıfır/negatif/metin -> 400
HR/Admin erişir; PERSONEL/YONETICI -> 403
pasif/bulunmayan principal -> 403/404
okuma audit oluşturmaz
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

- [ ] **Step 3: Lookup DTO ve repository sorgusunu ekle**

```java
public record AtanabilirYoneticiResponse(
        String personelId,
        String ad,
        String soyad,
        String kullaniciAdi
) {
}
```

```java
@Query("""
        SELECT p
        FROM Personel p
        WHERE p.calisiyorMu = true
          AND p.rol.rolAdi = :yoneticiRolu
          AND p.departman.departmanId = :departmanId
          AND (
                :haricPersonelId IS NULL
                OR p.personelId <> :haricPersonelId
          )
        ORDER BY p.adi, p.soyadi, p.personelId
        """)
List<Personel> findAtanabilirYoneticiler(
        @Param("departmanId") Integer departmanId,
        @Param("yoneticiRolu") RolAdi yoneticiRolu,
        @Param("haricPersonelId") String haricPersonelId
);
```

- [ ] **Step 4: Service ve controller metodunu ekle**

```java
public List<AtanabilirYoneticiResponse> atanabilirYoneticileriGetir(
        String aktorId,
        Integer departmanId,
        String haricPersonelId
)
```

Service aktif aktörü doğrular; null veya pozitif olmayan departman ID için `GecersizIstekException`, bulunmayan departman için `KaynakBulunamadiException("Departman bulunamadı.")` üretir; hariç ID'yi trim/null normalize eder.

```java
@GetMapping("/atanabilir-yoneticiler")
public ResponseEntity<List<AtanabilirYoneticiResponse>>
atanabilirYoneticileriGetir(
        @RequestParam Integer departmanId,
        @RequestParam(required = false) String haricPersonelId,
        Authentication authentication
)
```

- [ ] **Step 5: Eksik query parametresini kontrollü 400'e çevir**

`GlobalExceptionHandler` içine:

```java
@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<ErrorResponse> handleMissingRequestParameter(
        MissingServletRequestParameterException ex,
        HttpServletRequest request
) {
    ErrorResponse body = new ErrorResponse(
            400,
            "Geçersiz istek",
            LocalDateTime.now(),
            request.getRequestURI()
    );
    return ResponseEntity.badRequest().body(body);
}
```

- [ ] **Step 6: GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=PersonelYonetimControllerIntegrationTest test
```

---

### Task 6: Tam regresyon ve belgeler

**Files:**

- Verify: bütün backend testleri
- Modify: `API_SPEC.md`
- Modify: `ROADMAP.md`
- Modify: `PROGRESS.md`
- Modify: `SECURITY.md`

- [ ] **Step 1: Bütün ilgili odaklı testleri çalıştır**

```powershell
.\mvnw.cmd "-Dtest=PersonelYonetimYetkiServiceTest,PersonelYonetimControllerIntegrationTest,HrPersonelControllerIntegrationTest,AdminPersonelControllerIntegrationTest,PersonelServiceTest" test
```

- [ ] **Step 2: Tam backend suite'ini çalıştır**

```powershell
.\mvnw.cmd test
```

Kabul: gerçek çıktıda `0 failures`, `0 errors`, `BUILD SUCCESS`.

- [ ] **Step 3: Belgeleri yalnız gerçek sözleşme ve test sayılarıyla güncelle**

`API_SPEC.md`: üç endpoint'in query/response/status sözleşmeleri.

`ROADMAP.md`: Faz 6 liste, detay, lookup ve mutation güvenliği maddelerini `[x]`.

`SECURITY.md`: aktif HR/Admin aktör kontrolü ve hedef matrisi.

`PROGRESS.md`: değişen davranışlar, odaklı ve tam testlerin gerçek sayıları.

---

## Son kabul ölçütleri

- HR/Admin listesi ve detayı aynı hedef matrisini uygular.
- Liste filtreleri ana ve count sorgularında aynıdır.
- Lookup yalnız aktif, doğru departmandaki `YONETICI` adaylarını döndürür.
- Dört mevcut mutation pasif aktör tarafından kullanılamaz.
- Öz-hedef ve yasak hedefler frontend görünürlüğünden bağımsız olarak backend'de reddedilir.
- Response DTO'ları hassas alan içermez.
- Okuma endpoint'leri audit üretmez.
- Odaklı ve tam backend testleri başarılıdır.
