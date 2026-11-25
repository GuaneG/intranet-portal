# Personel Aktifleştirme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Aktif HR/Admin'in izinli pasif personel hedeflerini güvenli, idempotent, audit edilebilir ve transaction tutarlı biçimde yeniden aktifleştirmesini sağlamak.

**Architecture:** Mevcut `HrPersonelController`, `PersonelService` ve `PersonelYonetimYetkiService` korunur. Yeni body’siz `PATCH /api/hr/personel/{personelId}/aktiflestir` endpoint'i mevcut hedef rol matrisini yeniden kullanır; gerçek değişiklik ve `PERSONEL_AKTIFLESTIRILDI` audit kaydı aynı transaction'a katılır.

**Tech Stack:** Java 21, Spring Boot, Spring MVC, Spring Security method security, Spring Data JPA, JUnit 5, MockMvc, Testcontainers, MySQL 8, JaCoCo.

## Global Constraints

- Java ve test dosyalarını kullanıcı kopyalayıp uygular; agent bu dosyaları doğrudan değiştirmez.
- Frontend dosyalarına dokunulmaz.
- Git komutu çalıştırılmaz; plan adımlarındaki doğrulamalar commit içermez.
- Aktifleştirme request body almaz ve istemciden aktiflik, çıkış tarihi, aktör veya audit bilgisi kabul etmez.
- Mevcut pasifleştirme endpoint'i ve `PersonelYonetimYetkiService` hedef matrisi değiştirilmez.
- HR yalnız `PERSONEL` ve başka HR; Admin `PERSONEL`, HR ve `YONETICI` hedeflerini yönetebilir. Öz-hedef ve bütün `ADMIN` hedefler yasaktır.
- Hedef zaten aktifse yetki kontrollerinden sonra `204` no-op döner; persistence veya audit yan etkisi oluşmaz.
- Gerçek aktifleştirmede `calisiyorMu=true`, `isCikisTarihi=null` olur; parola, rol, departman, yönetici ve token kayıtlarına dokunulmaz.
- Pasifleştirme sırasında kaldırılan eski çalışan-yönetici ilişkileri otomatik geri yüklenmez.
- Access-token versioning, istihdam geçmişi ve frontend bu planın kapsamı dışındadır.

---

## Dosya haritası

Production:

- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java` — HTTP endpoint ve `204` response.
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java` — authorization-aware transactional aktifleştirme akışı.
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java` — `PERSONEL_AKTIFLESTIRILDI` olayı.

Test:

- Create: `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java` — endpoint, güvenlik, audit, rollback ve regresyon senaryoları.
- Verify: `backend/src/test/java/com/jforce/backend/HrPersonelControllerIntegrationTest.java` — mevcut pasifleştirme ve personel yönetimi regresyonu.
- Verify: `backend/src/test/java/com/jforce/backend/PersonelServiceTest.java` — mevcut service regresyonu.
- Verify: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java` — aktif ekip dizini regresyonu.

Docs:

- Modify after full verification: `API_SPEC.md`
- Modify after full verification: `ROADMAP.md`
- Modify after full verification: `SECURITY.md`
- Modify after full verification: `PROGRESS.md`

---

### Task 1: Temel aktifleştirme endpoint'i

**Files:**

- Create: `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/controller/HrPersonelController.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`

**Interfaces:**

- Consumes: `PersonelYonetimYetkiService.aktifAktoruGetir(String)` ve `hedefiDogrula(Personel, Personel)`.
- Produces: `void PersonelService.personelAktiflestir(String aktorId, String hedefPersonelId)` ve `PATCH /api/hr/personel/{personelId}/aktiflestir`.

- [ ] **Step 1: Temel failing integration test sınıfını oluştur**

