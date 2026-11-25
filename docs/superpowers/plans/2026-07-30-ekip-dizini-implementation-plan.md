# Ekip Dizini Implementation Plan

> **Çalışma biçimi:** Backend ve test kodlarını kullanıcı kopyalayıp çalıştırır. Agent Java/test dosyalarını değiştirmez, git komutu çalıştırmaz ve her kırmızı/yeşil test çıktısını kullanıcıdan doğrular.

**Goal:** Kimliği doğrulanmış ve aktif personelin, yalnız aktif çalışanları departman ve yetenek filtreleriyle sayfalı biçimde listeleyebildiği `GET /api/ekip-dizini` endpoint’ini oluşturmak.

**Architecture:** İlk repository sorgusu aktif personelleri opsiyonel departman/yetenek filtresiyle doğru şekilde sayfalar; yetenek filtresi satır çoğaltmamak için `EXISTS` kullanır. İkinci repository sorgusu yalnız o sayfadaki personellerin bütün yeteneklerini topluca getirir; service sonuçları personel ID’sine göre gruplayıp sade `SayfaliResponse<EkipDiziniResponse>` sözleşmesine dönüştürür.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Spring Security Method Security, Spring Data JPA/Hibernate, MySQL 8, JUnit 5, MockMvc, Testcontainers

## Global Constraints

- Agent hiçbir Java veya test dosyasını değiştirmez; yalnız kopyalanabilir kod blokları verir.
- Git komutlarını yalnız kullanıcı çalıştırır; planda commit adımı yoktur.
- Endpoint `GET /api/ekip-dizini` ve tüm authenticated rollere açıktır.
- JWT principal’daki personel bulunamazsa `404`, pasifse `403`, authentication yoksa `401` döner.
- Yalnız `calisiyorMu=true` personeller listelenir.
- `page` varsayılan `0` ve en az `0`; `size` varsayılan `20` ve `1–100` aralığındadır.
- `page`, `size`, `departmanId` veya `yetenekId` sayısal tipe çevrilemezse `400` döner.
- Olmayan departman/yetenek ID’si `200 OK` ve boş sayfa döndürür.
- Departman ve yetenek filtreleri birlikte gönderilirse `AND` uygulanır.
- Sabit sıralama `ad`, `soyad`, ardından response’ta gösterilmeyen `personelId` biçimindedir.
- `EkipDiziniResponse` değişmez; `personelId` eklenmez.
- Yetenek filtresi sonuç üyeliğini belirler; eşleşen personelin bütün yetenekleri alfabetik döner.
- Response `{ icerik, sayfa, sayfaBoyutu, toplamKayit, toplamSayfa }` alanlarını taşır; Spring `Page` doğrudan response yapılmaz.
- Listeleme read-only’dir ve audit kaydı oluşturmaz.
- Collection fetch join ile pagination yapılmaz; kişi sayfası ve sayfadaki yetenekler iki toplu sorguyla alınır.

---

## File Structure

- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/SayfaliResponse.java` — framework’ten bağımsız pagination response’u.
- Keep: `backend/src/main/java/com/jforce/backend/model/dto/response/EkipDiziniResponse.java` — mevcut alanlar değişmeden kullanılır.
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java` — aktif, filtrelenebilir ve sayfalı kişi sorgusu.
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelYetenekRepository.java` — sayfadaki personeller için toplu yetenek sorgusu.
- Create: `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java` — principal kontrolü, validation, iki sorgunun birleştirilmesi ve mapping.
- Create: `backend/src/main/java/com/jforce/backend/controller/EkipDiziniController.java` — HTTP query parametreleri ve `200 OK` response’u.
- Modify: `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java` — sayısal query parametresi tip hatasını kontrollü `400` yapar.
- Create: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java` — gerçek Security/JPA/MySQL davranışını doğrular.
- Modify after full verification: `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` — yalnız doğrulanmış uygulama durumunu kaydeder.

---

### Task 1: Temel aktif liste, pagination ve security — RED

