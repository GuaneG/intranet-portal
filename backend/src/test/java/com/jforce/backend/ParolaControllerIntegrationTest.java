package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ParolaControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

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

    private Personel personel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        Rol personelRolu = new Rol();
        personelRolu.setRolId(2);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        personelRolu = rolRepository.save(personelRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        personel = new Personel();
        personel.setKullaniciAdi("test.kullanici");
        personel.setParolaHash(
                passwordEncoder.encode("Eski1234!")
        );
        personel.setAdi("Test");
        personel.setSoyadi("Kullanıcı");
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta("test.kullanici@test.local");
        personel.setDepartman(departman);
        personel.setRol(personelRolu);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setParolaDegistirilmeliMi(false);

        personel = personelRepository.saveAndFlush(personel);
    }

    @Test
    void ilkParolasiDegismemisPersonelLoginOlunca428VeTokenOlusmaz() throws Exception {
        personel.setParolaHash(passwordEncoder.encode("test.kullanici"));
        personel.setParolaDegistirilmeliMi(true);
        personelRepository.saveAndFlush(personel);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kullaniciAdi": "test.kullanici",
                                  "parola": "test.kullanici"
                                }
                                """))
                .andExpect(status().is(428))
                .andExpect(jsonPath("$.status").value(428))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        assertEquals(0L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ilkParolaDegisinceNormalLoginTokenVeCookieUretir() throws Exception {
        personel.setParolaHash(passwordEncoder.encode("test.kullanici"));
        personel.setParolaDegistirilmeliMi(true);
        personelRepository.saveAndFlush(personel);

        mockMvc.perform(patch("/api/auth/ilk-parola")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kullaniciAdi": "test.kullanici",
                                  "mevcutParola": "test.kullanici",
                                  "yeniParola": "Yeni1234!"
                                }
                                """))
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(personel.getPersonelId()).orElseThrow();

        assertFalse(guncellenen.getParolaDegistirilmeliMi());

        assertTrue(passwordEncoder.matches("Yeni1234!", guncellenen.getParolaHash()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "kullaniciAdi": "test.kullanici",
                                  "parola": "Yeni1234!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header()
                        .exists(HttpHeaders.SET_COOKIE));
    }

    @Test
    void normalParolaDegisinceRefreshTokenlarSilinirVeAuditOlusur() throws Exception {
        refreshTokenService.createToken(personel);
        refreshTokenService.createToken(personel);

        assertEquals(2L, refreshTokenRepository.count());

        String accessToken = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(patch("/api/profil/parola")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mevcutParola": "Eski1234!",
                                  "yeniParola": "Yeni1234!"
                                }
                                """))
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(personel.getPersonelId()).orElseThrow();

        assertTrue(passwordEncoder.matches("Yeni1234!", guncellenen.getParolaHash()));

        assertEquals(0L, refreshTokenRepository.count());

        List<AuditLog> auditKayitlari =
                auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog auditLog = auditKayitlari.getFirst();

        assertEquals(AuditEylem.PAROLA_DEGISTIRILDI,auditLog.getEylemTipi());

        assertEquals(personel.getPersonelId(), auditLog.getPersonel().getPersonelId());

        assertEquals(personel.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
    }
}