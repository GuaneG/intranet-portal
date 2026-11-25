# Kendi Yeteneklerini Güncelleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif ve kimliği doğrulanmış personelin kendi yetenek kümesini `PUT /api/profil/yetenekler` ile atomik biçimde değiştirebilmesini sağlamak.

**Architecture:** Mevcut `ProfilController` ve `ProfilService` genişletilir. Request tam-değiştirme semantiği taşır; service mevcut ve istenen ID kümelerinin farkını hesaplayarak yalnız kaldırılan `PersonelYetenek` ilişkilerini siler, yalnız yeni ilişkileri ekler ve gerçek değişiklikte mevcut `PROFIL_GUNCELLENDI` audit akışını aynı transaction içinde kullanır.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security method security, Jakarta Validation, Spring Data JPA, JUnit 5, MockMvc, Testcontainers, MySQL 8.

## Global Constraints

- Backend Java ve test kodlarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Git komutlarını yalnız kullanıcı çalıştırır; bu plan git komutu içermez.
- Personel kimliği yalnız `Authentication#getName()` üzerinden alınır; request personel ID'si taşımaz.
- Endpoint aktif ve kimliği doğrulanmış bütün roller için kullanılabilir.
- Odaklı rol testi yalnız `PERSONEL`, `HR` ve `YONETICI` rollerini kapsar; `ADMIN` için ayrı test yazılmaz.
- Boş liste bütün yetenekleri kaldırır.
- Tekrarlanan, `null`, sıfır veya negatif ID `400 Bad Request` döndürür.
- İstenen ID'lerden biri bulunamazsa bütün istek `404 Yetenek bulunamadı.` ile reddedilir.
- Gerçek değişiklik ve audit aynı transaction içinde tamamlanır veya birlikte rollback olur.
- Aynı kümenin farklı sırada gönderilmesi `204 No Content` döndürür; persistence ve audit yan etkisi oluşturmaz.
- Audit detayı yalnız `Güncellenen alanlar: yetenekler` olur; yetenek adı veya ID içermez.
- MVP'de yetenek sayısı üst sınırı yoktur.

---

## Dosya haritası

**Oluşturulacak dosya**

- `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
  - HTTP sözleşmesi, validation, security, persistence, audit ve rollback davranışlarını gerçek MySQL ile doğrular.

**Değiştirilecek backend dosyaları**

- `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekUpdateRequest.java`
  - Listenin ve liste elemanlarının biçimsel validation kurallarını taşır.
- `backend/src/main/java/com/jforce/backend/controller/ProfilController.java`
  - `PUT /api/profil/yetenekler` HTTP girişini tanımlar ve principal kimliğini service'e aktarır.
- `backend/src/main/java/com/jforce/backend/service/ProfilService.java`
  - Aktiflik, duplicate, varlık, küme farkı, persistence ve audit iş kurallarını tek transaction içinde uygular.
- `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`
  - Eksik veya JSON'a dönüştürülemeyen request body'yi kontrollü `400` cevabına dönüştürür.

**Backend değişikliği gerektirmeyen dosyalar**

- `PersonelYetenekRepository`
  - Mevcut `findAllByPersonelIdWithYetenek`, `deleteAll` ve `saveAll` yeterlidir.
- `YetenekRepository`
  - `JpaRepository#findAllById` yeterlidir.
- `AuditEylem`
  - Mevcut `PROFIL_GUNCELLENDI` kullanılır.

**Başarılı tam testten sonra güncellenecek belgeler**

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`
- `SECURITY.md`

Belge değişiklikleri, kullanıcıdan o adımda ayrıca Markdown yazma onayı alındıktan sonra yapılır.

---

### Task 1: Tam-değiştirme, boş liste ve no-op davranışı

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/ProfilController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`

**Interfaces:**