**Files:**
- Create: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`

**Interfaces:**
- Exercises: `GET /api/ekip-dizini`
- Proves: authentication, aktif principal, yalnız aktif hedefler, pagination metadata ve sabit sıralama

- [ ] **Step 1: Test sınıfının ortak kurulumunu yaz**

Test sınıfı `AbstractIntegrationTest` tabanını ve gerçek repository’leri kullanır:

```java
package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EkipDiziniControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PersonelYetenekRepository personelYetenekRepository;
    @Autowired private PersonelRepository personelRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Rol personelRolu;
    private Departman bilgiTeknolojileri;
    private Personel aktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");

        personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        bilgiTeknolojileri = departmanKaydet("Bilgi Teknolojileri");
        aktor = personelKaydet(
                "aktif.aktor",
                "Aktif",
                "Aktör",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
    }
}
```

Ortak yardımcılar sınıfın sonunda bulunur:

```java
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

private String token(Personel personel, RolAdi rolAdi) {
    return jwtService.generateToken(personel.getPersonelId(), rolAdi);
}
```

- [ ] **Step 2: Temel liste ve pagination testlerini yaz**

```java
@Test
void yalnizAktifPersonellerSabitSiraylaListelenir() throws Exception {
    personelKaydet("zeynep.kaya", "Zeynep", "Kaya",
            personelRolu, bilgiTeknolojileri, true);
    personelKaydet("ayse.yilmaz", "Ayşe", "Yılmaz",
            personelRolu, bilgiTeknolojileri, true);
    personelKaydet("pasif.personel", "Ahmet", "Pasif",
            personelRolu, bilgiTeknolojileri, false);

    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sayfa").value(0))
            .andExpect(jsonPath("$.sayfaBoyutu").value(20))
            .andExpect(jsonPath("$.toplamKayit").value(3))
            .andExpect(jsonPath("$.toplamSayfa").value(1))
            .andExpect(jsonPath("$.icerik.length()").value(3))
            .andExpect(jsonPath("$.icerik[0].ad").value("Aktif"))
            .andExpect(jsonPath("$.icerik[1].ad").value("Ayşe"))
            .andExpect(jsonPath("$.icerik[2].ad").value("Zeynep"))
            .andExpect(jsonPath("$.icerik[*].ad",
                    not(hasItem("Ahmet"))));

    assertEquals(0L, auditLogRepository.count());
}

@Test
void ikinciSayfaDogruMetadataVeIcerigiDoner() throws Exception {
    personelKaydet("burak.test", "Burak", "Test",
            personelRolu, bilgiTeknolojileri, true);
    personelKaydet("cem.test", "Cem", "Test",
            personelRolu, bilgiTeknolojileri, true);
    personelKaydet("deniz.test", "Deniz", "Test",
            personelRolu, bilgiTeknolojileri, true);

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("page", "1")
                    .param("size", "2")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sayfa").value(1))
            .andExpect(jsonPath("$.sayfaBoyutu").value(2))
            .andExpect(jsonPath("$.toplamKayit").value(4))
            .andExpect(jsonPath("$.toplamSayfa").value(2))
            .andExpect(jsonPath("$.icerik.length()").value(2))
            .andExpect(jsonPath("$.icerik[0].ad").value("Cem"))
            .andExpect(jsonPath("$.icerik[1].ad").value("Deniz"));
}
```

- [ ] **Step 3: Security testlerini yaz**

```java
@Test
void tokenOlmadanEkipDiziniGoruntulenemez() throws Exception {
    mockMvc.perform(get("/api/ekip-dizini"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
}

@Test
void tokendakiPersonelBulunamazsa404Doner() throws Exception {
    String token = jwtService.generateToken(
            "veritabaninda-olmayan-id",
            RolAdi.PERSONEL
    );

    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
}

@Test
void pasifPrincipalGecerliTokenlaEkipDizininiGoremez() throws Exception {
    String token = token(aktor, RolAdi.PERSONEL);
    aktor.setCalisiyorMu(false);
    personelRepository.saveAndFlush(aktor);

    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
}
```

Dört rolün erişimi parameterized testle doğrulanır:

```java
@ParameterizedTest
@EnumSource(RolAdi.class)
void tumRollerEkipDizininiGoruntuleyebilir(RolAdi rolAdi)
        throws Exception {
    int rolId = switch (rolAdi) {
        case ADMIN -> 1;
        case PERSONEL -> 2;
        case YONETICI -> 3;
        case HR -> 4;
    };

    aktor.setRol(rolKaydet(rolId, rolAdi));
    aktor = personelRepository.saveAndFlush(aktor);

    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, rolAdi)))
            .andExpect(status().isOk());
}
```

- [ ] **Step 4: Testleri çalıştır ve doğru RED sonucunu gör**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: Testler derlenir, `GET /api/ekip-dizini` henüz bulunmadığı için authenticated senaryolar `404` ile başarısız olur. Bir kopyalama/derleme hatası RED kabul edilmez.

---

### Task 2: Temel liste ve pagination — GREEN

**Files:**
- Create: `backend/src/main/java/com/jforce/backend/model/dto/response/SayfaliResponse.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/PersonelYetenekRepository.java`
- Create: `backend/src/main/java/com/jforce/backend/service/EkipDiziniService.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/EkipDiziniController.java`

**Interfaces:**
- Produces: `SayfaliResponse<EkipDiziniResponse>`
- Produces: `PersonelRepository.findAktifEkipDizini(Integer, Integer, Pageable)`
- Produces: `PersonelYetenekRepository.findAllByPersonelIdsWithYetenek(List<String>)`
- Produces: `EkipDiziniService.ekipDizininiGetir(String, int, int, Integer, Integer)`

- [ ] **Step 1: Sade pagination DTO’sunu oluştur**

```java
package com.jforce.backend.model.dto.response;

