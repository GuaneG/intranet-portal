package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.ProfilService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AdminPersonelYetenekControllerIntegrationTest.RollbackTestConfiguration.class)
class AdminPersonelYetenekControllerIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private AdminPersonelYetenekRollbackTestService rollbackTestService;

    private Personel admin;
    private Personel hedefPersonel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");

        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        Rol personelRolu = rolKaydet(2, RolAdi.PERSONEL);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        admin = personelKaydet(
                "test.admin",
                "admin@test.local",
                "Test",
                "Admin",
                adminRolu,
                departman
        );

        hedefPersonel = personelKaydet(
                "hedef.personel",
                "hedef.personel@test.local",
                "Hedef",
                "Personel",
                personelRolu,
                departman
        );
    }

    @Test
    void adminPersonelHedefininYetenekKumesiniDegistirir() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");
        Integer sqlId = yetenekOlustur("SQL");
        Integer kubernetesId = yetenekOlustur("Kubernetes");

        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        personelYetenekOlustur(hedefPersonel.getPersonelId(), dockerId);

        String token = jwtService.generateToken(
                admin.getPersonelId(),
                RolAdi.ADMIN
        );

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "yetenekIdleri": [%d, %d, %d]
                                }
                                """.formatted(
                                dockerId,
                                sqlId,
                                kubernetesId
                        )))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(
                List.of(dockerId, sqlId, kubernetesId).stream()
                        .sorted()
                        .toList(),
                hedefinYetenekIdleri()
        );
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void adminIzinliHedefRollerininYetenekleriniGuncelleyebilir(RolAdi hedefRolu) throws Exception {
        hedefPersonelinRolunuDegistir(hedefRolu);
        Integer javaId = yetenekOlustur("Java");

        adminIstegi(hedefPersonel, "[%d]".formatted(javaId))
                .andExpect(status().isNoContent());

        assertEquals(List.of(javaId), hedefinYetenekIdleri());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void adminOlmayanRollerPersonelYetenekleriniYonetemez(RolAdi aktorRolu) throws Exception {
        admininRolunuDegistir(aktorRolu);
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        String token = jwtService.generateToken(admin.getPersonelId(), aktorRolu);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yetenekIdleri": []}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void adminAyniKumedeOlsaBileKendiYetenekleriniYonetemez() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(admin.getPersonelId(), javaId);

        adminIstegi(admin, "[%d]".formatted(javaId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(List.of(javaId), personelinYetenekIdleri(admin.getPersonelId()));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminAyniKumedeOlsaBileBaskaAdmininYetenekleriniYonetemez() throws Exception {
        hedefPersonelinRolunuDegistir(RolAdi.ADMIN);
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        adminIstegi(hedefPersonel, "[%d]".formatted(javaId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void adminAyniKumedeOlsaBilePasifHedefinYetenekleriniYonetemez() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        hedefPersonel.setCalisiyorMu(false);
        hedefPersonel = personelRepository.saveAndFlush(hedefPersonel);

        adminIstegi(hedefPersonel, "[%d]".formatted(javaId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void pasifAdminPersonelYetenekleriniYonetemez() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        admin.setCalisiyorMu(false);
        admin = personelRepository.saveAndFlush(admin);

        adminIstegi(hedefPersonel, "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void tokenRoluAdminOlsaBileDbRoluAdminOlmayanAktorReddedilir() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        admininRolunuDegistir(RolAdi.HR);

        adminIstegi(hedefPersonel, "[]")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void tokendakiAdminBulunamazsa404Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        String token = jwtService.generateToken("veritabaninda-olmayan-admin", RolAdi.ADMIN);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yetenekIdleri": []}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void hedefPersonelBulunamazsa404Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        "veritabaninda-olmayan-hedef"
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yetenekIdleri": []}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void tokenOlmadanPersonelYetenekleriYonetilemez() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yetenekIdleri": []}
                                """))
                .andExpect(status().isUnauthorized());

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void bosListeHedefinButunYetenekleriniKaldirir() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        personelYetenekOlustur(hedefPersonel.getPersonelId(), dockerId);

        adminIstegi(hedefPersonel, "[]")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(List.of(), hedefinYetenekIdleri());
    }

    @Test
    void ayniYetenekKumesiFarkliSiradaNoOpOlurVeAuditOlusturmaz() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        personelYetenekOlustur(hedefPersonel.getPersonelId(), dockerId);

        adminIstegi(
                hedefPersonel,
                "[%d, %d]".formatted(dockerId, javaId)
        ).andExpect(status().isNoContent());

        yeteneklerVeAuditDegismedi(
                List.of(javaId, dockerId).stream().sorted().toList()
        );
    }

    @Test
    void nullYetenekIdleriListesi400Doner() throws Exception {
        gecersizRequestYanEtkisizdir("null");
    }

    @Test
    void nullYetenekIdsi400Doner() throws Exception {
        gecersizRequestYanEtkisizdir("[null]");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanYetenekIdsi400Doner(int yetenekId) throws Exception {
        gecersizRequestYanEtkisizdir("[%d]".formatted(yetenekId));
    }

    @Test
    void duplicateYetenekIdsi400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        adminIstegi(
                hedefPersonel,
                "[%d, %d]".formatted(javaId, javaId)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void bilinmeyenYetenekIdsi404VeAtomikOlur() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        int bilinmeyenId = javaId + 100_000;

        adminIstegi(
                hedefPersonel,
                "[%d, %d]".formatted(javaId, bilinmeyenId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Yetenek bulunamadı."));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void requestBodyGonderilmezse400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void bozukJsonGovdesi400Doner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);
        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedefPersonel.getPersonelId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bozuk-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @Test
    void gercekDegisiklikTekVeGuvenliAuditKaydiOlusturur() throws Exception {
        Integer javaId = yetenekOlustur("Java");

        adminIstegi(hedefPersonel, "[%d]".formatted(javaId))
                .andExpect(status().isNoContent());

        assertEquals(1L, auditLogRepository.count());

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                        SELECT eylem_tipi, personel_id, hedef_personel_id,
                               kullanici_adi, detay
                        FROM audit_log
                        """
        );

        assertEquals(AuditEylem.PROFIL_GUNCELLENDI.name(), audit.get("eylem_tipi"));
        assertEquals(admin.getPersonelId(), audit.get("personel_id"));
        assertEquals(hedefPersonel.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(admin.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals("Güncellenen alanlar: yetenekler", audit.get("detay"));

        String detay = (String) audit.get("detay");
        assertFalse(detay.contains(javaId.toString()));
        assertFalse(detay.contains("Java"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disTransactionHataVerirseYeteneklerVeAuditBirlikteRollbackOlur() {
        Integer javaId = yetenekOlustur("Java");
        Integer dockerId = yetenekOlustur("Docker");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        YetenekUpdateRequest request = new YetenekUpdateRequest(List.of(dockerId));

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.guncelleVeHataFirlat(
                        admin.getPersonelId(),
                        hedefPersonel.getPersonelId(),
                        request
                )
        );

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ePosta,
            String ad,
            String soyad,
            Rol rol,
            Departman departman
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(ePosta);
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(personel);
    }

    private Integer yetenekOlustur(String yetenekAdi) {
        jdbcTemplate.update(
                "INSERT INTO yetenek (yetenek_adi) VALUES (?)",
                yetenekAdi
        );

        return jdbcTemplate.queryForObject(
                "SELECT yetenek_id FROM yetenek WHERE yetenek_adi = ?",
                Integer.class,
                yetenekAdi
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

    private List<Integer> hedefinYetenekIdleri() {
        return personelinYetenekIdleri(hedefPersonel.getPersonelId());
    }

    private List<Integer> personelinYetenekIdleri(String personelId) {
        return jdbcTemplate.queryForList(
                """
                        SELECT yetenek_id
                        FROM personel_yetenek
                        WHERE personel_id = ?
                        ORDER BY yetenek_id
                """,
                Integer.class,
                personelId
        );
    }

    private org.springframework.test.web.servlet.ResultActions adminIstegi(
            Personel hedef,
            String yetenekIdleriJson
    ) throws Exception {
        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        return mockMvc.perform(put(
                        "/api/hr/personel/{personelId}/yetenekler",
                        hedef.getPersonelId()
                )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"yetenekIdleri": %s}
                                """.formatted(yetenekIdleriJson)));
    }

    private void hedefPersonelinRolunuDegistir(RolAdi rolAdi) {
        hedefPersonel.setRol(roluGetirVeyaKaydet(rolAdi));
        hedefPersonel = personelRepository.saveAndFlush(hedefPersonel);
    }

    private void admininRolunuDegistir(RolAdi rolAdi) {
        admin.setRol(roluGetirVeyaKaydet(rolAdi));
        admin = personelRepository.saveAndFlush(admin);
    }

    private Rol roluGetirVeyaKaydet(RolAdi rolAdi) {
        int rolId = switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };

        return rolRepository.findById(rolId)
                .orElseGet(() -> rolKaydet(rolId, rolAdi));
    }

    private void yeteneklerVeAuditDegismedi(List<Integer> beklenenYetenekIdleri) {
        assertEquals(beklenenYetenekIdleri, hedefinYetenekIdleri());
        assertEquals(0L, auditLogRepository.count());
    }

    private void gecersizRequestYanEtkisizdir(String yetenekIdleriJson) throws Exception {
        Integer javaId = yetenekOlustur("Java");
        personelYetenekOlustur(hedefPersonel.getPersonelId(), javaId);

        adminIstegi(hedefPersonel, yetenekIdleriJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        yeteneklerVeAuditDegismedi(List.of(javaId));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        AdminPersonelYetenekRollbackTestService adminPersonelYetenekRollbackTestService(
                ProfilService profilService
        ) {
            return new AdminPersonelYetenekRollbackTestService(profilService);
        }
    }

    static class AdminPersonelYetenekRollbackTestService {

        private final ProfilService profilService;

        AdminPersonelYetenekRollbackTestService(ProfilService profilService) {
            this.profilService = profilService;
        }

        @Transactional
        public void guncelleVeHataFirlat(
                String aktorId,
                String hedefPersonelId,
                YetenekUpdateRequest request
        ) {
            profilService.personelYetenekleriniAdminOlarakGuncelle(
                    aktorId,
                    hedefPersonelId,
                    request
            );

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }
    }
}
