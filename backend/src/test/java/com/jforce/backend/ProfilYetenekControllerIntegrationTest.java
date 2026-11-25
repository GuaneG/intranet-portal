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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.service.ProfilService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    private ProfilYetenekRollbackTestService rollbackTestService;

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
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(javaId)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(
                List.of(javaId),
                personelinYetenekIdleri()
        );
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHataVerirseYeteneklerVeAuditRollbackOlur() {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");

        personelYetenekOlustur(javaId);

        YetenekUpdateRequest request =
                new YetenekUpdateRequest(
                        List.of(dockerId)
                );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.guncelleVeHataFirlat(
                        personel.getPersonelId(),
                        request
                )
        );

        assertEquals(
                List.of(javaId),
                personelinYetenekIdleri()
        );

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void pasifPersonelYetenekleriniGuncelleyemez()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": []
                        }
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner()
            throws Exception {
        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
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

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void tokenOlmadanYeteneklerGuncellenemez()
            throws Exception {
        mockMvc.perform(put("/api/profil/yetenekler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": []
                        }
                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void gercekDegisiklikGuvenliAuditKaydiOlusturur()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");

        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(dockerId)))
                .andExpect(status().isNoContent());

        assertEquals(
                1L,
                auditLogRepository.count()
        );

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
                "PROFIL_GUNCELLENDI",
                audit.get("eylem_tipi")
        );

        assertEquals(
                personel.getPersonelId(),
                audit.get("personel_id")
        );

        assertEquals(
                personel.getPersonelId(),
                audit.get("hedef_personel_id")
        );

        assertEquals(
                "test.personel",
                audit.get("kullanici_adi")
        );

        assertEquals(
                "Güncellenen alanlar: yetenekler",
                audit.get("detay")
        );
    }

    @Test
    void mevcutListeyiIstenenYetenekKumesiyleDegistirir()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer springId = yetenekOlustur("Spring");
        Integer dockerId = yetenekOlustur("Docker");

        personelYetenekOlustur(javaId);
        personelYetenekOlustur(springId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
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
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "yetenekIdleri": []
                            }
                            """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(
                List.of(),
                personelinYetenekIdleri()
        );
    }

    @Test
    void ayniKumeFarkliSiradaGonderilirseNoOpOlur()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer springId = yetenekOlustur("Spring");

        personelYetenekOlustur(javaId);
        personelYetenekOlustur(springId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
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

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }
    @Test
    void birYetenekBulunamazsaButunIstekReddedilir()
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
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

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    @Test
    void tekrarlananYetenekIdsi400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": [%d, %d]
                        }
                        """.formatted(javaId, javaId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    @Test
    void nullYetenekIdsi400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": [null]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanYetenekIdsi400Doner(int gecersizId)
            throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "yetenekIdleri": [%d]
                        }
                        """.formatted(gecersizId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

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
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    @Test
    void requestBodyGonderilmezse400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(javaId);

        String token = tokenOlustur(RolAdi.PERSONEL);

        mockMvc.perform(put("/api/profil/yetenekler")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(
                List.of(javaId)
        );
    }

    private void yeteneklerVeAuditDegismedi(
            List<Integer> beklenenYetenekIdleri
    ) {
        assertEquals(
                beklenenYetenekIdleri,
                personelinYetenekIdleri()
        );

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    private String tokenOlustur(RolAdi rolAdi) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
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
                INSERT INTO personel_yetenek (
                    personel_id,
                    yetenek_id
                )
                VALUES (?, ?)
                """,
                personel.getPersonelId(),
                yetenekId
        );
    }

    private void personelinRolunuDegistir(RolAdi rolAdi) {
        if (personel.getRol().getRolAdi() == rolAdi) {
            return;
        }

        int rolId = switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };

        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        rol = rolRepository.save(rol);

        personel.setRol(rol);
        personel = personelRepository.saveAndFlush(personel);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        ProfilYetenekRollbackTestService rollbackTestService(
                ProfilService profilService
        ) {
            return new ProfilYetenekRollbackTestService(
                    profilService
            );
        }
    }

    static class ProfilYetenekRollbackTestService {

        private final ProfilService profilService;

        ProfilYetenekRollbackTestService(
                ProfilService profilService
        ) {
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
}