- Consumes: `PersonelYetenekRepository#findAllByPersonelIdWithYetenek(String)`, `JpaRepository#deleteAll(Iterable)`, `JpaRepository#saveAll(Iterable)`, `YetenekRepository#findAllById(Iterable)`.
- Produces: `ProfilService#profilYeteneklerimiGuncelle(String, YetenekUpdateRequest): void` ve `PUT /api/profil/yetenekler`.

- [ ] **Step 1: Üç temel failing integration testini oluştur**

`backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java` dosyasını şu içerikle oluştur:

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ProfilYetenekControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Personel personel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");

        Rol personelRolu = new Rol();
        personelRolu.setRolId(2);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        personelRolu = rolRepository.save(personelRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        personel = new Personel();
        personel.setKullaniciAdi("test.personel");
        personel.setParolaHash("test-hash");
        personel.setAdi("Test");
        personel.setSoyadi("Personel");
        personel.setDogumTarihi(LocalDate.of(1995, 5, 10));
        personel.setePosta("test.personel@test.local");
        personel.setDepartman(departman);
        personel.setRol(personelRolu);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 10));
        personel.setYillikIzinHakki(20);

        personel = personelRepository.saveAndFlush(personel);
    }

    @Test
    void mevcutListeyiIstenenYetenekKumesiyleDegistirir() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer springId = yetenekOlustur("Spring");
        Integer dockerId = yetenekOlustur("Docker");

        personelYetenekOlustur(javaId);
        personelYetenekOlustur(springId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekIdleri": [%d, %d]
                            }
                            """.formatted(springId, dockerId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(
                List.of(springId, dockerId),
                personelinYetenekIdleri()
        );
    }

    @Test
    void bosListeButunYetenekleriKaldirir() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer springId = yetenekOlustur("Spring");
        personelYetenekOlustur(javaId);
        personelYetenekOlustur(springId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekIdleri": []
                            }
                            """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(List.of(), personelinYetenekIdleri());
    }

    @Test
    void ayniKumeFarkliSiradaGonderilirseNoOpOlur() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer springId = yetenekOlustur("Spring");
        personelYetenekOlustur(javaId);
        personelYetenekOlustur(springId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekIdleri": [%d, %d]
                            }
                            """.formatted(springId, javaId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(
                List.of(javaId, springId),
                personelinYetenekIdleri()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    private String tokenOlustur(RolAdi rolAdi) {
        return jwtService.generateToken(personel.getPersonelId(), rolAdi);
    }

    private Integer yetenekOlustur(String yetenekAdi) {
        jdbcTemplate.update(
                """
                INSERT INTO yetenek (yetenek_adi)
                VALUES (?)
                """,
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

    private void personelYetenekOlustur(Integer yetenekId) {
        jdbcTemplate.update(
                """
                INSERT INTO personel_yetenek (personel_id, yetenek_id)
                VALUES (?, ?)
                """,
                personel.getPersonelId(),
                yetenekId
        );
    }

    private List<Integer> personelinYetenekIdleri() {
        return jdbcTemplate.queryForList(
                """
                SELECT yetenek_id
                FROM personel_yetenek
                WHERE personel_id = ?
                ORDER BY yetenek_id
                """,
                Integer.class,
                personel.getPersonelId()
        );
    }
}
```

- [ ] **Step 2: Testi çalıştır ve doğru RED sonucunu doğrula**

`backend` dizininde çalıştır:

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen: Üç test de derlenir fakat henüz route bulunmadığı için beklenen
`204` yerine `404` alarak başarısız olur.

- [ ] **Step 3: Controller endpoint'ini ekle**

`ProfilController.java` importlarına şunu ekle:

```java
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
```

Sınıfın içine şu metodu ekle:

```java
@PutMapping("/yetenekler")
public ResponseEntity<Void> profilYeteneklerimiGuncelle(
        @Valid @RequestBody YetenekUpdateRequest request,
        Authentication authentication
) {
    profilService.profilYeteneklerimiGuncelle(
            authentication.getName(),
            request
    );
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 4: Service bağımlılıklarını ve tam-değiştirme algoritmasını ekle**

`ProfilService.java` importlarına şunları ekle:

```java
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.repository.YetenekRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
```

Alanlara şunu ekle:

```java
private final YetenekRepository yetenekRepository;
```

Mevcut constructor'a `YetenekRepository` parametresini ekle ve alanı ata:

```java
public ProfilService(
        PersonelRepository personelRepository,
        PersonelYetenekRepository personelYetenekRepository,
        IzinBilgileriRepository izinBilgileriRepository,
        AuditService auditService,
        YetenekRepository yetenekRepository
) {
    this.personelRepository = personelRepository;
    this.personelYetenekRepository = personelYetenekRepository;
    this.izinBilgileriRepository = izinBilgileriRepository;
    this.auditService = auditService;
    this.yetenekRepository = yetenekRepository;
}
```

Sınıfa şu service metodunu ekle:

```java
@Transactional
@PreAuthorize("isAuthenticated()")
public void profilYeteneklerimiGuncelle(
        String personelId,
        YetenekUpdateRequest request
) {
    Personel personel = personelRepository
            .findById(personelId)
            .orElseThrow(
                    () -> new KaynakBulunamadiException(
                            "Personel bulunamadı."
                    )
            );

    if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
        throw new AccessDeniedException(
                "Pasif personel yeteneklerini güncelleyemez."
        );
    }

    List<Integer> istenenIdListesi = request.yetenekIdleri();
    Set<Integer> istenenIdler = new HashSet<>(istenenIdListesi);

    if (istenenIdler.size() != istenenIdListesi.size()) {
        throw new GecersizIstekException(
                "Yetenek ID'leri tekrarlanamaz."
        );
    }

    List<Yetenek> istenenYetenekler =
            yetenekRepository.findAllById(istenenIdler);

    if (istenenYetenekler.size() != istenenIdler.size()) {
        throw new KaynakBulunamadiException(
                "Yetenek bulunamadı."
        );
    }

    List<PersonelYetenek> mevcutBaglantilar =
            personelYetenekRepository
                    .findAllByPersonelIdWithYetenek(personelId);

    Set<Integer> mevcutIdler = mevcutBaglantilar
            .stream()
            .map(
                    baglanti ->
                            baglanti.getYetenek().getYetenekId()
            )
            .collect(Collectors.toSet());

    if (mevcutIdler.equals(istenenIdler)) {
        return;
    }

    List<PersonelYetenek> silinecekBaglantilar =
            mevcutBaglantilar
                    .stream()
                    .filter(
                            baglanti ->
                                    !istenenIdler.contains(
                                            baglanti
                                                    .getYetenek()
                                                    .getYetenekId()
                                    )
                    )
                    .toList();

    List<PersonelYetenek> eklenecekBaglantilar =
            istenenYetenekler
                    .stream()
                    .filter(
                            yetenek ->
                                    !mevcutIdler.contains(
                                            yetenek.getYetenekId()
                                    )
                    )
                    .map(yetenek -> {
                        PersonelYetenek baglanti =
                                new PersonelYetenek();
                        baglanti.setId(
                                new PersonelYetenekId(
                                        personelId,
                                        yetenek.getYetenekId()
                                )
                        );
                        baglanti.setPersonel(personel);
                        baglanti.setYetenek(yetenek);
                        return baglanti;
                    })
                    .toList();

    personelYetenekRepository.deleteAll(
            silinecekBaglantilar
    );
    personelYetenekRepository.saveAll(
            eklenecekBaglantilar
    );

    auditService.logKaydetOrtakTransaction(
            AuditEylem.PROFIL_GUNCELLENDI,
            personel,
            personel.getKullaniciAdi(),
            "Güncellenen alanlar: yetenekler",
            personel
    );
}
```

- [ ] **Step 5: Üç temel testi yeniden çalıştır**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen: 3 test, 0 failure, 0 error.

- [ ] **Step 6: Task 1 kontrol kapısı**

Şunları elle kontrol et:

- Controller request'ten personel ID almıyor.
- Service kimliği yalnız `authentication.getName()` sonucundan alıyor.
- Bütün ilişkiler silinip yeniden oluşturulmuyor.
- No-op kontrolü delete, save ve audit çağrılarından önce yapılıyor.

---

### Task 2: Validation, bulunamayan yetenek ve atomiklik

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekUpdateRequest.java`
- Modify: `backend/src/main/java/com/jforce/backend/exception/GlobalExceptionHandler.java`
- Verify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`

**Interfaces:**

- Consumes: Task 1'de oluşturulan endpoint ve service metodu.
- Produces: Liste elemanları için `@NotNull` ve `@Positive` validation; okunamayan JSON için kontrollü `400`.

- [ ] **Step 1: Validation ve atomiklik test importlarını ekle**

Test sınıfının importlarına şunları ekle:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
```

- [ ] **Step 2: Bulunamayan ID ve duplicate testlerini ekle**

Test sınıfına şu metotları ekle:

```java
@Test
void birYetenekBulunamazsaButunIstekReddedilir() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [%d, 999999]
                        }
                        """.formatted(javaId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                    jsonPath("$.message")
                            .value("Yetenek bulunamadı.")
            );

    yeteneklerVeAuditDegismedi(List.of(javaId));
}

