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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.service.YetenekYonetimService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;


import java.util.Map;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private YetenekRollbackTestService rollbackTestService;

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
    void hrKullanilmayanYetenegiSiler() throws Exception {
        Integer yetenekId = yetenekOlustur("Kotlin");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(0, yetenekSayisi());
        assertEquals(1L, personelRepository.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aktifVeyaPasifPersonelleIliskiliYetenek409IleKorunur(boolean personelAktif)
            throws Exception {
        Integer yetenekId = yetenekOlustur("Java " + personelAktif);
        Rol personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        Personel bagliPersonel = personelKaydet(
                "yetenek.bagli.personel." + personelAktif,
                "Bağlı",
                "Personel",
                personelRolu,
                personelAktif
        );
        personelYetenekOlustur(bagliPersonel.getPersonelId(), yetenekId);

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Yetenek kullanımda olduğu için silinemez."
                ));

        assertEquals(1, yetenekSayisi());
        assertEquals(
                1,
                personelYetenekIliskiSayisi(
                        bagliPersonel.getPersonelId(),
                        yetenekId
                )
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void basariliYetenekSilmeGuvenliAuditOlusturur() throws Exception {
        Integer yetenekId = yetenekOlustur("Kotlin");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isNoContent());

        Map<String, Object> audit = tekAuditKaydi();
        assertEquals("YETENEK_SILINDI", audit.get("eylem_tipi"));
        assertEquals(aktor.getPersonelId(), audit.get("personel_id"));
        assertEquals("hr.aktor", audit.get("kullanici_adi"));
        assertEquals("Yetenek silindi: Kotlin", audit.get("detay"));
        assertNull(audit.get("hedef_personel_id"));
    }

    @Test
    void adminKullanilmayanYetenegiSiler() throws Exception {
        aktorunRolunuDegistir(RolAdi.ADMIN);
        Integer yetenekId = yetenekOlustur("Go");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.ADMIN)
                        ))
                .andExpect(status().isNoContent());

        assertEquals(0, yetenekSayisi());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI"})
    void personelVeYoneticiYetenekSilemez(RolAdi aktorRolu) throws Exception {
        aktorunRolunuDegistir(aktorRolu);
        Integer yetenekId = yetenekOlustur("Korunan Yetenek");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, aktorRolu)
                        ))
                .andExpect(status().isForbidden());

        assertEquals(1, yetenekSayisi());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"HR", "ADMIN"})
    void pasifHrVeAdminYetenekSilemez(RolAdi aktorRolu) throws Exception {
        aktorunRolunuDegistir(aktorRolu);
        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);
        Integer yetenekId = yetenekOlustur("Pasif Aktör Yeteneği");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, aktorRolu)
                        ))
                .andExpect(status().isForbidden());

        assertEquals(1, yetenekSayisi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaYetenekSilme404Doner() throws Exception {
        Integer yetenekId = yetenekOlustur("Kayıp Aktör Yeteneği");
        String token = jwtService.generateToken("olmayan-aktor-id", RolAdi.HR);

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        assertEquals(1, yetenekSayisi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bulunmayanYetenekSilme404Doner() throws Exception {
        mockMvc.perform(delete("/api/skills/{yetenekId}", 999999)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanYetenekIdsiSilmede400Doner(int yetenekId) throws Exception {
        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isBadRequest());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanYetenekSilme401Doner() throws Exception {
        Integer yetenekId = yetenekOlustur("Tokensız Yetenek");

        mockMvc.perform(delete("/api/skills/{yetenekId}", yetenekId))
                .andExpect(status().isUnauthorized());

        assertEquals(1, yetenekSayisi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disTransactionHataVerirseYetenekVeSilmeAuditiRollbackOlur() {
        Integer yetenekId = yetenekOlustur("Rollback Yeteneği");

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.silVeHataFirlat(
                        aktor.getPersonelId(),
                        yetenekId
                )
        );

        assertEquals("Rollback Yeteneği", yetenekAdi(yetenekId));
        assertEquals(0L, auditLogRepository.count());
    }

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
    void adminYetenekOlusturabilir()
            throws Exception {
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

    @Test
    void adminYetenekGuncelleyebilir()
            throws Exception {
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
    void tokenOlmadanYetenekYonetilemez()
            throws Exception {
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
    void tokendakiHrBulunamazsa404Doner()
            throws Exception {
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
    void pasifHrYetenekYonetemez()
            throws Exception {
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

        assertEquals(
                "YETENEK_EKLENDI",
                audit.get("eylem_tipi")
        );
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
    void ayniNormalizeAdNoOpOlur()
            throws Exception {
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
    void metinYetenekIdsi400Doner()
            throws Exception {
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
    void bulunamayanYetenek404Doner()
            throws Exception {
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
                .orElseGet(
                        () -> rolKaydet(rolId, rolAdi)
                );

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

    private String token(Personel personel, RolAdi rolAdi) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }

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

        @Transactional
        public void silVeHataFirlat(
                String aktorId,
                Integer yetenekId
        ) {
            service.yetenekSil(aktorId, yetenekId);

            throw new IllegalStateException(
                    "Rollback testi için dış hata."
            );
        }
    }

}
