# Yetenek Yönetimi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif HR ve Admin kullanıcılarının yetenek kataloğuna yeni tanım ekleyebilmesini ve mevcut bir yeteneği personel ilişkilerini bozmadan yeniden adlandırabilmesini sağlamak.

**Architecture:** Mevcut salt okunur `LookupController` ve `LookupService` korunur. `POST /api/skills` ve `PATCH /api/skills/{yetenekId}` işlemleri yeni `YetenekYonetimController` ve `YetenekYonetimService` içinde; HR/Admin method-security, normalizasyon, duplicate kontrolü, transaction ve audit sorumluluklarıyla uygulanır.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security method security, Jakarta Validation, Spring Data JPA, JUnit 5, Mockito, MockMvc, Testcontainers, MySQL 8.

## Global Constraints

- Backend Java ve test kodlarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Git komutlarını yalnız kullanıcı çalıştırır; bu plan git komutu içermez.
- `POST` ve `PATCH` yalnız aktif `HR` ve `ADMIN` rollerine açıktır.
- Aktör kimliği yalnız JWT principal'dan alınır; request aktör veya personel ID'si taşımaz.
- Yetenek adı trim edilir, kullanıcının harf biçimi korunur ve normalize edilmiş uzunluk 1–50 karakter olur.
- Duplicate karşılaştırması büyük/küçük harf duyarsızdır ve `409 Yetenek zaten mevcut.` döner.
- Oluşturma `201 Created` ve `{ id, ad }`; güncelleme/no-op `204 No Content` döner.
- Aynı normalize edilmiş tam metin no-op olur; persistence ve audit yan etkisi oluşturmaz.
- Yalnız harf biçimi değişikliği gerçek güncelleme ve audit üretir.
- Rename aynı entity ID'sini ve bütün `PersonelYetenek` ilişkilerini korur.
- Oluşturma ve güncelleme audit'i ana transaction'a katılır ve dış hatada birlikte rollback olur.
- Silme, pasifleştirme, aktifleştirme ve tablo şeması değişikliği kapsam dışıdır.
- Mevcut `GET /api/skills` lookup akışı taşınmaz veya yeniden tasarlanmaz.

---

## Dosya haritası

**Oluşturulacak üretim dosyaları**

- `backend/src/main/java/com/jforce/backend/controller/YetenekYonetimController.java`
- `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`
- `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekAdiRequest.java`

**Kaldırılacak dosya**

- `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekCreateRequest.java`
  - Hiçbir yerde kullanılmıyor; yerine iki endpoint'in ortak sözleşmesini anlatan `YetenekAdiRequest` gelir.

**Değiştirilecek üretim dosyaları**

- `backend/src/main/java/com/jforce/backend/repository/YetenekRepository.java`
- `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Oluşturulacak test dosyaları**

- `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`
- `backend/src/test/java/com/jforce/backend/YetenekYonetimServiceTest.java`

**Değişmeden kalacak üretim dosyaları**

- `backend/src/main/java/com/jforce/backend/controller/LookupController.java`
- `backend/src/main/java/com/jforce/backend/service/LookupService.java`
- `backend/src/main/java/com/jforce/backend/model/entity/Yetenek.java`

**Tam regresyondan sonra, ayrıca alınan Markdown onayıyla güncellenecek belgeler**

- `API_SPEC.md`
- `ROADMAP.md`
- `PROGRESS.md`
- `SECURITY.md`

---

### Task 1: Oluşturma ve ilişkiyi koruyan rename

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`
- Create: `backend/src/main/java/com/jforce/backend/controller/YetenekYonetimController.java`
- Create: `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`
- Create: `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekAdiRequest.java`
- Delete: `backend/src/main/java/com/jforce/backend/model/dto/request/YetenekCreateRequest.java`

**Interfaces:**

- Produces: `YetenekYonetimService#yetenekOlustur(String, YetenekAdiRequest): LookUpResponse`
- Produces: `YetenekYonetimService#yetenekGuncelle(String, Integer, YetenekAdiRequest): void`
- Produces: `POST /api/skills` ve `PATCH /api/skills/{yetenekId}`

- [ ] **Step 1: Temel failing integration test sınıfını oluştur**