@Test
void tekrarlananYetenekIdsi400Doner() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [%d, %d]
                        }
                        """.formatted(javaId, javaId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}
```

Test sınıfına şu yardımcı metodu ekle:

```java
private void yeteneklerVeAuditDegismedi(
        List<Integer> beklenenYetenekIdleri
) {
    assertEquals(
            beklenenYetenekIdleri,
            personelinYetenekIdleri()
    );
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 3: Liste elemanı validation testlerini ekle**

```java
@Test
void nullYetenekIdsi400Doner() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [null]
                        }
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}

@ParameterizedTest
@ValueSource(ints = {0, -1})
void pozitifOlmayanYetenekIdsi400Doner(int gecersizId)
        throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(gecersizId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}
```

- [ ] **Step 4: Eksik, null ve okunamayan body testlerini ekle**

```java
@ParameterizedTest
@ValueSource(strings = {
        "{}",
        "{\"yetenekIdleri\": null}",
        "{\"yetenekIdleri\": \"1\"}"
})
void gecersizRequestGovdesi400Doner(String requestBody)
        throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}

@Test
void requestBodyGonderilmezse400Doner() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}
```

- [ ] **Step 5: Testleri çalıştır ve validation RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen:

- Bulunamayan ID ve duplicate senaryoları Task 1 service kodu sayesinde geçer.
- `null`, sıfır ve negatif liste elemanı testleri henüz DTO eleman validation'ı olmadığı için başarısız olur.
- String tipindeki veya eksik request body mevcut generic exception handler'a düşerse `500` alarak başarısız olur.

- [ ] **Step 6: DTO eleman validation'ını ekle**

`YetenekUpdateRequest.java` dosyasını şu hale getir:

```java
package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record YetenekUpdateRequest(
        @NotNull
        List<@NotNull @Positive Integer> yetenekIdleri
) {
}
```

- [ ] **Step 7: Okunamayan request body için kontrollü handler ekle**

`GlobalExceptionHandler.java` importlarına şunu ekle:

```java
import org.springframework.http.converter.HttpMessageNotReadableException;
```

Sınıfa şu handler metodunu ekle:

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex,
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

- [ ] **Step 8: Validation ve atomiklik testlerini yeniden çalıştır**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen: Bu noktaya kadar eklenen bütün test invocations geçer; hiçbir başarısız request mevcut yetenek ilişkisini veya audit tablosunu değiştirmez.

---

### Task 3: Security, rol matrisi ve güvenli audit

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
- Verify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`

**Interfaces:**

- Consumes: Task 1'deki `@PreAuthorize("isAuthenticated()")`, principal tabanlı personel yükleme, aktiflik kontrolü ve audit çağrısı.
- Produces: `401`, `403`, `404`, üç odak rol ve audit sözleşmesi için integration kanıtı.

- [ ] **Step 1: Rol testi importlarını ekle**

```java
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;
```

- [ ] **Step 2: PERSONEL, HR ve YONETICI rol testini ekle**

```java
@ParameterizedTest
@EnumSource(
        value = RolAdi.class,
        names = {"PERSONEL", "HR", "YONETICI"}
)
void aktifOdakRollerKendiYetenekleriniGuncelleyebilir(
        RolAdi rolAdi
) throws Exception {
    personelinRolunuDegistir(rolAdi);
    Integer javaId = yetenekOlustur("Java");

    String token = tokenOlustur(rolAdi);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(javaId)))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

    assertEquals(List.of(javaId), personelinYetenekIdleri());
}
```

Test sınıfına şu yardımcı metodu ekle:

```java
private void personelinRolunuDegistir(RolAdi rolAdi) {
    if (personel.getRol().getRolAdi() == rolAdi) {
        return;
    }

    int rolId = switch (rolAdi) {
        case HR -> 4;
        case YONETICI -> 3;
        case PERSONEL -> 2;
        case ADMIN -> 1;
    };

    Rol rol = new Rol();
    rol.setRolId(rolId);
    rol.setRolAdi(rolAdi);
    rol = rolRepository.save(rol);

    personel.setRol(rol);
    personel = personelRepository.saveAndFlush(personel);
}
```

Bu parameterized testte `ADMIN` adı bulunmadığı için Admin ayrı bir invocation olarak çalışmaz.

- [ ] **Step 3: Pasif, bulunamayan principal ve authentication testlerini ekle**

```java
@Test
void pasifPersonelYetenekleriniGuncelleyemez() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    personelYetenekOlustur(javaId);

    personel.setCalisiyorMu(false);
    personel = personelRepository.saveAndFlush(personel);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": []
                        }
                        """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

    yeteneklerVeAuditDegismedi(List.of(javaId));
}