import java.util.List;

public record SayfaliResponse<T>(
        List<T> icerik,
        int sayfa,
        int sayfaBoyutu,
        long toplamKayit,
        int toplamSayfa
) {
}
```

- [ ] **Step 2: Sayfalı aktif personel sorgusunu ekle**

`PersonelRepository` içine aşağıdaki metodu ekle; `Page` ve `Pageable` importlarını eklemeyi unutma:

```java
@Query(
        value = """
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman d
                WHERE p.calisiyorMu = true
                  AND (:departmanId IS NULL
                       OR d.departmanId = :departmanId)
                  AND (:yetenekId IS NULL
                       OR EXISTS (
                           SELECT 1
                           FROM PersonelYetenek py
                           WHERE py.personel = p
                             AND py.yetenek.yetenekId = :yetenekId
                       ))
                ORDER BY p.adi ASC,
                         p.soyadi ASC,
                         p.personelId ASC
                """,
        countQuery = """
                SELECT COUNT(p)
                FROM Personel p
                WHERE p.calisiyorMu = true
                  AND (:departmanId IS NULL
                       OR p.departman.departmanId = :departmanId)
                  AND (:yetenekId IS NULL
                       OR EXISTS (
                           SELECT 1
                           FROM PersonelYetenek py
                           WHERE py.personel = p
                             AND py.yetenek.yetenekId = :yetenekId
                       ))
                """
)
Page<Personel> findAktifEkipDizini(
        @Param("departmanId") Integer departmanId,
        @Param("yetenekId") Integer yetenekId,
        Pageable pageable
);
```

- [ ] **Step 3: Sayfadaki personellerin yeteneklerini tek sorguda getir**

`PersonelYetenekRepository` içine:

```java
@Query("""
        SELECT py
        FROM PersonelYetenek py
        JOIN FETCH py.yetenek y
        WHERE py.personel.personelId IN :personelIds
        ORDER BY py.personel.personelId ASC,
                 y.yetenekAdi ASC
        """)