Yeni test sınıfı aşağıdaki bağımlılıkları, temiz kurulumu ve ilk davranış testini içersin:

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
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PersonelAktiflestirmeControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private IzinBilgileriRepository izinBilgileriRepository;
    @Autowired private PersonelYetenekRepository personelYetenekRepository;
    @Autowired private PersonelRepository personelRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;

    private final Map<RolAdi, Rol> roller =
            new EnumMap<>(RolAdi.class);

    private Departman departman;
    private Personel hrAktor;
    private Personel pasifPersonel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        roller.clear();

        roller.put(RolAdi.ADMIN, rolKaydet(1, RolAdi.ADMIN));
        roller.put(RolAdi.PERSONEL, rolKaydet(2, RolAdi.PERSONEL));
        roller.put(RolAdi.YONETICI, rolKaydet(3, RolAdi.YONETICI));
        roller.put(RolAdi.HR, rolKaydet(4, RolAdi.HR));

        departman = departmanKaydet("Bilgi Teknolojileri");

        hrAktor = personelKaydet(
                "aktiflestirme.hr.aktor",
                "Aktifleştirme",
                "HR",
                RolAdi.HR,
                true
        );

        pasifPersonel = personelKaydet(
                "aktiflestirilecek.personel",
                "Aktifleşecek",
                "Personel",
                RolAdi.PERSONEL,
                false
        );
        pasifPersonel.setIsCikisTarihi(
                LocalDate.now().minusDays(1)
        );
        pasifPersonel = personelRepository.saveAndFlush(
                pasifPersonel
        );
    }

    @Test
    void hrPasifPersoneliAktiflestirir()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Personel aktiflesen = personelRepository
                .findById(pasifPersonel.getPersonelId())
                .orElseThrow();

        assertTrue(aktiflesen.getCalisiyorMu());
        assertNull(aktiflesen.getIsCikisTarihi());
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(yeniDepartman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            RolAdi rolAdi,
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
        personel.setRol(roller.get(rolAdi));
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(personel);
    }

    private String token(Personel personel, RolAdi rolAdi) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }
}
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: Tek test endpoint bulunmadığı için `404` alır; test `204` beklediğinden başarısız olur.

- [ ] **Step 3: Controller endpoint'ini ekle**

`HrPersonelController` içine mevcut pasifleştirme metodunun yanına ekle:

```java
@PatchMapping("/{personelId}/aktiflestir")
public ResponseEntity<Void> personelAktiflestir(
        @PathVariable String personelId,
        Authentication authentication
) {
    personelService.personelAktiflestir(
            authentication.getName(),
            personelId
    );
    return ResponseEntity.noContent().build();
}
```

- [ ] **Step 4: Minimum service akışını ekle**

`PersonelService` içine ekle; audit Task 3'ten önce eklenmez:

```java
@Transactional
@PreAuthorize("hasAnyRole('HR', 'ADMIN')")
public void personelAktiflestir(
        String aktorId,
        String hedefPersonelId
) {
    Personel aktor =
            personelYonetimYetkiService
                    .aktifAktoruGetir(aktorId);

    Personel hedef = personelRepository
            .findById(hedefPersonelId)
            .orElseThrow(
                    () -> new KaynakBulunamadiException(
                            "Personel bulunamadı."
                    )
            );

    personelYonetimYetkiService.hedefiDogrula(
            aktor,
            hedef
    );

    if (Boolean.TRUE.equals(hedef.getCalisiyorMu())) {
        return;
    }

    hedef.setCalisiyorMu(true);
    hedef.setIsCikisTarihi(null);
}
```

- [ ] **Step 5: GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: `1` test, `0` failure, `0` error, `BUILD SUCCESS`.

---

### Task 2: Yetki matrisi, principal hataları ve no-op

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java`

**Interfaces:**

- Consumes: Task 1 endpoint'i ve mevcut `PersonelYonetimYetkiService` hedef matrisi.
- Produces: HTTP seviyesinde doğrulanmış HR/Admin hedef matrisi ve no-op sözleşmesi.

- [ ] **Step 1: Parametreli hedef matrisi testini ekle**

Importlar:

```java
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
```

Test:

```java
@ParameterizedTest
@CsvSource({
        "HR,PERSONEL,204",
        "HR,HR,204",
        "HR,YONETICI,403",
        "HR,ADMIN,403",
        "ADMIN,PERSONEL,204",
        "ADMIN,HR,204",
        "ADMIN,YONETICI,204",
        "ADMIN,ADMIN,403"
})
void hedefRolMatrisiUygulanir(
        RolAdi aktorRolu,
        RolAdi hedefRolu,
        int beklenenStatus
) throws Exception {
    Personel aktor = personelKaydet(
            "matris.aktor."
                    + aktorRolu.name().toLowerCase(),
            "Matris",
            "Aktör",
            aktorRolu,
            true
    );
    Personel hedef = personelKaydet(
            "matris.hedef."
                    + hedefRolu.name().toLowerCase(),
            "Matris",
            "Hedef",
            hedefRolu,
            false
    );
    hedef.setIsCikisTarihi(LocalDate.now().minusDays(1));
    hedef = personelRepository.saveAndFlush(hedef);

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            hedef.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            aktor,
                                            aktorRolu
                                    )
                            )
            )
            .andExpect(status().is(beklenenStatus));

    Personel sonHedef = personelRepository
            .findById(hedef.getPersonelId())
            .orElseThrow();

    assertEquals(
            beklenenStatus == 204,
            sonHedef.getCalisiyorMu()
    );
}
```

- [ ] **Step 2: Öz-hedef, pasif aktör, eksik principal/hedef ve authentication testlerini ekle**

Gerekli import:

```java
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
```

Testler:

```java
@Test
void hrKendisiniAktiflestiremez() throws Exception {
    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            hrAktor.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            hrAktor,
                                            RolAdi.HR
                                    )
                            )
            )
            .andExpect(status().isForbidden());
}