@Test
void tokendakiPersonelBulunamazsa404Doner() throws Exception {
    String token = jwtService.generateToken(
            "veritabaninda-olmayan-id",
            RolAdi.PERSONEL
    );

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": []
                        }
                        """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                    jsonPath("$.message")
                            .value("Personel bulunamadı.")
            );

    assertEquals(0L, auditLogRepository.count());
}

@Test
void tokenOlmadanYeteneklerGuncellenemez() throws Exception {
    mockMvc.perform(put("/api/profil/yetenekler")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": []
                        }
                        """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));

    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 4: Audit sözleşmesi testini ekle**

```java
@Test
void gercekDegisiklikGuvenliAuditKaydiOlusturur() throws Exception {
    Integer javaId = yetenekOlustur("Java");
    Integer dockerId = yetenekOlustur("Docker");
    personelYetenekOlustur(javaId);

    String token = tokenOlustur(RolAdi.PERSONEL);

    mockMvc.perform(put("/api/profil/yetenekler")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(dockerId)))
            .andExpect(status().isNoContent());

    assertEquals(1L, auditLogRepository.count());

    Map<String, Object> audit = jdbcTemplate.queryForMap(
            """
            SELECT
                eylem_tipi,
                personel_id,
                hedef_personel_id,
                kullanici_adi,
                detay
            FROM audit_log
            """
    );

    assertEquals("PROFIL_GUNCELLENDI", audit.get("eylem_tipi"));
    assertEquals(personel.getPersonelId(), audit.get("personel_id"));
    assertEquals(
            personel.getPersonelId(),
            audit.get("hedef_personel_id")
    );
    assertEquals("test.personel", audit.get("kullanici_adi"));
    assertEquals(
            "Güncellenen alanlar: yetenekler",
            audit.get("detay")
    );
}
```

