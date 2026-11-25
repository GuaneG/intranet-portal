package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.RefreshTokenService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AdminParolaSifirlamaControllerIntegrationTest.RollbackTestConfiguration.class)
class AdminParolaSifirlamaControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String GECICI_PAROLA = "Gecici123!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private ParolaSifirlamaRollbackTestService rollbackTestService;

    private Personel admin;
    private Personel hedef;
    private Rol adminRolu;
    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;
    private Departman departman;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        adminRolu = rolOlustur(1, RolAdi.ADMIN);
        personelRolu = rolOlustur(2, RolAdi.PERSONEL);
        yoneticiRolu = rolOlustur(3, RolAdi.YONETICI);
        hrRolu = rolOlustur(4, RolAdi.HR);

        departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        admin = personelOlustur(
                "test_admin",
                "Test",
                "Admin",
                "admin@test.local",
                1001,
                adminRolu,
                departman
        );
        hedef = personelOlustur(
                "hedef_personel",
                "Hedef",
                "Personel",
                "hedef@test.local",
                1002,
                personelRolu,
                departman
        );
    }

    @Test
    void adminPersonelinParolasiniHashleyerekSifirlar() throws Exception {
        String eskiParolaHash = hedef.getParolaHash();
        assertFalse(hedef.getParolaDegistirilmeliMi());

        String accessToken = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedef.getPersonelId()
                        )
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "geciciParola": "Gecici123!"
                                        }
                                        """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Personel guncellenenHedef = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertNotEquals(eskiParolaHash, guncellenenHedef.getParolaHash());
        assertNotEquals(GECICI_PAROLA, guncellenenHedef.getParolaHash());
        assertTrue(passwordEncoder.matches(GECICI_PAROLA, guncellenenHedef.getParolaHash()));
        assertTrue(guncellenenHedef.getParolaDegistirilmeliMi());
    }

    @Test
    void sifirlamaRefreshTokenlariniIptalEderGuvenliAuditOlustururVeIlkParolaAkisinaZorlar() throws Exception {
        refreshTokenService.createToken(hedef);
        refreshTokenService.createToken(hedef);
        assertEquals(2L, refreshTokenRepository.count());

        String accessToken = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedef.getPersonelId()
                        )
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "geciciParola": "Gecici123!"
                                        }
                                        """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(0L, refreshTokenRepository.count());

        List<AuditLog> auditKayitlari = auditLogRepository.findAll();
        assertEquals(1, auditKayitlari.size());

        AuditLog auditLog = auditKayitlari.getFirst();
        assertEquals("PAROLA_SIFIRLANDI", auditLog.getEylemTipi().name());
        assertEquals(admin.getPersonelId(), auditLog.getPersonel().getPersonelId());
        assertEquals(hedef.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
        assertEquals("Personel parolası Admin tarafından sıfırlandı.", auditLog.getDetay());
        assertFalse(auditLog.getDetay().contains(GECICI_PAROLA));

        mockMvc.perform(post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "kullaniciAdi": "hedef_personel",
                                          "parola": "Gecici123!"
                                        }
                                        """))
                .andExpect(status().is(428))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertEquals(0L, refreshTokenRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void adminIzinliHedefRollerininParolasiniSifirlayabilir(RolAdi hedefRolu) throws Exception {
        Personel rolHedefi = personelOlustur(
                "izinli_hedef_" + hedefRolu.name().toLowerCase(),
                "Hedef",
                hedefRolu.name(),
                hedefRolu.name().toLowerCase() + "@test.local",
                2000 + hedefRolu.ordinal(),
                rolGetir(hedefRolu),
                departman
        );

        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, rolHedefi.getPersonelId())
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(rolHedefi.getPersonelId()).orElseThrow();
        assertTrue(passwordEncoder.matches(GECICI_PAROLA, guncellenen.getParolaHash()));
        assertTrue(guncellenen.getParolaDegistirilmeliMi());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void adminOlmayanRollerEndpointiKullanamaz(RolAdi aktorRolu) throws Exception {
        Personel aktor = personelOlustur(
                "aktor_" + aktorRolu.name().toLowerCase(),
                "Yetkisiz",
                aktorRolu.name(),
                "aktor." + aktorRolu.name().toLowerCase() + "@test.local",
                3000 + aktorRolu.ordinal(),
                rolGetir(aktorRolu),
                departman
        );
        String eskiHash = hedef.getParolaHash();

        parolaSifirlamaIstegi(aktor, aktorRolu, hedef.getPersonelId())
                .andExpect(status().isForbidden());

        basarisizIsteginYanEtkisizOldugunuDogrula(eskiHash);
    }

    @Test
    void adminKendisiniVeyaBaskaAdminiHedefleyemez() throws Exception {
        Personel baskaAdmin = personelOlustur(
                "baska_admin",
                "Başka",
                "Admin",
                "baska.admin@test.local",
                4001,
                adminRolu,
                departman
        );
        admin.setParolaHash(passwordEncoder.encode(GECICI_PAROLA));
        baskaAdmin.setParolaHash(passwordEncoder.encode(GECICI_PAROLA));
        admin = personelRepository.saveAndFlush(admin);
        baskaAdmin = personelRepository.saveAndFlush(baskaAdmin);

        String adminHash = admin.getParolaHash();
        String baskaAdminHash = baskaAdmin.getParolaHash();

        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, admin.getPersonelId())
                .andExpect(status().isForbidden());
        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, baskaAdmin.getPersonelId())
                .andExpect(status().isForbidden());

        assertEquals(adminHash, personelRepository.findById(admin.getPersonelId()).orElseThrow().getParolaHash());
        assertEquals(baskaAdminHash, personelRepository.findById(baskaAdmin.getPersonelId()).orElseThrow().getParolaHash());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifAdminParolaSifirlayamaz() throws Exception {
        admin.setCalisiyorMu(false);
        admin = personelRepository.saveAndFlush(admin);
        String eskiHash = hedef.getParolaHash();

        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, hedef.getPersonelId())
                .andExpect(status().isForbidden());

        basarisizIsteginYanEtkisizOldugunuDogrula(eskiHash);
    }

    @Test
    void tokendakiAdminBulunamazsa404Doner() throws Exception {
        String eskiHash = hedef.getParolaHash();

        parolaSifirlamaIstegi("olmayan-admin-id", RolAdi.ADMIN, hedef.getPersonelId())
                .andExpect(status().isNotFound());

        basarisizIsteginYanEtkisizOldugunuDogrula(eskiHash);
    }

    @Test
    void hedefPersonelBulunamazsa404Doner() throws Exception {
        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, "olmayan-hedef-id")
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void pasifIzinliHedefinParolasiSifirlanabilir(RolAdi hedefRolu) throws Exception {
        Personel pasifHedef = personelOlustur(
                "pasif_" + hedefRolu.name().toLowerCase(),
                "Pasif",
                hedefRolu.name(),
                "pasif." + hedefRolu.name().toLowerCase() + "@test.local",
                5000 + hedefRolu.ordinal(),
                rolGetir(hedefRolu),
                departman
        );
        pasifHedef.setCalisiyorMu(false);
        pasifHedef = personelRepository.saveAndFlush(pasifHedef);

        parolaSifirlamaIstegi(admin, RolAdi.ADMIN, pasifHedef.getPersonelId())
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(pasifHedef.getPersonelId()).orElseThrow();
        assertFalse(guncellenen.getCalisiyorMu());
        assertTrue(passwordEncoder.matches(GECICI_PAROLA, guncellenen.getParolaHash()));
        assertTrue(guncellenen.getParolaDegistirilmeliMi());
    }

    @Test
    void tokenOlmadanParolaSifirlama401Doner() throws Exception {
        String eskiHash = hedef.getParolaHash();

        mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedef.getPersonelId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "geciciParola": "Gecici123!"
                                        }
                                        """))
                .andExpect(status().isUnauthorized());

        basarisizIsteginYanEtkisizOldugunuDogrula(eskiHash);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "kisa", "hedef_personel", "Eski1234!"})
    void gecersizGeciciParola400VeYanEtkisizdir(String gecersizParola) throws Exception {
        refreshTokenService.createToken(hedef);
        String eskiHash = hedef.getParolaHash();
        String accessToken = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedef.getPersonelId()
                        )
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "geciciParola": "%s"
                                        }
                                        """.formatted(gecersizParola)))
                .andExpect(status().isBadRequest());

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
        assertEquals(eskiHash, degismeyen.getParolaHash());
        assertFalse(degismeyen.getParolaDegistirilmeliMi());
        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void geciciParolaAlaniEksikse400VeYanEtkisizdir() throws Exception {
        String eskiHash = hedef.getParolaHash();
        String accessToken = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedef.getPersonelId()
                        )
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());

        basarisizIsteginYanEtkisizOldugunuDogrula(eskiHash);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disTransactionHataVerirseParolaTokenVeAuditBirlikteRollbackOlur() {
        refreshTokenService.createToken(hedef);
        refreshTokenService.createToken(hedef);
        String eskiHash = hedef.getParolaHash();

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.sifirlaVeHataFirlat(
                        admin.getPersonelId(),
                        hedef.getPersonelId(),
                        GECICI_PAROLA
                )
        );

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
        assertEquals(eskiHash, degismeyen.getParolaHash());
        assertFalse(degismeyen.getParolaDegistirilmeliMi());
        assertEquals(2L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    private org.springframework.test.web.servlet.ResultActions parolaSifirlamaIstegi(
            Personel aktor,
            RolAdi tokenRolu,
            String hedefPersonelId
    ) throws Exception {
        return parolaSifirlamaIstegi(aktor.getPersonelId(), tokenRolu, hedefPersonelId);
    }

    private org.springframework.test.web.servlet.ResultActions parolaSifirlamaIstegi(
            String aktorId,
            RolAdi tokenRolu,
            String hedefPersonelId
    ) throws Exception {
        String accessToken = jwtService.generateToken(aktorId, tokenRolu);

        return mockMvc.perform(patch(
                                "/api/admin/personel/{personelId}/parola-sifirla",
                                hedefPersonelId
                        )
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "geciciParola": "Gecici123!"
                                        }
                                        """));
    }

    private void basarisizIsteginYanEtkisizOldugunuDogrula(String eskiHash) {
        Personel degismeyenHedef = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
        assertEquals(eskiHash, degismeyenHedef.getParolaHash());
        assertFalse(degismeyenHedef.getParolaDegistirilmeliMi());
        assertEquals(0L, auditLogRepository.count());
    }

    private Rol rolGetir(RolAdi rolAdi) {
        return switch (rolAdi) {
            case ADMIN -> adminRolu;
            case PERSONEL -> personelRolu;
            case YONETICI -> yoneticiRolu;
            case HR -> hrRolu;
        };
    }

    private Rol rolOlustur(Integer rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Personel personelOlustur(
            String kullaniciAdi,
            String ad,
            String soyad,
            String ePosta,
            Integer sicilNumarasi,
            Rol rol,
            Departman departman
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash(passwordEncoder.encode("Eski1234!"));
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(ePosta);
        personel.setSicilNumarasi(sicilNumarasi);
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(personel);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        ParolaSifirlamaRollbackTestService parolaSifirlamaRollbackTestService(ParolaService parolaService) {
            return new ParolaSifirlamaRollbackTestService(parolaService);
        }
    }

    static class ParolaSifirlamaRollbackTestService {

        private final ParolaService parolaService;

        ParolaSifirlamaRollbackTestService(ParolaService parolaService) {
            this.parolaService = parolaService;
        }

        @Transactional
        public void sifirlaVeHataFirlat(String aktorId, String hedefId, String geciciParola) {
            parolaService.adminParolaSifirla(aktorId, hedefId, geciciParola);
            throw new IllegalStateException("Rollback davranışını doğrulamak için test hatası.");
        }
    }
}