@Test
void pasifHrPersonelAktiflestiremez() throws Exception {
    hrAktor.setCalisiyorMu(false);
    hrAktor = personelRepository.saveAndFlush(hrAktor);

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            pasifPersonel.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            hrAktor,
                                            RolAdi.HR
                                    )
                            )
            )
            .andExpect(status().isForbidden());

    assertFalse(
            personelRepository
                    .findById(pasifPersonel.getPersonelId())
                    .orElseThrow()
                    .getCalisiyorMu()
    );
}

@Test
void tokendakiPersonelBulunamazsa404Doner()
        throws Exception {
    String token = jwtService.generateToken(
            "veritabaninda-olmayan-personel",
            RolAdi.HR
    );

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            pasifPersonel.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token
                            )
            )
            .andExpect(status().isNotFound())
            .andExpect(
                    jsonPath("$.message").value(
                            "Personel bulunamadı."
                    )
            );
}

@Test
void hedefPersonelBulunamazsa404Doner()
        throws Exception {
    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            "veritabaninda-olmayan-hedef"
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            hrAktor,
                                            RolAdi.HR
                                    )
                            )
            )
            .andExpect(status().isNotFound())
            .andExpect(
                    jsonPath("$.message").value(
                            "Personel bulunamadı."
                    )
            );
}

@Test
void tokenOlmadanPersonelAktiflestirilemez()
        throws Exception {
    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            pasifPersonel.getPersonelId()
                    )
            )
            .andExpect(status().isUnauthorized());
}
```

- [ ] **Step 3: Yetki ve hata paketini çalıştır**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: Bütün test invocations geçer. Bir matris testi farklı status döndürürse production koduna yeni rol koşulu eklemek yerine önce mevcut `PersonelYonetimYetkiService` ile uyumsuzluğun kök nedenini incele.

---

### Task 3: Audit, aktif no-op ve transaction rollback

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java`
- Modify: `backend/src/main/java/com/jforce/backend/model/enums/AuditEylem.java`
- Modify: `backend/src/main/java/com/jforce/backend/service/PersonelService.java`

**Interfaces:**

- Consumes: Task 1 `personelAktiflestir(...)` metodu.
- Produces: `AuditEylem.PERSONEL_AKTIFLESTIRILDI` ve ortak-transaction audit davranışı.

- [ ] **Step 1: Failing audit ve no-op testlerini ekle**

Importlar ve alan:

```java
import org.springframework.jdbc.core.JdbcTemplate;

@Autowired private JdbcTemplate jdbcTemplate;
```

Testler:

```java
@Test
void gercekAktiflestirmeGuvenliAuditOlusturur()
        throws Exception {
    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            pasifPersonel.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            hrAktor,
                                            RolAdi.HR
                                    )
                            )
            )
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

    assertEquals(
            "PERSONEL_AKTIFLESTIRILDI",
            audit.get("eylem_tipi")
    );
    assertEquals(
            hrAktor.getPersonelId(),
            audit.get("personel_id")
    );
    assertEquals(
            pasifPersonel.getPersonelId(),
            audit.get("hedef_personel_id")
    );
    assertEquals(
            "aktiflestirme.hr.aktor",
            audit.get("kullanici_adi")
    );
    assertEquals(
            "Personel aktifleştirildi.",
            audit.get("detay")
    );
}

@Test
void zatenAktifHedefNoOpOlur() throws Exception {
    Personel zatenAktif = personelKaydet(
            "zaten.aktif.personel",
            "Zaten",
            "Aktif",
            RolAdi.PERSONEL,
            true
    );
    LocalDate tutarsizEskiCikisTarihi =
            LocalDate.now().minusDays(10);
    zatenAktif.setIsCikisTarihi(tutarsizEskiCikisTarihi);
    zatenAktif = personelRepository.saveAndFlush(zatenAktif);

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            zatenAktif.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(
                                            hrAktor,
                                            RolAdi.HR
                                    )
                            )
            )
            .andExpect(status().isNoContent());

    Personel degismeyen = personelRepository
            .findById(zatenAktif.getPersonelId())
            .orElseThrow();

    assertTrue(degismeyen.getCalisiyorMu());
    assertEquals(
            tutarsizEskiCikisTarihi,
            degismeyen.getIsCikisTarihi()
    );
    assertEquals(0L, auditLogRepository.count());
}
```

- [ ] **Step 2: RED doğrula**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: `gercekAktiflestirmeGuvenliAuditOlusturur` audit sayısını `1` beklerken `0` bulur. No-op testi geçmelidir.

- [ ] **Step 3: Audit enum değerini ekle**

`AuditEylem` içinde personel lifecycle olaylarının yanına ekle:

```java
PERSONEL_PASIFE_ALINDI,
PERSONEL_AKTIFLESTIRILDI,
```

- [ ] **Step 4: Service'e ortak-transaction audit çağrısını ekle**

`hedef.setIsCikisTarihi(null);` satırından sonra ekle:

```java
auditService.logKaydetOrtakTransaction(
        AuditEylem.PERSONEL_AKTIFLESTIRILDI,
        aktor,
        aktor.getKullaniciAdi(),
        "Personel aktifleştirildi.",
        hedef
);
```

Bu çağrı `if (hedef aktif) return;` kontrolünden sonra kalmalıdır.

- [ ] **Step 5: Rollback test altyapısını ve testini ekle**

Importlar ve alan:

```java
import com.jforce.backend.service.PersonelService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Autowired
private PersonelAktiflestirmeRollbackTestService
        rollbackTestService;
```

Test:

```java
@Test
@WithMockUser(roles = "HR")
void disHataAktiflestirmeVeAuditiRollbackYapar() {
    LocalDate eskiCikisTarihi =
            pasifPersonel.getIsCikisTarihi();

    assertThrows(
            IllegalStateException.class,
            () -> rollbackTestService
                    .aktiflestirVeHataFirlat(
                            hrAktor.getPersonelId(),
                            pasifPersonel.getPersonelId()
                    )
    );

    Map<String, Object> durum = jdbcTemplate.queryForMap(
            """
            SELECT calisiyor_mu, is_cikis_tarihi
            FROM personel
            WHERE personel_id = ?
            """,
            pasifPersonel.getPersonelId()
    );

    assertEquals(
            false,
            durum.get("calisiyor_mu")
    );
    assertEquals(
            eskiCikisTarihi,
            ((java.sql.Date) durum.get("is_cikis_tarihi"))
                    .toLocalDate()
    );
    assertEquals(0L, auditLogRepository.count());
}
```

Sınıfın son kapanışından önce test bean'i:

```java
@TestConfiguration(proxyBeanMethods = false)
static class RollbackTestConfiguration {

    @Bean
    PersonelAktiflestirmeRollbackTestService
    personelAktiflestirmeRollbackTestService(
            PersonelService personelService
    ) {
        return new PersonelAktiflestirmeRollbackTestService(
                personelService
        );
    }
}

static class PersonelAktiflestirmeRollbackTestService {

    private final PersonelService personelService;

    PersonelAktiflestirmeRollbackTestService(
            PersonelService personelService
    ) {
        this.personelService = personelService;
    }

    @Transactional
    public void aktiflestirVeHataFirlat(
            String aktorId,
            String hedefPersonelId
    ) {
        personelService.personelAktiflestir(
                aktorId,
                hedefPersonelId
        );
        throw new IllegalStateException(
                "Rollback davranışını doğrulamak için test hatası."
        );
    }
}
```

- [ ] **Step 6: Audit ve rollback GREEN doğrula**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: bütün testler `0` failure ve `0` error ile geçer.

---

### Task 4: Yönetici ilişkisi ve ekip dizini regresyonu