`YetenekYonetimControllerIntegrationTest.java`:

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class YetenekYonetimControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IzinBilgileriRepository izinBilgileriRepository;

    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Rol hrRolu;
    private Departman departman;
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

        hrRolu = rolKaydet(4, RolAdi.HR);
        departman = departmanKaydet("İnsan Kaynakları");
        aktor = personelKaydet(
                "hr.aktor",
                "HR",
                "Aktör",
                hrRolu,
                true
        );
    }

    @Test
    void hrYetenekOlustururVeTrimEdilmisKaydiDoner()
            throws Exception {
        mockMvc.perform(post("/api/skills")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekAdi": "  Node.js  "
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ad").value("Node.js"))
                .andExpect(jsonPath("$.yetenekId").doesNotExist())
                .andExpect(jsonPath("$.yetenekAdi").doesNotExist());

        assertEquals(1, yetenekSayisi());
        assertEquals(
                "Node.js",
                jdbcTemplate.queryForObject(
                        "SELECT yetenek_adi FROM yetenek",
                        String.class
                )
        );
    }

    @Test
    void renameAyniIdVePersonelYetenekIliskisiniKorur()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(
                aktor.getPersonelId(),
                javaId
        );

        mockMvc.perform(patch("/api/skills/{yetenekId}", javaId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekAdi": "Java Backend"
                            }
                            """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals("Java Backend", yetenekAdi(javaId));
        assertEquals(
                1,
                personelYetenekIliskiSayisi(
                        aktor.getPersonelId(),
                        javaId
                )
        );
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);
        return departmanRepository.save(yeniDepartman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
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

    private Integer yetenekOlustur(String ad) {
        jdbcTemplate.update(
                "INSERT INTO yetenek (yetenek_adi) VALUES (?)",
                ad
        );
        return jdbcTemplate.queryForObject(
                "SELECT yetenek_id FROM yetenek WHERE yetenek_adi = ?",
                Integer.class,
                ad
        );
    }

    private void personelYetenekOlustur(
            String personelId,
            Integer yetenekId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO personel_yetenek (personel_id, yetenek_id)
                VALUES (?, ?)
                """,
                personelId,
                yetenekId
        );
    }

    private String yetenekAdi(Integer yetenekId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT yetenek_adi
                FROM yetenek
                WHERE yetenek_id = ?
                """,
                String.class,
                yetenekId
        );
    }

    private int yetenekSayisi() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM yetenek",
                Integer.class
        );
    }

    private int personelYetenekIliskiSayisi(
            String personelId,
            Integer yetenekId
    ) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM personel_yetenek
                WHERE personel_id = ? AND yetenek_id = ?
                """,
                Integer.class,
                personelId,
                yetenekId
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

- [ ] **Step 2: RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 2 test de derlenir ve route bulunmadığı için beklenen `201/204`
yerine `404` alarak failure verir; error oluşmaz.

- [ ] **Step 3: Ortak request DTO'sunu oluştur**

`YetenekCreateRequest.java` dosyasını kaldır. Yerine
`YetenekAdiRequest.java` oluştur:

```java
package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record YetenekAdiRequest(
        @NotBlank
        String yetenekAdi
) {
}
```

- [ ] **Step 4: Temel yönetim service'ini oluştur**

```java
package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.repository.YetenekRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YetenekYonetimService {

    private final YetenekRepository yetenekRepository;

    public YetenekYonetimService(
            YetenekRepository yetenekRepository
    ) {
        this.yetenekRepository = yetenekRepository;
    }

    @Transactional
    public LookUpResponse yetenekOlustur(
            String aktorId,
            YetenekAdiRequest request
    ) {
        String normalizeAd = request.yetenekAdi().trim();

        Yetenek yetenek = new Yetenek();
        yetenek.setYetenekAdi(normalizeAd);
        yetenek = yetenekRepository.saveAndFlush(yetenek);

        return new LookUpResponse(
                yetenek.getYetenekId(),
                yetenek.getYetenekAdi()
        );
    }

    @Transactional
    public void yetenekGuncelle(
            String aktorId,
            Integer yetenekId,
            YetenekAdiRequest request
    ) {
        Yetenek yetenek = yetenekRepository
                .findById(yetenekId)
                .orElseThrow(
                        () -> new KaynakBulunamadiException(
                                "Yetenek bulunamadı."
                        )
                );

        yetenek.setYetenekAdi(
                request.yetenekAdi().trim()
        );
        yetenekRepository.saveAndFlush(yetenek);
    }
}
```

Bu ilk GREEN adımında `aktorId` bilinçli olarak henüz kullanılmaz. Security ve
aktif aktör davranışı kendi failing testinden sonra Task 4'te eklenir.

- [ ] **Step 5: Controller'ı oluştur**

```java
package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.service.YetenekYonetimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class YetenekYonetimController {

    private final YetenekYonetimService yetenekYonetimService;

    public YetenekYonetimController(
            YetenekYonetimService yetenekYonetimService
    ) {
        this.yetenekYonetimService = yetenekYonetimService;
    }

    @PostMapping
    public ResponseEntity<LookUpResponse> yetenekOlustur(
            @Valid @RequestBody YetenekAdiRequest request,
            Authentication authentication
    ) {
        LookUpResponse response =
                yetenekYonetimService.yetenekOlustur(
                        authentication.getName(),
                        request
                );
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{yetenekId}")
    public ResponseEntity<Void> yetenekGuncelle(
            @PathVariable Integer yetenekId,
            @Valid @RequestBody YetenekAdiRequest request,
            Authentication authentication
    ) {
        yetenekYonetimService.yetenekGuncelle(
                authentication.getName(),
                yetenekId,
                request
        );
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 6: Task 1 GREEN sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 2 test, 0 failure, 0 error.

---

### Task 2: Request ve path validation

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`

**Interfaces:**

- Consumes: Task 1 endpoint ve service imzaları.
- Produces: Normalize edilmiş 1–50 karakter adı ve pozitif path ID kuralı.

- [ ] **Step 1: Validation test importlarını ekle**

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
```

- [ ] **Step 2: Body validation testlerini ekle**

```java
@ParameterizedTest
@ValueSource(strings = {
        "{}",
        "{\"yetenekAdi\": null}",
        "{\"yetenekAdi\": \"\"}",
        "{\"yetenekAdi\": \"   \"}"
})
void gecersizYetenekAdi400Doner(String requestBody)
        throws Exception {
    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    assertEquals(0, yetenekSayisi());
    assertEquals(0L, auditLogRepository.count());
}

@Test
void elliKarakterdenUzunNormalizeAd400Doner()
        throws Exception {
    String uzunAd = "a".repeat(51);

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "%s"
                        }
                        """.formatted(uzunAd)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

    assertEquals(0, yetenekSayisi());
}

@Test
void requestBodyGonderilmezse400Doner()
        throws Exception {
    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
}
```

- [ ] **Step 3: Path ID testlerini ekle**

```java
@ParameterizedTest
@ValueSource(ints = {0, -1})
void pozitifOlmayanYetenekIdsi400Doner(int yetenekId)
        throws Exception {
    mockMvc.perform(patch("/api/skills/{yetenekId}", yetenekId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
}

@Test
void metinYetenekIdsi400Doner() throws Exception {
    mockMvc.perform(patch("/api/skills/metin")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
}

@Test
void bulunamayanYetenek404Doner() throws Exception {
    mockMvc.perform(patch("/api/skills/999999")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                    jsonPath("$.message")
                            .value("Yetenek bulunamadı.")
            );
}
```

- [ ] **Step 4: RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 12 test invocation çalışır. Eksik/null/boş body ve metin ID mevcut
global handler/validation sayesinde geçebilir; 51 karakter ile sıfır ve negatif
ID senaryoları beklenen `400` yerine `500/404` alarak failure verir.

- [ ] **Step 5: Normalize uzunluk ve pozitif ID kurallarını ekle**

Service importlarına ekle:

```java
import com.jforce.backend.exception.GecersizIstekException;
```

İki metotta doğrudan `trim()` yerine şu yardımcı metodu kullan:

```java
private String yetenekAdiniNormalizeEt(String yetenekAdi) {
    String normalizeAd = yetenekAdi.trim();

    if (normalizeAd.length() > 50) {
        throw new GecersizIstekException(
                "Yetenek adı en fazla 50 karakter olmalıdır."
        );
    }
    return normalizeAd;
}
```

`yetenekGuncelle` metodunda repository sorgusundan önce ekle:

```java
if (yetenekId == null || yetenekId <= 0) {
    throw new GecersizIstekException(
            "Yetenek ID pozitif olmalıdır."
    );
}
```

- [ ] **Step 6: Task 2 GREEN sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 12 test invocation, 0 failure, 0 error.

---

### Task 3: Case-insensitive duplicate kuralları

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/repository/YetenekRepository.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`

**Interfaces:**

- Produces: `existsByYetenekAdiIgnoreCase(String): boolean`
- Produces: `existsByYetenekAdiIgnoreCaseAndYetenekIdNot(String, Integer): boolean`

- [ ] **Step 1: Duplicate create testlerini ekle**

```java
@ParameterizedTest
@ValueSource(strings = {
        "Java",
        "JAVA",
        "  Java  "
})
void duplicateYetenekOlusturma409Doner(String istenenAd)
        throws Exception {
    yetenekOlustur("Java");

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "%s"
                        }
                        """.formatted(istenenAd)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(
                    jsonPath("$.message")
                            .value("Yetenek zaten mevcut.")
            );

    assertEquals(1, yetenekSayisi());
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 2: Duplicate update testini ekle**

```java
@Test
void baskaYetenegeDuplicateOlacakGuncelleme409Doner()
        throws Exception {
    yetenekOlustur("Java");
    Integer dockerId = yetenekOlustur("Docker");

    mockMvc.perform(patch("/api/skills/{yetenekId}", dockerId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": " java "
                        }
                        """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(
                    jsonPath("$.message")
                            .value("Yetenek zaten mevcut.")
            );

    assertEquals("Docker", yetenekAdi(dockerId));
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 3: RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 16 test invocation. Duplicate istekler mevcut unique constraint
nedeniyle ham persistence hatasına düşüp kontrollü `409` sözleşmesini
sağlamaz; en az bu dört invocation failure verir.

- [ ] **Step 4: Repository duplicate metotlarını ekle**

```java
boolean existsByYetenekAdiIgnoreCase(String yetenekAdi);

boolean existsByYetenekAdiIgnoreCaseAndYetenekIdNot(
        String yetenekAdi,
        Integer yetenekId
);
```

- [ ] **Step 5: Service duplicate ön kontrollerini ekle**

Oluşturma metodunda entity oluşturmadan önce:

```java
if (yetenekRepository.existsByYetenekAdiIgnoreCase(
        normalizeAd
)) {
    throw new KaynakCakismasiException(
            "Yetenek zaten mevcut."
    );
}
```

Güncelleme metodunda entity adını değiştirmeden önce:

```java
if (yetenekRepository
        .existsByYetenekAdiIgnoreCaseAndYetenekIdNot(
                normalizeAd,
                yetenekId
        )) {
    throw new KaynakCakismasiException(
            "Yetenek zaten mevcut."
    );
}
```

Service importlarına ekle:

```java
import com.jforce.backend.exception.KaynakCakismasiException;
```

- [ ] **Step 6: Task 3 GREEN sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 16 test invocation, 0 failure, 0 error.

---

### Task 4: Security, audit, case düzeltme ve no-op

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`

**Interfaces:**

- Consumes: Task 1–3 service ve repository imzaları.
- Produces: HR/Admin method-security, aktif aktör doğrulaması ve iki audit olayı.

- [ ] **Step 1: Test importlarını ekle**

```java
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
```

- [ ] **Step 2: Rol ve principal testlerini ekle**

```java
@Test
void adminYetenekOlusturabilir() throws Exception {
    aktorunRolunuDegistir(RolAdi.ADMIN);

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.ADMIN)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isCreated());
}

@Test
void adminYetenekGuncelleyebilir() throws Exception {
    aktorunRolunuDegistir(RolAdi.ADMIN);
    Integer javaId = yetenekOlustur("Java");

    mockMvc.perform(patch("/api/skills/{yetenekId}", javaId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.ADMIN)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java Backend"
                        }
                        """))
            .andExpect(status().isNoContent());

    assertEquals("Java Backend", yetenekAdi(javaId));
}

@ParameterizedTest
@EnumSource(
        value = RolAdi.class,
        names = {"PERSONEL", "YONETICI"}
)
void personelVeYoneticiYetenekYonetemez(RolAdi rolAdi)
        throws Exception {
    aktorunRolunuDegistir(rolAdi);

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, rolAdi)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

    mockMvc.perform(patch("/api/skills/999999")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, rolAdi)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

    assertEquals(0, yetenekSayisi());
    assertEquals(0L, auditLogRepository.count());
}

@Test
void tokenOlmadanYetenekYonetilemez() throws Exception {
    mockMvc.perform(post("/api/skills")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));

    mockMvc.perform(patch("/api/skills/999999")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
}

@Test
void tokendakiHrBulunamazsa404Doner() throws Exception {
    String token = jwtService.generateToken(
            "veritabaninda-olmayan-id",
            RolAdi.HR
    );

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(
                    jsonPath("$.message")
                            .value("Personel bulunamadı.")
            );

    mockMvc.perform(patch("/api/skills/999999")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isNotFound())
            .andExpect(
                    jsonPath("$.message")
                            .value("Personel bulunamadı.")
            );
}

@Test
void pasifHrYetenekYonetemez() throws Exception {
    aktor.setCalisiyorMu(false);
    aktor = personelRepository.saveAndFlush(aktor);

    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

    mockMvc.perform(patch("/api/skills/999999")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));

    assertEquals(0, yetenekSayisi());
}
```

- [ ] **Step 3: Audit, case düzeltme ve no-op testlerini ekle**

```java
@Test
void yetenekOlusturmaGuvenliAuditOlusturur()
        throws Exception {
    mockMvc.perform(post("/api/skills")
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "  Node.js  "
                        }
                        """))
            .andExpect(status().isCreated());

    Map<String, Object> audit = tekAuditKaydi();
    assertEquals("YETENEK_EKLENDI", audit.get("eylem_tipi"));
    assertEquals(
            aktor.getPersonelId(),
            audit.get("personel_id")
    );
    assertEquals(
            "hr.aktor",
            audit.get("kullanici_adi")
    );
    assertNull(audit.get("hedef_personel_id"));
    assertEquals(
            "Yetenek oluşturuldu: Node.js",
            audit.get("detay")
    );
}

@Test
void yalnizHarfBicimiDegisikligiGuncellenirVeAuditOlur()
        throws Exception {
    Integer javaId = yetenekOlustur("java");

    mockMvc.perform(patch("/api/skills/{yetenekId}", javaId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "Java"
                        }
                        """))
            .andExpect(status().isNoContent());

    assertEquals("Java", yetenekAdi(javaId));

    Map<String, Object> audit = tekAuditKaydi();
    assertEquals(
            "YETENEK_GUNCELLENDI",
            audit.get("eylem_tipi")
    );
    assertEquals(
            "Yetenek güncellendi: java -> Java",
            audit.get("detay")
    );
    assertNull(audit.get("hedef_personel_id"));
}

@Test
void ayniNormalizeAdNoOpOlur() throws Exception {
    Integer javaId = yetenekOlustur("Java");

    mockMvc.perform(patch("/api/skills/{yetenekId}", javaId)
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            "Bearer " + token(aktor, RolAdi.HR)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "yetenekAdi": "  Java  "
                        }
                        """))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));

    assertEquals("Java", yetenekAdi(javaId));
    assertEquals(0L, auditLogRepository.count());
}
```

Yardımcı metotları ekle:

```java
private void aktorunRolunuDegistir(RolAdi rolAdi) {
    if (aktor.getRol().getRolAdi() == rolAdi) {
        return;
    }

    int rolId = switch (rolAdi) {
        case ADMIN -> 1;
        case PERSONEL -> 2;
        case YONETICI -> 3;
        case HR -> 4;
    };

    Rol rol = rolRepository
            .findById(rolId)
            .orElseGet(() -> rolKaydet(rolId, rolAdi));

    aktor.setRol(rol);
    aktor = personelRepository.saveAndFlush(aktor);
}

private Map<String, Object> tekAuditKaydi() {
    assertEquals(1L, auditLogRepository.count());
    return jdbcTemplate.queryForMap(
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
}
```

- [ ] **Step 4: RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 26 test invocation. `PERSONEL/YONETICI`, missing/pasif principal,
audit ve no-op senaryoları mevcut service'te uygulanmadığı için failure verir.

- [ ] **Step 5: Audit enum değerlerini ekle**

`AuditEylem` enum'unun sonunu şu hale getir:

```java
PROFIL_GUNCELLENDI,
YETENEK_EKLENDI,
YETENEK_GUNCELLENDI
```

- [ ] **Step 6: Service bağımlılıkları ve security kuralını ekle**

Importlar:

```java
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
```

Alanlar ve constructor:

```java
private final YetenekRepository yetenekRepository;
private final PersonelRepository personelRepository;
private final AuditService auditService;

public YetenekYonetimService(
        YetenekRepository yetenekRepository,
        PersonelRepository personelRepository,
        AuditService auditService
) {
    this.yetenekRepository = yetenekRepository;
    this.personelRepository = personelRepository;
    this.auditService = auditService;
}
```

İki public metoda ekle:

```java
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
```

İki metodun ilk satırında aktörü yükle:

```java
Personel aktor = aktifAktoruDogrula(aktorId);
```

Yardımcı metot:

```java
private Personel aktifAktoruDogrula(String aktorId) {
    Personel aktor = personelRepository
            .findById(aktorId)
            .orElseThrow(
                    () -> new KaynakBulunamadiException(
                            "Personel bulunamadı."
                    )
            );

    if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
        throw new AccessDeniedException(
                "Pasif personel yetenek yönetimi yapamaz."
        );
    }
    return aktor;
}
```

- [ ] **Step 7: Audit ve no-op davranışını ekle**

Oluşturmada `saveAndFlush` sonrasına:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.YETENEK_EKLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Yetenek oluşturuldu: " + normalizeAd,
        null
);
```

Güncellemede duplicate kontrolünden önce:

```java
String eskiAd = yetenek.getYetenekAdi();