List<PersonelYetenek> findAllByPersonelIdsWithYetenek(
        @Param("personelIds") List<String> personelIds
);
```

- [ ] **Step 4: EkipDiziniService’i oluştur**

```java
package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.EkipDiziniResponse;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EkipDiziniService {

    private final PersonelRepository personelRepository;
    private final PersonelYetenekRepository personelYetenekRepository;

    public EkipDiziniService(
            PersonelRepository personelRepository,
            PersonelYetenekRepository personelYetenekRepository
    ) {
        this.personelRepository = personelRepository;
        this.personelYetenekRepository = personelYetenekRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public SayfaliResponse<EkipDiziniResponse> ekipDizininiGetir(
            String aktorId,
            int page,
            int size,
            Integer departmanId,
            Integer yetenekId
    ) {
        paginationDogrula(page, size);

        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() ->
                        new KaynakBulunamadiException(
                                "Personel bulunamadı."
                        ));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel ekip dizinini görüntüleyemez."
            );
        }

        Page<Personel> personelSayfasi =
                personelRepository.findAktifEkipDizini(
                        departmanId,
                        yetenekId,
                        PageRequest.of(page, size)
                );

        Map<String, List<LookUpResponse>> yetenekler =
                yetenekleriPersonelIdyeGoreGetir(
                        personelSayfasi.getContent()
                );

        List<EkipDiziniResponse> icerik = personelSayfasi
                .getContent()
                .stream()
                .map(personel -> new EkipDiziniResponse(
                        personel.getAdi(),
                        personel.getSoyadi(),
                        personel.getDepartman().getDepartmanAdi(),
                        personel.getProfilFoto(),
                        yetenekler.getOrDefault(
                                personel.getPersonelId(),
                                List.of()
                        )
                ))
                .toList();

        return new SayfaliResponse<>(
                icerik,
                personelSayfasi.getNumber(),
                personelSayfasi.getSize(),
                personelSayfasi.getTotalElements(),
                personelSayfasi.getTotalPages()
        );
    }

    private void paginationDogrula(int page, int size) {
        if (page < 0) {
            throw new GecersizIstekException(
                    "Sayfa numarası sıfırdan küçük olamaz."
            );
        }

        if (size < 1 || size > 100) {
            throw new GecersizIstekException(
                    "Sayfa boyutu 1 ile 100 arasında olmalıdır."
            );
        }
    }

    private Map<String, List<LookUpResponse>>
    yetenekleriPersonelIdyeGoreGetir(List<Personel> personeller) {
        if (personeller.isEmpty()) {
            return Map.of();
        }

        List<String> personelIds = personeller.stream()
                .map(Personel::getPersonelId)
                .toList();

        Map<String, List<LookUpResponse>> sonuc = new HashMap<>();

        personelYetenekRepository
                .findAllByPersonelIdsWithYetenek(personelIds)
                .forEach(personelYetenek ->
                        sonuc.computeIfAbsent(
                                        personelYetenek
                                                .getPersonel()
                                                .getPersonelId(),
                                        key -> new java.util.ArrayList<>()
                                )
                                .add(new LookUpResponse(
                                        personelYetenek
                                                .getYetenek()
                                                .getYetenekId(),
                                        personelYetenek
                                                .getYetenek()
                                                .getYetenekAdi()
                                ))
                );

        return sonuc;
    }
}
```

- [ ] **Step 5: Controller’ı oluştur**

```java
package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.EkipDiziniResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.service.EkipDiziniService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ekip-dizini")
public class EkipDiziniController {

    private final EkipDiziniService ekipDiziniService;

    public EkipDiziniController(
            EkipDiziniService ekipDiziniService
    ) {
        this.ekipDiziniService = ekipDiziniService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipDiziniResponse>>
    ekipDizininiGetir(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer departmanId,
            @RequestParam(required = false) Integer yetenekId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ekipDiziniService.ekipDizininiGetir(
                        authentication.getName(),
                        page,
                        size,
                        departmanId,
                        yetenekId
                )
        );
    }
}
```

- [ ] **Step 6: Odaklı testleri yeniden çalıştır**

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: Task 1’deki testlerin tamamı `PASS`.

---

### Task 3: Departman/yetenek filtreleri ve toplu yetenek davranışı

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`
- Production files from Task 2 are changed only if a failing test exposes a mismatch.

**Interfaces:**
- Proves: filter `AND`, unknown ID empty page, no duplicate/count corruption, full skill list and empty skill list

- [ ] **Step 1: Test yardımcılarına yetenek ekleme metotlarını ekle**