**Files:**

- Modify: `backend/src/test/java/com/jforce/backend/PersonelAktiflestirmeControllerIntegrationTest.java`
- Verify: `backend/src/test/java/com/jforce/backend/EkipDiziniControllerIntegrationTest.java`

**Interfaces:**

- Consumes: Aktifleştirme ve mevcut pasifleştirme endpoint'leri ile `GET /api/ekip-dizini`.
- Produces: Eski çalışanların otomatik geri bağlanmadığını ve aktifleştirilen personelin dizine geri döndüğünü kanıtlayan regresyon.

- [ ] **Step 1: Yönetici ilişkisinin geri yüklenmediği testi ekle**

```java
@Test
void yoneticiAktiflestirilinceEskiCalisanlariGeriBaglanmaz()
        throws Exception {
    Personel admin = personelKaydet(
            "aktiflestirme.admin",
            "Aktifleştirme",
            "Admin",
            RolAdi.ADMIN,
            true
    );
    Personel yonetici = personelKaydet(
            "aktiflestirme.yonetici",
            "Aktifleştirme",
            "Yönetici",
            RolAdi.YONETICI,
            true
    );
    Personel calisan = personelKaydet(
            "aktiflestirme.calisan",
            "Aktifleştirme",
            "Çalışan",
            RolAdi.PERSONEL,
            true
    );
    calisan.setYonetici(yonetici);
    calisan = personelRepository.saveAndFlush(calisan);

    String adminToken = token(admin, RolAdi.ADMIN);

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/pasiflestir",
                            yonetici.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + adminToken
                            )
            )
            .andExpect(status().isNoContent());

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            yonetici.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + adminToken
                            )
            )
            .andExpect(status().isNoContent());

    Personel sonCalisan = personelRepository
            .findById(calisan.getPersonelId())
            .orElseThrow();

    assertNull(sonCalisan.getYonetici());
    assertTrue(
            personelRepository
                    .findById(yonetici.getPersonelId())
                    .orElseThrow()
                    .getCalisiyorMu()
    );
}
```

- [ ] **Step 2: Aktifleştirilen personelin ekip dizinine döndüğü testi ekle**

Gerekli statik import:

```java
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
```

Test:

```java
@Test
void aktiflestirilenPersonelEkipDizinineGeriDoner()
        throws Exception {
    String hrToken = token(hrAktor, RolAdi.HR);

    mockMvc.perform(
                    patch(
                            "/api/hr/personel/{personelId}/aktiflestir",
                            pasifPersonel.getPersonelId()
                    )
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + hrToken
                            )
            )
            .andExpect(status().isNoContent());

    mockMvc.perform(
                    get("/api/ekip-dizini")
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + hrToken
                            )
            )
            .andExpect(status().isOk())
            .andExpect(
                    jsonPath("$.icerik[*].ad")
                            .value(hasItem("Aktifleşecek"))
            );
}
```

- [ ] **Step 3: Odaklı lifecycle ve dizin regresyonunu çalıştır**

```powershell
.\mvnw.cmd "-Dtest=PersonelAktiflestirmeControllerIntegrationTest,HrPersonelControllerIntegrationTest,PersonelServiceTest,EkipDiziniControllerIntegrationTest" "-Djacoco.skip=true" test
```

Beklenen: Bütün seçili testler `0` failure ve `0` error ile geçer; mevcut pasifleştirme ve ekip dizini sözleşmeleri değişmez.

---

### Task 5: Belgeler, tam backend paketi ve kabul

**Files:**

- Modify: `API_SPEC.md`
- Modify: `ROADMAP.md`
- Modify: `SECURITY.md`
- Modify: `PROGRESS.md`
- Verify: all backend tests

**Interfaces:**

- Consumes: Task 1–4 doğrulanmış davranışları.
- Produces: Kodla uyumlu API/güvenlik sözleşmesi ve gerçek test sayılarını içeren ilerleme kaydı.

- [ ] **Step 1: Tam backend test paketini JaCoCo açık çalıştır**

```powershell
.\mvnw.cmd test
```

Kabul: `0 failures`, `0 errors`, JaCoCo check başarılı ve `BUILD SUCCESS`. Belgelere yalnız gerçek çıktıdaki test toplamı yazılır; önceki taban `306` testtir.