- [ ] **Step 5: Security ve audit testlerini çalıştır**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen: Bütün test invocations geçer. Özellikle bulunamayan principal testi genel route `404` cevabıyla yanlış pozitif olmamak için `Personel bulunamadı.` mesajını doğrular.

- [ ] **Step 6: Task 3 kontrol kapısı**

Şunları kontrol et:

- Controller veya service request'ten personel ID okumuyor.
- Pasif kontrolü bütün persistence işlemlerinden önce.
- Audit actor ve target aynı principal personeli.
- Audit detayı yalnız alan adını içeriyor.
- Admin için ayrı test invocation yok.

---

### Task 4: Yetenek ilişkileri ve audit için transaction rollback

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
- Verify: `backend/src/main/java/com/jforce/backend/service/ProfilService.java`

**Interfaces:**

- Consumes: `ProfilService#profilYeteneklerimiGuncelle(String, YetenekUpdateRequest)`.
- Produces: Dış transaction hata verdiğinde ilişki değişiklikleriyle audit kaydının birlikte rollback olduğuna dair gerçek MySQL kanıtı.

- [ ] **Step 1: Rollback test importlarını ekle**

```java
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.service.ProfilService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
```

- [ ] **Step 2: Rollback test helper alanını ekle**

Test sınıfındaki autowired alanlara şunu ekle:

```java
@Autowired
private ProfilYetenekRollbackTestService rollbackTestService;
```

- [ ] **Step 3: Rollback testini ekle**

```java
@Test
@WithMockUser(roles = "PERSONEL")
void disTransactionHataVerirseYeteneklerVeAuditRollbackOlur() {
    Integer javaId = yetenekOlustur("Java");
    Integer dockerId = yetenekOlustur("Docker");
    personelYetenekOlustur(javaId);

    YetenekUpdateRequest request =
            new YetenekUpdateRequest(List.of(dockerId));

    assertThrows(
            IllegalStateException.class,
            () -> rollbackTestService.guncelleVeHataFirlat(
                    personel.getPersonelId(),
                    request
            )
    );

    assertEquals(List.of(javaId), personelinYetenekIdleri());
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 4: Dış transaction test bileşenini ekle**

Test sınıfının kapanış süslü parantezinden önce şu iki nested sınıfı ekle:

```java
@TestConfiguration(proxyBeanMethods = false)
static class RollbackTestConfiguration {

    @Bean
    ProfilYetenekRollbackTestService rollbackTestService(
            ProfilService profilService
    ) {
        return new ProfilYetenekRollbackTestService(profilService);
    }
}

static class ProfilYetenekRollbackTestService {

    private final ProfilService profilService;

    ProfilYetenekRollbackTestService(ProfilService profilService) {
        this.profilService = profilService;
    }

