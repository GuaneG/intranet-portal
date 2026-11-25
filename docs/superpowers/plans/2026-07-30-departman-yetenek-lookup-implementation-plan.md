# Departman ve Yetenek Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif ve kimliği doğrulanmış personelin bütün departman ve yetenek tanımlarını alfabetik, sayfalamasız ve ortak `{ id, ad }` sözleşmesiyle okuyabildiği iki lookup endpoint’i oluşturmak.

**Architecture:** Ortak `LookupController` iki GET endpoint’ini sunar; `LookupService` JWT principal personelinin mevcut ve aktif olduğunu doğrular, salt-okunur repository sonuçlarını `LookUpResponse` listesine dönüştürür. Mevcut `DepartmanRepository` genişletilir, yeni `YetenekRepository` eklenir; yönetim işlemleri ve audit bu kapsamın dışındadır.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security Method Security, Spring Data JPA/Hibernate, MySQL 8, JUnit 5, MockMvc, Testcontainers

## Global Constraints

- Agent hiçbir Java veya test dosyasını değiştirmez; yalnız kopyalanabilir kod blokları verir.
- Git komutlarını yalnız kullanıcı çalıştırır; planda commit adımı yoktur.
- Endpointler `GET /api/departments` ve `GET /api/skills` biçimindedir.
- `ADMIN`, `HR`, `YONETICI` ve `PERSONEL` rollerinin tamamı erişebilir.
- JWT principal personeli bulunamazsa `404`, pasifse kalan access-token ömründe dahi `403`, authentication yoksa `401` döner.
- Bütün tanımlar döner; aktif personele henüz atanmamış departman veya yetenek gizlenmez.
- Sonuçlar ada göre, ardından ID ile artan ve kararlı sıralanır.
- Response sayfalamasız `List<LookUpResponse>` biçimindedir; kayıt yoksa `[]` döner.
- Entity doğrudan API response’u yapılmaz.
- Listeleme read-only’dir ve audit kaydı oluşturmaz.
- Departman/yetenek oluşturma, güncelleme, silme ve personele yetenek atama kapsam dışıdır.

---

## File Structure

- Modify: `backend/src/main/java/com/jforce/backend/repository/DepartmanRepository.java` — alfabetik departman sorgusu.
- Create: `backend/src/main/java/com/jforce/backend/repository/YetenekRepository.java` — alfabetik yetenek sorgusu.
- Create: `backend/src/main/java/com/jforce/backend/service/LookupService.java` — aktif principal doğrulama ve entity–DTO mapping.
- Create: `backend/src/main/java/com/jforce/backend/controller/LookupController.java` — iki GET endpoint’i.
- Create: `backend/src/test/java/com/jforce/backend/LookupControllerIntegrationTest.java` — Security/JPA/MySQL sözleşmesi.
- Modify only after full verification: `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md`, `SECURITY.md`.

---