```java
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

private void personelYetenekOlustur(
        Personel personel,
        Integer yetenekId
) {
    jdbcTemplate.update(
            """
            INSERT INTO personel_yetenek (personel_id, yetenek_id)
            VALUES (?, ?)
            """,
            personel.getPersonelId(),
            yetenekId
    );
}
```

- [ ] **Step 2: Departman ve `AND` filtresi testlerini yaz**

```java
@Test
void departmanFiltresiYalnizEslesenAktifPersonelleriDoner()
        throws Exception {
    Departman insanKaynaklari =
            departmanKaydet("İnsan Kaynakları");
    personelKaydet("ik.personel", "İpek", "Kaya",
            personelRolu, insanKaynaklari, true);

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("departmanId",
                            insanKaynaklari.getDepartmanId().toString())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.icerik[0].ad").value("İpek"))
            .andExpect(jsonPath("$.icerik[0].departman")
                    .value("İnsan Kaynakları"));
}

@Test
void departmanVeYetenekFiltreleriBirlikteAndUygular()
        throws Exception {
    Departman insanKaynaklari =
            departmanKaydet("İnsan Kaynakları");
    Integer javaId = yetenekOlustur("Java");

    Personel btJava = personelKaydet(
            "bt.java", "Bilge", "Tekin",
            personelRolu, bilgiTeknolojileri, true
    );
    Personel ikJava = personelKaydet(
            "ik.java", "İpek", "Kaya",
            personelRolu, insanKaynaklari, true
    );
    personelYetenekOlustur(btJava, javaId);
    personelYetenekOlustur(ikJava, javaId);

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("departmanId",
                            insanKaynaklari.getDepartmanId().toString())
                    .param("yetenekId", javaId.toString())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.icerik[0].ad").value("İpek"));
}
```

- [ ] **Step 3: Yetenek filtresinin bütün yetenekleri döndürdüğünü ve pagination’ı bozmadığını test et**

```java
@Test
void yetenekFiltresiEslesenPersonelinButunYetenekleriniDoner()
        throws Exception {
    Integer dockerId = yetenekOlustur("Docker");
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(aktor, javaId);
    personelYetenekOlustur(aktor, dockerId);

    mockMvc.perform(get("/api/ekip-dizini")
                    .param("yetenekId", javaId.toString())
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.icerik.length()").value(1))
            .andExpect(jsonPath("$.icerik[0].yetenekler.length()")
                    .value(2))
            .andExpect(jsonPath("$.icerik[0].yetenekler[0].ad")
                    .value("Docker"))
            .andExpect(jsonPath("$.icerik[0].yetenekler[1].ad")
                    .value("Java"));
}

@Test
void birdenFazlaYetenekPersoneliCogaltmazVeToplamKaydiBozmaz()
        throws Exception {
    Integer dockerId = yetenekOlustur("Docker");
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(aktor, dockerId);
    personelYetenekOlustur(aktor, javaId);

    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toplamKayit").value(1))
            .andExpect(jsonPath("$.icerik.length()").value(1));
}

@Test
void yetenegiOlmayanPersonelinYetenekleriBosDoner()
        throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.icerik[0].yetenekler").isEmpty());
}
```

- [ ] **Step 4: Olmayan filtre ID’sinin boş `200` döndüğünü test et**

```java
@ParameterizedTest
@ValueSource(strings = {"departmanId", "yetenekId"})
void olmayanFiltreIdsiBosSayfaDoner(String parametre)
        throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .param(parametre, "999999")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.icerik").isEmpty())
            .andExpect(jsonPath("$.toplamKayit").value(0))
            .andExpect(jsonPath("$.toplamSayfa").value(0));
}
```

- [ ] **Step 5: Filtre testlerini çalıştır**

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: bütün temel, security, pagination ve filtre testleri `PASS`. Bir filtre testi başarısızsa testi gevşetme; Task 2 sorgu veya mapping kodunu sözleşmeye göre düzelt.

---

### Task 4: Geçersiz pagination ve query tipi

**Files:**
- Modify: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Proves: range hataları ve sayısal type mismatch kontrollü `400`

- [ ] **Step 1: Geçersiz range testlerini yaz**