    @Transactional
    public void guncelleVeHataFirlat(
            String personelId,
            YetenekUpdateRequest request
    ) {
        profilService.profilYeteneklerimiGuncelle(
                personelId,
                request
        );

        throw new IllegalStateException(
                "Rollback davranışını doğrulamak için test hatası."
        );
    }
}
```

- [ ] **Step 5: Rollback dahil odaklı sınıfı çalıştır**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Beklenen: Bütün test invocations geçer; rollback testinden sonra eski `Java` ilişkisi durur, yeni `Docker` ilişkisi ve audit kaydı bulunmaz.

---

### Task 5: Tam regresyon ve dokümantasyon

**Files:**

- Verify: `backend/src/test/java/com/jforce/backend/ProfilYetenekControllerIntegrationTest.java`
- Verify: bütün `backend` testleri
- Modify after explicit approval: `API_SPEC.md`
- Modify after explicit approval: `ROADMAP.md`
- Modify after explicit approval: `PROGRESS.md`
- Modify after explicit approval: `SECURITY.md`

**Interfaces:**

- Consumes: Task 1–4 sonunda geçen özellik.
- Produces: Tam backend regresyon sonucu ve kodla uyumlu proje belgeleri.

- [ ] **Step 1: Odaklı integration test sonucunu kaydet**

```powershell
.\mvnw.cmd -Dtest=ProfilYetenekControllerIntegrationTest test
```

Sonuçta `Tests run`, `Failures`, `Errors` ve `Skipped` sayılarını kaydet. Başarısızlık varsa tam backend testine geçmeden önce yalnız ilgili davranışı düzelt.

- [ ] **Step 2: Tam backend regresyonunu çalıştır**

```powershell
.\mvnw.cmd test
```

Beklenen: `BUILD SUCCESS`, 0 failure ve 0 error.

- [ ] **Step 3: Modül tamamlanma kontrolünü yap**

`CONTRIBUTING.md` içindeki backend modül tamamlanma listesini bu özellikle sınırlı olarak kontrol et:

- Request validation var.
- Authentication ve principal ownership backend'de uygulanıyor.
- Pasif personel reddediliyor.
- Entity doğrudan response yapılmıyor.
- Kontrollü `400`, `401`, `403`, `404` davranışları test ediliyor.
- Gerçek MySQL ile ilişki ve rollback test ediliyor.
- Audit hassas değer içermiyor.
- Odaklı ve tam test sonuçları mevcut.

- [ ] **Step 4: Markdown yazma onayı iste**

Kullanıcıya `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` ve `SECURITY.md` dosyalarının kodla uyumlu güncellenmesi için açık onay sor. Onay gelmeden bu dört dosyayı değiştirme.

- [ ] **Step 5: Onaydan sonra API ve roadmap sözleşmesini güncelle**

`API_SPEC.md` Profil tablosuna şunu ekle:

```markdown
| PUT | `/api/profil/yetenekler` | kimliği doğrulanmış tüm roller |
```

Profil bölümüne şu davranışları ekle:

```markdown
- `PUT /api/profil/yetenekler` request'i `{ "yetenekIdleri": [1, 3, 7] }` biçimindedir ve gönderilen liste personelin işlem sonrasındaki yetenek kümesinin tamamını temsil eder.
- Boş liste bütün yetenekleri kaldırır. Liste sırası anlam taşımaz; aynı kümenin farklı sırada gönderildiği no-op istek `204 No Content` döner ve persistence veya audit yan etkisi oluşturmaz.
- `null`, sıfır, negatif veya tekrarlanan yetenek ID'leri `400 Bad Request`; bulunamayan ID'lerden biri bile varsa bütün istek `404 Yetenek bulunamadı.` ile reddedilir.
- Gerçek değişiklik `204 No Content` döner ve aynı transaction içinde `PROFIL_GUNCELLENDI` audit kaydı oluşturur. Audit detayı yalnız `Güncellenen alanlar: yetenekler` olur.
- Personel kimliği yalnız JWT principal'dan alınır. Pasif personel `403`, bulunamayan principal `404`, kimlik doğrulanmamış istek `401` alır.
```

Personel bölümündeki şu eski ifadeyi kaldır veya yalnız profil fotoğrafı eksiğini anlatacak biçimde değiştir:

```markdown
- Kendi yeteneklerini değiştirme ve profil fotoğrafı yükleme/silme endpointleri henüz uygulanmamıştır.
```

Yeni ifade:

```markdown
- Profil fotoğrafı yükleme/silme endpointleri henüz uygulanmamıştır.
```

Lookup bölümündeki toplu güncelleme yolunun sonra kesinleşeceğini söyleyen eski maddeyi kaldır:

```markdown
- Birden fazla yeteneğin profile atanması için toplu güncelleme endpointinin method ve yolu ilgili profil güncelleme tasarımında kesinleştirilecektir.
```

`ROADMAP.md` içindeki maddeyi güncelle:

```markdown
- [x] Personel yeteneklerini güncelleme akışını oluştur
    - Endpoint `PUT /api/profil/yetenekler`; tam-değiştirme ve küme semantiği kullanılır.
    - Boş liste bütün yetenekleri kaldırır; aynı küme no-op olarak `204` döner ve audit oluşturmaz.
    - Değişiklikler yalnız JWT principal personelinde, fark tabanlı ve audit ile aynı transaction içinde uygulanır.