### Task 1: Lookup HTTP sözleşmesi — RED

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/LookupControllerIntegrationTest.java`

**Interfaces:**
- Exercises: `GET /api/departments`
- Exercises: `GET /api/skills`
- Proves: sıralama, bütün tanımlar, boş yetenek listesi, rol matrisi, principal kontrolleri ve auditsiz okuma

- [ ] **Step 1: Integration test sınıfını oluştur**

Yeni test production `YetenekRepository` sınıfına bağımlı olmaz; yetenek fixture’ları `JdbcTemplate` ile hazırlanır. Böylece production kodu eklenmeden test sınıfı derlenir ve doğru nedenle RED olur.

```java
package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LookupControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private IzinBilgileriRepository izinBilgileriRepository;
    @Autowired private PersonelYetenekRepository personelYetenekRepository;
    @Autowired private PersonelRepository personelRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Rol personelRolu;
    private Departman aktorDepartmani;
    private Personel aktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");

        personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        aktorDepartmani = departmanKaydet("Yazılım");
        aktor = personelKaydet(
                "lookup.aktor",
                "Lookup",
                "Aktör",
                personelRolu,
                aktorDepartmani,
                true
        );
    }

    @Test
    void departmanlarAlfabetikDonerVeKullanilmayanDepartmanDahilEdilir()
            throws Exception {
        Departman finans = departmanKaydet("Finans");
        Departman bilgiTeknolojileri =
                departmanKaydet("Bilgi Teknolojileri");

        mockMvc.perform(get("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id")
                        .value(bilgiTeknolojileri.getDepartmanId()))
                .andExpect(jsonPath("$[0].ad")
                        .value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$[1].id")
                        .value(finans.getDepartmanId()))
                .andExpect(jsonPath("$[1].ad").value("Finans"))
                .andExpect(jsonPath("$[2].id")
                        .value(aktorDepartmani.getDepartmanId()))
                .andExpect(jsonPath("$[2].ad").value("Yazılım"))
                .andExpect(jsonPath("$[0].departmanId")
                        .doesNotExist())
                .andExpect(jsonPath("$[0].departmanAdi")
                        .doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yeteneklerAlfabetikDonerVeKullanilmayanYetenekDahilEdilir()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer angularId = yetenekOlustur("Angular");
        Integer dockerId = yetenekOlustur("Docker");

        mockMvc.perform(get("/api/skills")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(angularId))
                .andExpect(jsonPath("$[0].ad").value("Angular"))
                .andExpect(jsonPath("$[1].id").value(dockerId))
                .andExpect(jsonPath("$[1].ad").value("Docker"))
                .andExpect(jsonPath("$[2].id").value(javaId))
                .andExpect(jsonPath("$[2].ad").value("Java"))
                .andExpect(jsonPath("$[0].yetenekId")
                        .doesNotExist())
                .andExpect(jsonPath("$[0].yetenekAdi")
                        .doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yetenekYoksaBosListeDoner() throws Exception {
        mockMvc.perform(get("/api/skills")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("rollerVeEndpointler")
    void tumRollerIkiLookupEndpointineErisebilir(
            RolAdi rolAdi,
            String endpoint
    ) throws Exception {
        Rol rol = switch (rolAdi) {
            case ADMIN -> rolKaydet(1, RolAdi.ADMIN);
            case PERSONEL -> personelRolu;
            case YONETICI -> rolKaydet(3, RolAdi.YONETICI);
            case HR -> rolKaydet(4, RolAdi.HR);
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, rolAdi)
                        ))
                .andExpect(status().isOk());
    }

    static Stream<Arguments> rollerVeEndpointler() {
        return Stream.of(RolAdi.values())
                .flatMap(rolAdi -> Stream.of(
                                "/api/departments",
                                "/api/skills"
                        )
                        .map(endpoint ->
                                Arguments.of(rolAdi, endpoint)
                        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void tokenOlmadanLookupGoruntulenemez(String endpoint)
            throws Exception {
        mockMvc.perform(get(endpoint))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void tokendakiPersonelBulunamazsa404Doner(String endpoint)
            throws Exception {
        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Personel bulunamadı."));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/departments",
            "/api/skills"
    })
    void pasifPrincipalLookupGoruntuleyemez(String endpoint)
            throws Exception {
        String token = token(aktor, RolAdi.PERSONEL);
        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get(endpoint)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);
        return departmanRepository.save(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman departman,
            boolean aktif
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        return personelRepository.saveAndFlush(personel);
    }

    private Integer yetenekOlustur(String yetenekAdi) {
        jdbcTemplate.update(
                "INSERT INTO yetenek (yetenek_adi) VALUES (?)",
                yetenekAdi
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT yetenek_id
                FROM yetenek
                WHERE yetenek_adi = ?
                """,
                Integer.class,
                yetenekAdi
        );
    }

    private String token(Personel personel, RolAdi rolAdi) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }
}
```

- [ ] **Step 2: Odaklı testi çalıştır ve doğru RED sonucunu doğrula**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd -Dtest=LookupControllerIntegrationTest test
```

Beklenen:

- Test sınıfı derlenir ve toplam 17 invocation çalışır.
- Token olmayan iki invocation mevcut Security filter chain nedeniyle `401` ile geçebilir.
- Authenticated endpoint çağrıları endpoint henüz bulunmadığı için `404` ile başarısız olur.
- Eksik principal testleri genel route `404` mesajı ile `Personel bulunamadı.` mesajını ayırt ettiği için başarısız kalır.
- Derleme, fixture veya foreign-key hatası doğru RED kabul edilmez.

---

### Task 2: Lookup repository, service ve controller — GREEN

**Files:**
- Modify: `backend/src/main/java/com/jforce/backend/repository/DepartmanRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/repository/YetenekRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/service/LookupService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/LookupController.java`
- Test: `backend/src/test/java/com/jforce/backend/LookupControllerIntegrationTest.java`

**Interfaces:**
- Produces: `List<Departman> findAllByOrderByDepartmanAdiAscDepartmanIdAsc()`
- Produces: `List<Yetenek> findAllByOrderByYetenekAdiAscYetenekIdAsc()`
- Produces: `List<LookUpResponse> departmanlariGetir(String aktorId)`
- Produces: `List<LookUpResponse> yetenekleriGetir(String aktorId)`

- [ ] **Step 1: DepartmanRepository’ye alfabetik sorguyu ekle**

`DepartmanRepository.java` dosyasının tamamı:

```java
package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Departman;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepartmanRepository
        extends JpaRepository<Departman, Integer> {

    List<Departman>
    findAllByOrderByDepartmanAdiAscDepartmanIdAsc();
}
```

- [ ] **Step 2: YetenekRepository’yi oluştur**

```java
package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Yetenek;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface YetenekRepository
        extends JpaRepository<Yetenek, Integer> {

    List<Yetenek>
    findAllByOrderByYetenekAdiAscYetenekIdAsc();
}
```

- [ ] **Step 3: LookupService’i oluştur**

```java
package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.YetenekRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LookupService {

    private final PersonelRepository personelRepository;
    private final DepartmanRepository departmanRepository;
    private final YetenekRepository yetenekRepository;

    public LookupService(
            PersonelRepository personelRepository,
            DepartmanRepository departmanRepository,
            YetenekRepository yetenekRepository
    ) {
        this.personelRepository = personelRepository;
        this.departmanRepository = departmanRepository;
        this.yetenekRepository = yetenekRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<LookUpResponse> departmanlariGetir(
            String aktorId
    ) {
        aktifPersoneliDogrula(aktorId);

        return departmanRepository
                .findAllByOrderByDepartmanAdiAscDepartmanIdAsc()
                .stream()
                .map(departman -> new LookUpResponse(
                        departman.getDepartmanId(),
                        departman.getDepartmanAdi()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<LookUpResponse> yetenekleriGetir(
            String aktorId
    ) {
        aktifPersoneliDogrula(aktorId);

        return yetenekRepository
                .findAllByOrderByYetenekAdiAscYetenekIdAsc()
                .stream()
                .map(yetenek -> new LookUpResponse(
                        yetenek.getYetenekId(),
                        yetenek.getYetenekAdi()
                ))
                .toList();
    }

    private void aktifPersoneliDogrula(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() ->
                        new KaynakBulunamadiException(
                                "Personel bulunamadı."
                        )
                );

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel lookup verilerini görüntüleyemez."
            );
        }
    }
}
```

- [ ] **Step 4: LookupController’ı oluştur**

```java
package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.service.LookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LookupController {

    private final LookupService lookupService;

    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/departments")
    public ResponseEntity<List<LookUpResponse>>
    departmanlariGetir(Authentication authentication) {
        return ResponseEntity.ok(
                lookupService.departmanlariGetir(
                        authentication.getName()
                )
        );
    }

    @GetMapping("/skills")
    public ResponseEntity<List<LookUpResponse>>
    yetenekleriGetir(Authentication authentication) {
        return ResponseEntity.ok(
                lookupService.yetenekleriGetir(
                        authentication.getName()
                )
        );
    }
}
```

- [ ] **Step 5: Odaklı testi GREEN doğrula**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd -Dtest=LookupControllerIntegrationTest test
```

Beklenen: 17 test invocation, sıfır failure/error/skipped ve `BUILD SUCCESS`.

Bir test başarısızsa testi gevşetme. Hatanın repository sıralaması, principal doğrulaması, DTO mapping veya HTTP katmanından hangisinde oluştuğunu belirleyip yalnız production kodundaki sözleşme uyumsuzluğunu düzelt.

---

### Task 3: Tam doğrulama ve belge eşitleme

**Files:**
- Verify: `backend/src/test/java/com/jforce/backend/LookupControllerIntegrationTest.java`
- Modify only after successful full suite: `API_SPEC.md`
- Modify only after successful full suite: `ROADMAP.md`
- Modify only after successful full suite: `PROGRESS.md`
- Modify only after successful full suite: `SECURITY.md`

**Interfaces:**
- Consumes: doğrulanmış lookup endpointleri
- Produces: regresyon kanıtı ve kodla uyumlu proje belgeleri

- [ ] **Step 1: Odaklı doğrulama çıktısını kaydet**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd -Dtest=LookupControllerIntegrationTest test
```

Çıktıdaki gerçek test sayısı, failure/error/skipped değerleri ve build durumu belge güncellemesinde aynen kullanılır.

- [ ] **Step 2: Tam backend paketini çalıştır**

```powershell
.\mvnw.cmd test
```

Beklenen: sıfır failure/error ve `BUILD SUCCESS`. Gerçek toplam test sayısı Maven çıktısından alınır.

- [ ] **Step 3: API_SPEC.md durumunu güncelle**

- `GET /api/departments` ve `GET /api/skills` satırlarını “Planlanan Lookup’lar” bölümünden uygulanmış lookup sözleşmesine taşı.
- İki endpoint’in authenticated bütün roller, sayfalamasız `[{ id, ad }]`, alfabetik bütün tanımlar, aktif principal ve auditsiz okuma davranışını yaz.
- `POST /api/skills` ve diğer yönetim işlemlerini planlanan durumda bırak.

- [ ] **Step 4: ROADMAP.md maddelerini hassas biçimde böl**

Geniş “listeleme/yönetme” maddelerini, uygulanmış listeleme ile bekleyen yönetimi birbirinden ayır:

```markdown
- [x] Departman bilgilerini alfabetik listeleme endpoint’ini oluştur
- [ ] Departman ekleme/güncelleme yönetim akışını oluştur
- [x] Yetenek bilgilerini alfabetik listeleme endpoint’ini oluştur
- [ ] Yetenek ekleme/güncelleme yönetim akışını oluştur
- [ ] Personel yeteneklerini güncelleme akışını oluştur
```

- [ ] **Step 5: PROGRESS.md ve SECURITY.md kanıtlarını ekle**

- `PROGRESS.md`: iki endpoint, ortak service/controller yaklaşımı, aktif principal kontrolü, bütün tanımlar, alfabetik mapping ve kullanıcının raporladığı gerçek odaklı/tam test sayılarını yeni gün kaydına ekle.
- `SECURITY.md`: pasif principal’ın geçerli access token ile iki lookup endpointinde `403` almasını; bulunamayan principal’ın `404`, unauthenticated isteğin `401` almasını ve okumanın audit-free olduğunu kaydet.

- [ ] **Step 6: Son kapsam kontrolünü yap**

- Response yalnız `id` ve `ad` içerir.
- Aktif personele atanmamış tanımlar da görünür.
- Dört rol iki endpoint’e erişebilir.
- Pasif principal iki endpoint’te `403` alır.
- GET işlemleri audit veya persistence yan etkisi oluşturmaz.
- `POST /api/skills`, departman/yetenek yönetimi ve personele yetenek atama uygulanmış gibi belgelenmez.
- Tam backend test sonucu paylaşılmadan özellik tamamlandı sayılmaz.