```java
@ParameterizedTest
@CsvSource({
        "-1,20",
        "0,0",
        "0,101"
})
void gecersizPagination400Doner(int page, int size)
        throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .param("page", String.valueOf(page))
                    .param("size", String.valueOf(size))
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
}
```

Bu testler Task 2 service validation’ıyla doğrudan yeşil olmalıdır.

- [ ] **Step 2: Sayısal tip hatası testini yaz ve RED’i doğrula**

```java
@ParameterizedTest
@ValueSource(strings = {"page", "size", "departmanId", "yetenekId"})
void sayisalOlmasiGerekenParametreMetinse400Doner(
        String parametre
) throws Exception {
    mockMvc.perform(get("/api/ekip-dizini")
                    .param(parametre, "abc")
                    .header(HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.PERSONEL)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message")
                    .value("Geçersiz istek"));
}
```

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen RED: `MethodArgumentTypeMismatchException` mevcut catch-all handler’a düştüğü için type-mismatch senaryoları `500` döner. Range testleri yeşil kalır.

- [ ] **Step 3: Type mismatch handler’ını ekle**

`GlobalExceptionHandler` importlarına:

```java
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
```

Handler:

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException ex,
        HttpServletRequest request
) {
    ErrorResponse body = new ErrorResponse(
            400,
            "Geçersiz istek",
            LocalDateTime.now(),
            request.getRequestURI()
    );

    return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(body);
}
```

- [ ] **Step 4: Tüm ekip dizini testlerini GREEN doğrula**

```powershell
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: failures/errors/skips sıfır ve `BUILD SUCCESS`.

---

### Task 5: Tam doğrulama ve belge durumunu uygulandıya çevirme

**Files:**
- Verify: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`
- Modify only after successful full suite: `API_SPEC.md`
- Modify only after successful full suite: `ROADMAP.md`
- Modify only after successful full suite: `PROGRESS.md`

**Interfaces:**
- Consumes: tamamlanmış ekip dizini endpoint’i
- Produces: tam regresyon kanıtı ve kodla uyumlu proje belgeleri

- [ ] **Step 1: Odaklı test sayısını ve sonucu kaydet**

```powershell
cd C:\Projects\intranet-portal\backend
.\mvnw.cmd -Dtest=EkipDiziniControllerIntegrationTest test
```

Beklenen: `BUILD SUCCESS`; gerçek test sayısı Maven çıktısından alınır.

- [ ] **Step 2: Tam backend paketini çalıştır**

```powershell
.\mvnw.cmd test
```

Beklenen: `BUILD SUCCESS`, failures/errors/skips sıfır. Gerçek toplam test sayısı çıktıda kaydedilir.

- [ ] **Step 3: Doğrulama sonrası belgeleri güncelle**

- `API_SPEC.md`: “planlanan/henüz uygulanmamış” ifadelerini kaldır; sözleşmeyi uygulanmış endpoint olarak göster.
- `ROADMAP.md`: ekip dizini sorgusu, departman filtresi, yetenek filtresi ve pasif personeli çıkarma maddelerini `[x]` yap; Stage 1 durumunu ekip dizini backend’i hazır olacak şekilde güncelle.
- `PROGRESS.md`: endpoint, iki toplu sorgu mimarisi, security/filter/pagination davranışları ve kullanıcının raporladığı gerçek odaklı/tam test sayılarını yeni gün kaydına ekle.
- `SECURITY.md`: pasif JWT principal’ın ekip dizininde service katmanında `403` almasını ve stateless token’ın teknik olarak iptal edilmediğini kaydet.

- [ ] **Step 4: Son kapsam kontrolünü yap**

- Response’ta `personelId`, kullanıcı adı, e-posta veya sicil numarası bulunmamalı.
- Pasif hedef hiçbir filtre kombinasyonunda listelenmemeli.
- Pasif principal aynı token ile `403` almalı.
- Eşleşmeyen filtre boş `200` dönmeli.
- Yetenek filtresi eşleşen kişinin bütün yeteneklerini döndürmeli.
- Audit tablosu listeleme nedeniyle değişmemeli.
- Tam backend test sonucu paylaşılmadan özellik “tamamlandı” sayılmamalı.