if (eskiAd.equals(normalizeAd)) {
    return;
}
```

Güncellemede `saveAndFlush` sonrasına:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.YETENEK_GUNCELLENDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Yetenek güncellendi: "
                + eskiAd
                + " -> "
                + normalizeAd,
        null
);
```

- [ ] **Step 8: Task 4 GREEN sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 26 test invocation, 0 failure, 0 error.

---

### Task 5: Yetenek ve audit transaction rollback

**Files:**

- Test: `backend/src/test/java/com/jforce/backend/YetenekYonetimControllerIntegrationTest.java`

**Interfaces:**

- Consumes: `YetenekYonetimService#yetenekOlustur` ve `#yetenekGuncelle`.
- Produces: Dış hata durumunda yetenek ve audit'in birlikte rollback kanıtı.

- [ ] **Step 1: Rollback importlarını ve bean alanını ekle**

```java
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.service.YetenekYonetimService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;
```

Autowired alan:

```java
@Autowired
private YetenekRollbackTestService rollbackTestService;
```

- [ ] **Step 2: Oluşturma ve güncelleme rollback testlerini ekle**

```java
@Test
@WithMockUser(roles = "HR")
void disHataOlusturmaVeAuditiRollbackYapar() {
    assertThrows(
            IllegalStateException.class,
            () -> rollbackTestService.olusturVeHataFirlat(
                    aktor.getPersonelId(),
                    new YetenekAdiRequest("Java")
            )
    );

    assertEquals(0, yetenekSayisi());
    assertEquals(0L, auditLogRepository.count());
}

@Test
@WithMockUser(roles = "HR")
void disHataGuncellemeVeAuditiRollbackYapar() {
    Integer javaId = yetenekOlustur("Java");

    assertThrows(
            IllegalStateException.class,
            () -> rollbackTestService.guncelleVeHataFirlat(
                    aktor.getPersonelId(),
                    javaId,
                    new YetenekAdiRequest("Java Backend")
            )
    );

    assertEquals("Java", yetenekAdi(javaId));
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 3: Nested transaction test bileşenini ekle**

```java
@TestConfiguration(proxyBeanMethods = false)
static class RollbackTestConfiguration {

