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
import com.jforce.backend.model.dto.request.DepartmanAdiRequest;
import com.jforce.backend.service.DepartmanYonetimService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DepartmanYonetimControllerIntegrationTest extends AbstractIntegrationTest {

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
    private DepartmanRollbackTestService rollbackTestService;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Departman departman;
    private Personel hrAktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        Rol hrRolu = rolKaydet(4, RolAdi.HR);

        departman =
                departmanKaydet("Bilgi Teknolojileri");

        hrAktor = personelKaydet(
                "departman.hr.aktor",
                "Departman",
                "HR",
                hrRolu,
                departman,
                true
        );
    }

    @Test
    void hrKullanilmayanDepartmaniSiler() throws Exception {
        Departman kullanilmayanDepartman =
                departmanKaydet("Kullanılmayan Departman");

        mockMvc.perform(delete(
                                "/api/departments/{departmanId}",
                                kullanilmayanDepartman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(hrAktor, RolAdi.HR)
                                ))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertFalse(
                departmanRepository.existsById(
                        kullanilmayanDepartman.getDepartmanId()
                )
        );
        assertEquals(1L, personelRepository.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aktifVeyaPasifPersoneleBagliDepartman409IleKorunur(boolean personelAktif) throws Exception {
        Departman kullanilanDepartman =
                departmanKaydet("Kullanılan Departman " + personelAktif);
        Rol personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        Personel bagliPersonel = personelKaydet(
                "bagli.personel." + personelAktif,
                "Bağlı",
                "Personel",
                personelRolu,
                kullanilanDepartman,
                personelAktif
        );

        mockMvc.perform(delete(
                                "/api/departments/{departmanId}",
                                kullanilanDepartman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(hrAktor, RolAdi.HR)
                                ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(
                        "Departman kullanımda olduğu için silinemez."
                ));

        assertTrue(
                departmanRepository.existsById(kullanilanDepartman.getDepartmanId())
        );
        assertEquals(
                kullanilanDepartman.getDepartmanId(),
                personelRepository.findById(bagliPersonel.getPersonelId())
                        .orElseThrow()
                        .getDepartman()
                        .getDepartmanId()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void basariliDepartmanSilmeGuvenliAuditOlusturur() throws Exception {
        Departman kullanilmayanDepartman =
                departmanKaydet("İnsan Kaynakları");

        mockMvc.perform(delete(
                                "/api/departments/{departmanId}",
                                kullanilmayanDepartman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(hrAktor, RolAdi.HR)
                                ))
                .andExpect(status().isNoContent());

        Map<String, Object> audit = tekAuditKaydi();
        assertEquals("DEPARTMAN_SILINDI", audit.get("eylem_tipi"));
        assertEquals(hrAktor.getPersonelId(), audit.get("personel_id"));
        assertEquals("departman.hr.aktor", audit.get("kullanici_adi"));
        assertEquals("Departman silindi: İnsan Kaynakları", audit.get("detay"));
        assertNull(audit.get("hedef_personel_id"));
    }

    @Test
    void adminKullanilmayanDepartmaniSiler() throws Exception {
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        Personel admin = personelKaydet(
                "departman.admin.aktor", "Departman", "Admin",
                adminRolu, departman, true
        );
        Departman kullanilmayan = departmanKaydet("Operasyon");

        mockMvc.perform(delete("/api/departments/{departmanId}", kullanilmayan.getDepartmanId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isNoContent());

        assertFalse(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI"})
    void personelVeYoneticiDepartmanSilemez(RolAdi aktorRolu) throws Exception {
        Rol rol = rolKaydet(aktorRolu.ordinal() + 1, aktorRolu);
        Personel aktor = personelKaydet(
                "departman." + aktorRolu.name().toLowerCase() + ".aktor",
                "Yetkisiz", aktorRolu.name(), rol, departman, true
        );
        Departman kullanilmayan = departmanKaydet("Korunan Departman");

        mockMvc.perform(delete("/api/departments/{departmanId}", kullanilmayan.getDepartmanId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, aktorRolu)))
                .andExpect(status().isForbidden());

        assertTrue(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"HR", "ADMIN"})
    void pasifHrVeAdminDepartmanSilemez(RolAdi aktorRolu) throws Exception {
        Personel aktor = aktorRolu == RolAdi.HR
                ? hrAktor
                : personelKaydet(
                        "pasif.departman.admin", "Pasif", "Admin",
                        rolKaydet(1, RolAdi.ADMIN), departman, true
                );
        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);
        Departman kullanilmayan = departmanKaydet("Pasif Aktör Departmanı");

        mockMvc.perform(delete("/api/departments/{departmanId}", kullanilmayan.getDepartmanId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, aktorRolu)))
                .andExpect(status().isForbidden());

        assertTrue(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaDepartmanSilme404Doner() throws Exception {
        Departman kullanilmayan = departmanKaydet("Kayıp Aktör Departmanı");
        String token = jwtService.generateToken("olmayan-aktor-id", RolAdi.HR);

        mockMvc.perform(delete("/api/departments/{departmanId}", kullanilmayan.getDepartmanId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        assertTrue(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bulunmayanDepartmanSilme404Doner() throws Exception {
        mockMvc.perform(delete("/api/departments/{departmanId}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(hrAktor, RolAdi.HR)))
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanDepartmanIdsiSilmede400Doner(int departmanId) throws Exception {
        mockMvc.perform(delete("/api/departments/{departmanId}", departmanId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(hrAktor, RolAdi.HR)))
                .andExpect(status().isBadRequest());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanDepartmanSilme401Doner() throws Exception {
        Departman kullanilmayan = departmanKaydet("Tokensız Departman");

        mockMvc.perform(delete("/api/departments/{departmanId}", kullanilmayan.getDepartmanId()))
                .andExpect(status().isUnauthorized());

        assertTrue(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disTransactionHataVerirseDepartmanVeSilmeAuditiRollbackOlur() {
        Departman kullanilmayan = departmanKaydet("Rollback Departmanı");

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.silVeHataFirlat(
                        hrAktor.getPersonelId(),
                        kullanilmayan.getDepartmanId()
                )
        );

        assertTrue(departmanRepository.existsById(kullanilmayan.getDepartmanId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"departmanAdi\": null}",
            "{\"departmanAdi\": \"\"}",
            "{\"departmanAdi\": \"   \"}"
    })
    void gecersizDepartmanAdi400Doner(String requestBody)
            throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(
                1L,
                departmanRepository.count()
        );
    }

    @Test
    void trimSonrasiElliKarakterdenUzunAd400Doner()
            throws Exception {
        String uzunAd = "a".repeat(51);

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "  %s  "
                            }
                            """.formatted(uzunAd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message").value(
                                "Departman adı en fazla 50 karakter olmalıdır."
                        )
                );

        assertEquals(
                1L,
                departmanRepository.count()
        );
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataDepartmanOlusturmaVeAuditiRollbackYapar() {
        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService
                        .olusturVeHataFirlat(
                                hrAktor.getPersonelId(),
                                new DepartmanAdiRequest(
                                        "Finans"
                                )
                        )
        );

        assertEquals(
                1L,
                departmanRepository.count()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataDepartmanGuncellemeVeAuditiRollbackYapar() {
        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService
                        .guncelleVeHataFirlat(
                                hrAktor.getPersonelId(),
                                departman.getDepartmanId(),
                                new DepartmanAdiRequest(
                                        "Kurumsal Teknoloji"
                                )
                        )
        );

        String veritabanindakiAd =
                jdbcTemplate.queryForObject(
                        """
                        SELECT departman_adi
                        FROM departman
                        WHERE departman_id = ?
                        """,
                        String.class,
                        departman.getDepartmanId()
                );

        assertEquals(
                "Bilgi Teknolojileri",
                veritabanindakiAd
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void aktifAdminDepartmanOlustururVeGunceller()
            throws Exception {
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);

        Personel admin = personelKaydet(
                "departman.admin.aktor",
                "Departman",
                "Admin",
                adminRolu,
                departman,
                true
        );

        String adminToken = token(admin, RolAdi.ADMIN);

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + adminToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "Finans"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ad").value("Finans"));

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + adminToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi":
                                          "Kurumsal Teknoloji"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        assertEquals(
                "Kurumsal Teknoloji",
                departmanRepository
                        .findById(departman.getDepartmanId())
                        .orElseThrow()
                        .getDepartmanAdi()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"PERSONEL", "YONETICI"}
    )
    void personelVeYoneticiDepartmanYonetemez(
            RolAdi rolAdi
    ) throws Exception {
        int rolId = switch (rolAdi) {
            case PERSONEL -> 2;
            case YONETICI -> 3;
            default -> throw new IllegalArgumentException();
        };

        Rol yetkisizRol = rolKaydet(rolId, rolAdi);

        Personel yetkisizAktor = personelKaydet(
                "departman." + rolAdi.name().toLowerCase(),
                "Yetkisiz",
                rolAdi.name(),
                yetkisizRol,
                departman,
                true
        );

        String yetkisizToken =
                token(yetkisizAktor, rolAdi);

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + yetkisizToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "Finans"
                            }
                            """))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + yetkisizToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden());

        assertEquals(
                1L,
                departmanRepository.count()
        );
        assertEquals(
                "Bilgi Teknolojileri",
                departmanRepository
                        .findById(departman.getDepartmanId())
                        .orElseThrow()
                        .getDepartmanAdi()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"HR", "ADMIN"}
    )
    void pasifHrVeAdminDepartmanYonetemez(
            RolAdi rolAdi
    ) throws Exception {
        Personel pasifAktor;

        if (rolAdi == RolAdi.HR) {
            hrAktor.setCalisiyorMu(false);
            pasifAktor =
                    personelRepository.saveAndFlush(hrAktor);
        } else {
            Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);

            pasifAktor = personelKaydet(
                    "departman.pasif.admin",
                    "Pasif",
                    "Admin",
                    adminRolu,
                    departman,
                    false
            );
        }

        String pasifToken = token(pasifAktor, rolAdi);

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + pasifToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "Finans"
                            }
                            """))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + pasifToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden());

        assertEquals(
                1L,
                departmanRepository.count()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner()
            throws Exception {
        String gecersizToken =
                jwtService.generateToken(
                        "veritabaninda-olmayan-personel",
                        RolAdi.HR
                );

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + gecersizToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "Finans"
                            }
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message").value(
                                "Personel bulunamadı."
                        )
                );

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + gecersizToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message").value(
                                "Personel bulunamadı."
                        )
                );

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void tokenOlmadanDepartmanYonetilemez()
            throws Exception {
        mockMvc.perform(post("/api/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "Finans"
                            }
                            """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isUnauthorized());

        assertEquals(
                1L,
                departmanRepository.count()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanDepartmanIdsi400Doner(
            int departmanId
    ) throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departmanId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message").value(
                                "Departman ID pozitif olmalıdır."
                        )
                );
    }

    @Test
    void bulunamayanDepartman404Doner()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                999999
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message").value(
                                "Departman bulunamadı."
                        )
                );
    }

    @Test
    void duplicateDepartmanOlusturma409Doner()
            throws Exception {
        departmanKaydet("Finans");

        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi": "  finans  "
                            }
                            """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.message").value(
                                "Departman zaten mevcut."
                        )
                );

        assertEquals(
                2L,
                departmanRepository.count()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void baskaDepartmanaDuplicateOlacakGuncelleme409Doner()
            throws Exception {
        Departman finans = departmanKaydet("Finans");

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "  finans  "
                                    }
                                    """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.message").value(
                                "Departman zaten mevcut."
                        )
                );

        Departman degismeyen =
                departmanRepository
                        .findById(departman.getDepartmanId())
                        .orElseThrow();

        assertEquals(
                "Bilgi Teknolojileri",
                degismeyen.getDepartmanAdi()
        );
        assertEquals(
                "Finans",
                departmanRepository
                        .findById(finans.getDepartmanId())
                        .orElseThrow()
                        .getDepartmanAdi()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void ayniNormalizeDepartmanAdiNoOpOlur()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi":
                                          "  Bilgi Teknolojileri  "
                                    }
                                    """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Departman degismeyen =
                departmanRepository
                        .findById(departman.getDepartmanId())
                        .orElseThrow();

        assertEquals(
                "Bilgi Teknolojileri",
                degismeyen.getDepartmanAdi()
        );
        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void yalnizHarfBicimiDegisikligiGuncellenir()
            throws Exception {
        Departman finans = departmanKaydet("finans");

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                finans.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi": "Finans"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Departman guncellenen =
                departmanRepository
                        .findById(finans.getDepartmanId())
                        .orElseThrow();

        assertEquals(
                "Finans",
                guncellenen.getDepartmanAdi()
        );
    }

    @Test
    void hrDepartmanOlustururVeTrimEdilmisKaydiDoner()
            throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "departmanAdi":
                                      "  İnsan Kaynakları  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.id").isNumber()
                )
                .andExpect(
                        jsonPath("$.ad")
                                .value("İnsan Kaynakları")
                );

        assertEquals(
                2L,
                departmanRepository.count()
        );

        Integer kayitSayisi =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM departman
                        WHERE departman_adi = ?
                        """,
                        Integer.class,
                        "İnsan Kaynakları"
                );

        assertEquals(1, kayitSayisi);
    }

    @Test
    void departmanOlusturmaGuvenliAuditOlusturur()
            throws Exception {
        mockMvc.perform(post("/api/departments")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "departmanAdi":
                                  "  İnsan Kaynakları  "
                            }
                            """))
                .andExpect(status().isCreated());

        Map<String, Object> audit = tekAuditKaydi();

        assertEquals(
                "DEPARTMAN_EKLENDI",
                audit.get("eylem_tipi")
        );
        assertEquals(
                hrAktor.getPersonelId(),
                audit.get("personel_id")
        );
        assertEquals(
                "departman.hr.aktor",
                audit.get("kullanici_adi")
        );
        assertEquals(
                "Departman oluşturuldu: İnsan Kaynakları",
                audit.get("detay")
        );
        assertNull(audit.get("hedef_personel_id"));
    }

    @Test
    void caseDegisikligiDepartmanGuncellemeAuditiOlusturur()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                departman.getDepartmanId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                    {
                                      "departmanAdi":
                                          "bilgi teknolojileri"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        Map<String, Object> audit = tekAuditKaydi();

        assertEquals(
                "DEPARTMAN_GUNCELLENDI",
                audit.get("eylem_tipi")
        );
        assertEquals(
                hrAktor.getPersonelId(),
                audit.get("personel_id")
        );
        assertEquals(
                "departman.hr.aktor",
                audit.get("kullanici_adi")
        );
        assertEquals(
                "Departman güncellendi: "
                        + "Bilgi Teknolojileri"
                        + " -> "
                        + "bilgi teknolojileri",
                audit.get("detay")
        );
        assertNull(audit.get("hedef_personel_id"));
    }

    @Test
    void renameAyniIdVePersonelDepartmanIliskisiniKorur()
            throws Exception {
        Integer mevcutDepartmanId =
                departman.getDepartmanId();

        mockMvc.perform(
                        patch(
                                "/api/departments/{departmanId}",
                                mevcutDepartmanId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "departmanAdi":
                                              "Kurumsal Teknoloji"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Departman guncellenen =
                departmanRepository
                        .findById(mevcutDepartmanId)
                        .orElseThrow();

        assertEquals(
                mevcutDepartmanId,
                guncellenen.getDepartmanId()
        );
        assertEquals(
                "Kurumsal Teknoloji",
                guncellenen.getDepartmanAdi()
        );

        Integer personelinDepartmanIdsi =
                jdbcTemplate.queryForObject(
                        """
                        SELECT departman_id
                        FROM personel
                        WHERE personel_id = ?
                        """,
                        Integer.class,
                        hrAktor.getPersonelId()
                );

        assertEquals(
                mevcutDepartmanId,
                personelinDepartmanIdsi
        );
        assertEquals(
                1L,
                departmanRepository.count()
        );
    }

    private Rol rolKaydet(
            int rolId,
            RolAdi rolAdi
    ) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);

        return rolRepository.save(rol);
    }

    private Map<String, Object> tekAuditKaydi() {
        assertEquals(
                1L,
                auditLogRepository.count()
        );

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

    private Departman departmanKaydet(String ad) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);

        return departmanRepository.saveAndFlush(
                yeniDepartman
        );
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman personelDepartmani,
            boolean aktif
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(
                LocalDate.of(1995, 1, 1)
        );
        personel.setePosta(
                kullaniciAdi + "@test.local"
        );
        personel.setDepartman(personelDepartmani);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);

        return personelRepository.saveAndFlush(
                personel
        );
    }

    private String token(
            Personel personel,
            RolAdi rolAdi
    ) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        DepartmanRollbackTestService
        departmanRollbackTestService(
                DepartmanYonetimService service
        ) {
            return new DepartmanRollbackTestService(
                    service
            );
        }
    }

    static class DepartmanRollbackTestService {

        private final DepartmanYonetimService service;

        DepartmanRollbackTestService(
                DepartmanYonetimService service
        ) {
            this.service = service;
        }

        @Transactional
        public void olusturVeHataFirlat(
                String aktorId,
                DepartmanAdiRequest request
        ) {
            service.departmanOlustur(
                    aktorId,
                    request
            );

            throw new IllegalStateException(
                    "Rollback testi için dış hata."
            );
        }

        @Transactional
        public void guncelleVeHataFirlat(
                String aktorId,
                Integer departmanId,
                DepartmanAdiRequest request
        ) {
            service.departmanGuncelle(
                    aktorId,
                    departmanId,
                    request
            );

            throw new IllegalStateException(
                    "Rollback testi için dış hata."
            );
        }

        @Transactional
        public void silVeHataFirlat(
                String aktorId,
                Integer departmanId
        ) {
            service.departmanSil(aktorId, departmanId);

            throw new IllegalStateException(
                    "Rollback testi için dış hata."
            );
        }
    }

}