- [ ] **Step 2: `API_SPEC.md` sözleşmesini güncelle**

Personel endpoint tablosuna ekle:

```markdown
| PATCH | `/api/hr/personel/{personelId}/aktiflestir` | HR, admin; hedef rol kuralları uygulanır |
```

Pasifleştirme açıklamalarının yanına şu kesin davranışları ekle:

```markdown
- `PATCH /api/hr/personel/{personelId}/aktiflestir` request body almaz. HR yalnız `PERSONEL` ve başka HR; Admin `PERSONEL`, HR ve `YONETICI` hedeflerini aktifleştirebilir. Öz-hedef ve bütün `ADMIN` hedefler `403` döner.
- Gerçek aktifleştirme `calisiyorMu=true`, `isCikisTarihi=null` yapar ve aynı transaction içinde `PERSONEL_AKTIFLESTIRILDI` audit kaydı oluşturur. Parola, rol, departman, yönetici ve token kayıtları değiştirilmez; kaldırılmış eski çalışan-yönetici ilişkileri geri yüklenmez.
- Zaten aktif izinli hedef `204 No Content` no-op döner ve persistence/audit yan etkisi oluşturmaz. Bulunamayan aktör/hedef `404`, pasif aktör veya yasak hedef `403`, authentication yoksa `401` döner.
```

- [ ] **Step 3: `ROADMAP.md`, `SECURITY.md` ve `PROGRESS.md` güncelle**

`ROADMAP.md` personel işlemlerine:

```markdown
- [x] HR/Admin personel aktifleştirme akışını oluştur
```

`SECURITY.md` mevcut mutation paragrafına aktifleştirmeyi ve audit listesine olayı ekle:

```markdown
- `PERSONEL_AKTIFLESTIRILDI`
```

Güvenlik açıklaması, aktör/target matrisinin no-op'tan önce uygulandığını, eski stateless access-token mimarisinin değişmediğini ve audit'in ortak transaction'a katıldığını açıkça belirtmelidir.

`PROGRESS.md` yeni tarihli bölümde şu doğrulanmış kapsamı kaydetsin:

```markdown
- Added active HR/Admin-only personnel reactivation with the existing target-role matrix, idempotent `204` behavior, exit-date clearing and shared-transaction audit.
- Verified that reactivated managers do not regain previously detached direct reports and reactivated personnel return to the active team directory.
- Focused verification satırında seçili Maven çalıştırmasının `Tests run` değerini rakamla ve `0 failures, 0 errors` sonucunu kaydet.
- Full backend verification satırında tam Maven çalıştırmasının `Tests run` değerini rakamla; `0 failures, 0 errors`, başarılı JaCoCo check ve build sonucunu kaydet.
```

Markdown düzenlemelerinde önce `apply_patch`; Windows sandbox başarısız olursa yalnız hedef Markdown dosyaları için onaylı PowerShell→UTF-8 Python yöntemi kullanılır. Çoklu dosya değişiklikleri bütün eşleşmeler doğrulandıktan sonra birlikte yazılır ve strict UTF-8/U+FFFD/mojibake/tekrar kontrolleri yapılır.

- [ ] **Step 4: Son kabul kontrolü**

```text
PATCH /api/hr/personel/{id}/aktiflestir -> body yok, 204
pasif izinli hedef -> aktif=true, çıkış tarihi=null, tek audit
aktif izinli hedef -> no-op, audit yok
HR/Admin hedef matrisi ve öz-hedef yasağı -> backend'de
pasif aktör 403, eksik principal/hedef 404, tokensız 401
yönetici ilişkileri otomatik geri yüklenmez
ekip dizini yalnız güncel aktiflik durumunu yansıtır
personel değişikliği + audit dış hatada birlikte rollback
frontend ve Git kapsam dışı
```

---

## Son kabul ölçütleri

- Yeni endpoint mevcut pasifleştirme sözleşmesini değiştirmez.
- Aktör ve hedef yetkileri frontend'e güvenmeden backend'de uygulanır.
- Gerçek aktifleştirme yalnız onaylanan iki alanı değiştirir ve güvenli audit üretir.
- No-op güvenlik kontrollerini atlamaz ve yan etki üretmez.
- Yönetici ilişkisi, ekip dizini ve transaction davranışları integration testleriyle kanıtlanır.
- Odaklı ve tam backend testleri gerçek çıktıda başarılıdır.