    @Bean
    YetenekRollbackTestService rollbackTestService(
            YetenekYonetimService service
    ) {
        return new YetenekRollbackTestService(service);
    }
}

static class YetenekRollbackTestService {

    private final YetenekYonetimService service;

    YetenekRollbackTestService(
            YetenekYonetimService service
    ) {
        this.service = service;
    }

    @Transactional
    public void olusturVeHataFirlat(
            String aktorId,
            YetenekAdiRequest request
    ) {
        service.yetenekOlustur(aktorId, request);
        throw new IllegalStateException(
                "Rollback testi için dış hata."
        );
    }

    @Transactional
    public void guncelleVeHataFirlat(
            String aktorId,
            Integer yetenekId,
            YetenekAdiRequest request
    ) {
        service.yetenekGuncelle(
                aktorId,
                yetenekId,
                request
        );
        throw new IllegalStateException(
                "Rollback testi için dış hata."
        );
    }
}
```

- [ ] **Step 4: Rollback testlerini doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimControllerIntegrationTest test
```

Beklenen: 28 test invocation, 0 failure, 0 error.

---

### Task 6: Eşzamanlı unique constraint fallback

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/YetenekYonetimServiceTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/YetenekYonetimService.java`

**Interfaces:**

- Produces: Her iki yazma metodunda `DataIntegrityViolationException` → `KaynakCakismasiException`.

- [ ] **Step 1: Failing unit test sınıfını oluştur**

```java
package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.YetenekRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.YetenekYonetimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YetenekYonetimServiceTest {

    @Mock
    private YetenekRepository yetenekRepository;

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private AuditService auditService;

    private YetenekYonetimService service;
    private Personel hr;

    @BeforeEach
    void setUp() {
        service = new YetenekYonetimService(
                yetenekRepository,
                personelRepository,
                auditService
        );

        Rol hrRolu = new Rol();
        hrRolu.setRolAdi(RolAdi.HR);

        hr = new Personel();
        hr.setPersonelId("hr-id");
        hr.setKullaniciAdi("hr.aktor");
        hr.setRol(hrRolu);
        hr.setCalisiyorMu(true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));
    }

    @Test
    void olusturmadaUniqueConstraintYarisi409aCevrilir() {
        when(
                yetenekRepository
                        .existsByYetenekAdiIgnoreCase("Java")
        ).thenReturn(false);

        when(yetenekRepository.saveAndFlush(any(Yetenek.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        assertThrows(
                KaynakCakismasiException.class,
                () -> service.yetenekOlustur(
                        "hr-id",
                        new YetenekAdiRequest("Java")
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void guncellemedeUniqueConstraintYarisi409aCevrilir() {
        Yetenek java = new Yetenek();
        java.setYetenekId(7);
        java.setYetenekAdi("Java");

        when(yetenekRepository.findById(7))
                .thenReturn(Optional.of(java));
        when(
                yetenekRepository
                        .existsByYetenekAdiIgnoreCaseAndYetenekIdNot(
                                "Java Backend",
                                7
                        )
        ).thenReturn(false);
        when(yetenekRepository.saveAndFlush(java))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        assertThrows(
                KaynakCakismasiException.class,
                () -> service.yetenekGuncelle(
                        "hr-id",
                        7,
                        new YetenekAdiRequest("Java Backend")
                )
        );

        verifyNoInteractions(auditService);
    }
}
```

- [ ] **Step 2: Unit RED sonucunu doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimServiceTest test
```

Beklenen: 2 test raw `DataIntegrityViolationException` aldığı için failure verir.

- [ ] **Step 3: Güvenli save-and-flush yardımcısını ekle**

Service importlarına ekle:

```java
import org.springframework.dao.DataIntegrityViolationException;
```

İki public metottaki doğrudan `saveAndFlush` çağrılarını şu yardımcı metoda
yönlendir:

```java
private Yetenek yetenegiGuvenliKaydet(Yetenek yetenek) {
    try {
        return yetenekRepository.saveAndFlush(yetenek);
    } catch (DataIntegrityViolationException exception) {
        throw new KaynakCakismasiException(
                "Yetenek zaten mevcut."
        );
    }
}
```

Oluşturmada dönüş değerini kullan:

```java
yetenek = yetenegiGuvenliKaydet(yetenek);
```

Güncellemede:

```java
yetenegiGuvenliKaydet(yetenek);
```

- [ ] **Step 4: Unit ve integration testlerini doğrula**

```powershell
.\mvnw.cmd -Dtest=YetenekYonetimServiceTest,YetenekYonetimControllerIntegrationTest test
```

Beklenen: 30 test invocation, 0 failure, 0 error.

---

### Task 7: Tam regresyon, mevcut lookup ve dokümantasyon

**Files:**

- Verify: bütün `backend` testleri
- Modify after explicit approval: `API_SPEC.md`
- Modify after explicit approval: `ROADMAP.md`
- Modify after explicit approval: `PROGRESS.md`
- Modify after explicit approval: `SECURITY.md`

**Interfaces:**

- Consumes: Task 1–6 sonunda geçen yetenek yönetimi.
- Produces: Tam backend regresyon kanıtı ve kodla uyumlu belgeler.

- [ ] **Step 1: Mevcut lookup regresyonunu ayrıca doğrula**

```powershell
.\mvnw.cmd -Dtest=LookupControllerIntegrationTest test
```

Beklenen: Mevcut 17 test invocation geçer; `GET /api/skills` bütün aktif
authenticated roller için alfabetik ve audit-free kalır.

- [ ] **Step 2: Tam backend suite'i çalıştır**

```powershell
.\mvnw.cmd test
```

Mevcut 166 teste 28 integration ve 2 unit invocation ekleneceği için beklenen
toplam 196 testtir. Gerçek çıktı farklıysa belgeye yalnız doğrulanan gerçek
sayı yazılır. Kabul ölçütü `BUILD SUCCESS`, 0 failure ve 0 error'dır.

- [ ] **Step 3: Modül tamamlanma kontrolünü yap**

`CONTRIBUTING.md` backend kontrol listesine göre:

- Request yalnız `yetenekAdi` kabul ediyor.
- Entity doğrudan response yapılmıyor.
- Controller'da iş kuralı veya repository erişimi yok.
- HR/Admin method-security ve principal aktiflik kontrolü backend'de.
- Duplicate ön kontrolüyle database unique fallback birlikte var.
- `400/401/403/404/409` sözleşmeleri integration testlerle doğrulanıyor.
- Rename ID ve personel ilişkisini koruyor.
- Audit target null ve ana transaction'a katılıyor.
- No-op persistence/audit yan etkisiz.
- MySQL rollback ve unique davranışı kapsanıyor.

- [ ] **Step 4: Dört Markdown dosyası için açık yazma onayı iste**

Onay gelmeden `API_SPEC.md`, `ROADMAP.md`, `PROGRESS.md` veya `SECURITY.md`
değiştirilmez.

- [ ] **Step 5: Onaydan sonra API_SPEC ve ROADMAP'i güncelle**

`API_SPEC.md` lookup bölümünde planlanan `POST /api/skills` satırını ve eski
“duplicate gelirse mevcut kayıt döner” maddesini kaldır. Uygulanan yönetim
sözleşmesini ekle:

```markdown
### Yetenek yönetimi

| Method | Endpoint | Role |
| --- | --- | --- |
| POST | `/api/skills` | HR, admin |
| PATCH | `/api/skills/{yetenekId}` | HR, admin |

- Yetenek adı trim edilir, kullanıcının harf biçimi korunur ve normalize edilmiş uzunluk 1–50 karakterdir.
- Case-insensitive duplicate oluşturma veya başka kayda duplicate olacak güncelleme `409 Yetenek zaten mevcut.` döner.
- Başarılı oluşturma `201 Created` ile `{ id, ad }`; gerçek güncelleme ve no-op `204 No Content` döner.
- Rename aynı ID ve personel-yetenek ilişkilerini korur. Yalnız harf biçimi değişikliği gerçek güncellemedir; tam normalize eşitlik no-op olur.
- Endpoint'ler yalnız aktif HR/Admin tarafından kullanılabilir. Principal bulunamazsa `404`, pasifse `403`, authentication yoksa `401` döner.
- Gerçek değişiklikler güvenli audit ile aynı transaction içinde tamamlanır veya rollback olur.
```

`ROADMAP.md`:

```markdown
- [x] Yetenek ekleme/güncelleme yönetim akışını oluştur
    - `POST /api/skills` ve `PATCH /api/skills/{yetenekId}` yalnız aktif HR/Admin rollerine açıktır.
    - Case-insensitive duplicate `409`; rename mevcut personel-yetenek ilişkilerini korur.
    - Oluşturma/güncelleme audit ile aynı transaction içinde uygulanır; silme/pasifleştirme ayrı tasarıma bırakılmıştır.
```

- [ ] **Step 6: Onaydan sonra PROGRESS ve SECURITY'yi güncelle**

Beklenen sayılar gerçek test çıktısıyla eşleşirse `PROGRESS.md` sonuna ekle:

```markdown
- Implemented HR/Admin-only `POST /api/skills` and `PATCH /api/skills/{yetenekId}` management endpoints without changing the existing lookup GET flow.
- Normalized names with trim while preserving intentional casing, rejected case-insensitive duplicates with controlled `409`, and retained the database unique constraint as a concurrency fallback.
- Renamed skills in place so existing personnel-skill relationships keep the same ID; exact normalized no-op updates remain persistence- and audit-free.
- Added `YETENEK_EKLENDI` and `YETENEK_GUNCELLENDI` audit events with the acting principal, null target personnel and catalog-safe name details in the shared transaction.
- Focused verification reported by the developer: `YetenekYonetimControllerIntegrationTest` ran 28 tests and `YetenekYonetimServiceTest` ran 2 tests, with 0 failures, 0 errors and 0 skipped.
- Full backend verification reported by the developer: 196 tests, 0 failures, 0 errors and 0 skipped; build successful.
```

`SECURITY.md` authorization bölümüne ekle:

```markdown
`POST /api/skills` ve `PATCH /api/skills/{yetenekId}` yalnız aktif `HR` ve `ADMIN` rollerine açıktır. Aktör yalnız JWT principal'dan belirlenir; request aktör veya personel kimliği taşımaz. Service principal personelinin mevcut ve aktif olduğunu doğrular. Case-insensitive duplicate ön kontrolüne ek olarak database unique constraint yarışı kontrollü `409` cevabına çevrilir; SQL veya constraint ayrıntısı sızdırılmaz. Yetenek değişikliği ve katalog audit kaydı aynı transaction içinde tamamlanır veya birlikte rollback olur. Audit hedef personeli null'dır; no-op güncelleme audit oluşturmaz.
```

- [ ] **Step 7: Markdown bütünlüğünü doğrula**

Önce repository'ye göreli `apply_patch` kullan. Windows'ta başarısız olursa
aynı yöntemi tekrarlamadan, alınmış onay kapsamında yalnız dört hedef Markdown
dosyasını UTF-8 Python ile; bütün eşleşmeleri bellekte hazırlayıp doğruladıktan
sonra birlikte yaz.

Kontroller:

- Dört dosya UTF-8 okunur.
- `\uFFFD`, `Ã`, `Ä` veya Türkçe metin içinde şüpheli `?` oluşmaz.
- Mevcut CRLF satır sonları korunur.
- `POST /api/skills` ve `PATCH /api/skills/{yetenekId}` uygulanan tabloda birer kez bulunur.
- Eski “tüm roller” ve “mevcut kayıt döner” yetenek yönetimi metni kaldırılır.
- Roadmap maddesi bir kez `[x]` olur ve eski `[ ]` maddesi kalmaz.
- Progress test sayıları yalnız gerçek çıktıyı kullanır ve bölüm bir kez eklenir.
- Security yönetim paragrafı bir kez eklenir.

---

## Son kabul ölçütleri

- HR ve Admin oluşturma/güncelleme yapabilir; Personel/Yönetici yapamaz.
- Principal mevcut ve aktif olmak zorundadır.
- Oluşturma trim edilmiş `{ id, ad }` ile `201` döner.
- Güncelleme aynı ID ve bütün personel ilişkilerini koruyarak `204` döner.
- Case-insensitive duplicate `409`; DB unique yarışı da aynı kontrollü hataya çevrilir.
- Null/boş/fazla uzun ad ve geçersiz ID `400`; bulunamayan yetenek `404` olur.
- Harf biçimi düzeltmesi gerçek değişiklik; tam normalize eşitlik no-op olur.
- Oluşturma/güncelleme audit kayıtları aktör, null hedef ve doğru detayla saklanır.
- Yetenek ve audit dış transaction hatasında birlikte rollback olur.
- `GET /api/skills` lookup sözleşmesi ve tam backend suite geçer.
- Silme/pasifleştirme bu planın dışında kalır.