```

- [ ] **Step 6: Onaydan sonra progress ve security belgelerini güncelle**

`PROGRESS.md` sonuna testte gözlenen gerçek sayıları kullanarak yeni bir bölüm ekle:

```markdown
## 2026-07-30 — Own skill replacement backend

- Added `PUT /api/profil/yetenekler` with full-replacement set semantics for the current JWT principal.
- Validated null, non-positive and duplicate IDs; rejected an unknown skill atomically without partial relationship changes.
- Applied only relationship differences, kept same-set requests audit-free, and recorded real changes with `PROFIL_GUNCELLENDI` in the shared transaction.
- Covered active PERSONEL/HR/YONETICI access, inactive and missing principals, unauthenticated requests, safe audit content and transaction rollback with MySQL/Testcontainers.
- Focused verification reported by the developer: 20 tests, 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 166 tests, 0 failures, 0 errors and 0 skipped; build successful.
```

Bu iki sayı planın mevcut test envanterine göre beklenen değerlerdir. Gerçek test
çıktısı farklı sayı verirse yalnız doğrulanmış gerçek sayıları belgeye yaz.

`SECURITY.md` authorization/ownership bölümüne şunu ekle:

```markdown
`PUT /api/profil/yetenekler` kimliği doğrulanmış bütün rollere açıktır; güncellenecek personel yalnız JWT principal'dan belirlenir ve request personel kimliği taşımaz. Service principal personelinin mevcut ve aktif olduğunu her istekte doğrular. İstenen bütün yetenek ID'leri yazma işleminden önce doğrulanır; tek bir bulunamayan ID bile isteği kısmi değişiklik yapılmadan reddeder. Gerçek ilişki değişiklikleriyle güvenli `PROFIL_GUNCELLENDI` audit kaydı aynı transaction içinde tamamlanır veya birlikte rollback olur. Audit detayına yetenek adı veya ID yazılmaz.
```

- [ ] **Step 7: Markdown bütünlüğünü doğrula**

Belge düzenlemesinde önce normal `apply_patch` kullan. Başarısız olursa, alınmış onay kapsamında yalnız bu dört Markdown dosyası için belirtilen UTF-8 PowerShell/Python yöntemine geç.

Doğrulamalar:

- Dört dosya UTF-8 olarak okunabiliyor.
- `\uFFFD`, `Ã` ve `Ä` mojibake işaretleri yeni metinde oluşmadı.
- Türkçe karakterler `?` karakterine dönüşmedi.
- `PUT /api/profil/yetenekler` API tablosunda tam bir kez bulunuyor.
- Roadmap maddesi tam bir kez `[x]` olarak bulunuyor ve eski `[ ]` maddesi kalmadı.
- API_SPEC içindeki “henüz uygulanmamıştır” ve “kesinleştirilecektir” eski yetenek maddeleri kaldırıldı.
- Yeni progress ve security bölümleri birer kez eklendi.
- Her dosyanın mevcut CRLF/LF biçimi korunuyor.

---

## Son kabul ölçütleri

- `PUT /api/profil/yetenekler` yalnız JWT principal personelini etkiler.
- Tam-değiştirme ve boş liste davranışları integration testle kanıtlanır.
- Aynı küme farklı sırada no-op olur.
- Duplicate, `null`, sıfır, negatif ve okunamayan istekler kontrollü `400` olur.
- Bilinmeyen tek bir ID bütün isteği `404` ile atomik reddeder.
- Aktif `PERSONEL`, `HR`, `YONETICI` testleri geçer; Admin için ayrı test yoktur.
- Pasif principal `403`, bulunamayan principal `404`, authentication olmayan istek `401` alır.
- Gerçek değişiklik güvenli audit oluşturur.
- Dış transaction hatasında ilişkiler ve audit birlikte rollback olur.
- Odaklı test ve bütün backend testi başarılıdır.
- Belgeler yalnız ayrıca alınan onaydan sonra kod ve doğrulanmış test sonuçlarıyla eşleştirilir.
