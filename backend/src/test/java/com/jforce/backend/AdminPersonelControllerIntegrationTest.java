package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.PersonelService;
import com.jforce.backend.service.RefreshTokenService;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(AdminPersonelControllerIntegrationTest.RollbackTestConfiguration.class)
class AdminPersonelControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RolDegistirmeRollbackTestService rollbackTestService;

    private Rol personelRolu;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;
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
    private JdbcTemplate jdbcTemplate;

    private Personel admin;
    private Personel hedef;
    private Rol hrRolu;

    @BeforeEach
    public void setUp(){
        // Foreign key sırasına dikkat ederek temizle
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        jdbcTemplate.update("DELETE FROM ekipman_talep");
        jdbcTemplate.update("DELETE FROM ekipman_bilgileri");
        jdbcTemplate.update("DELETE FROM ekipman_tipi");
        jdbcTemplate.update("DELETE FROM durum_turu");
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        Rol adminRolu = new Rol();
        adminRolu.setRolId(1);
        adminRolu.setRolAdi(RolAdi.ADMIN);
        adminRolu = rolRepository.save(adminRolu);

        personelRolu = new Rol();
        personelRolu.setRolId(2);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        personelRolu = rolRepository.save(personelRolu);

        hrRolu = new Rol();
        hrRolu.setRolId(4);
        hrRolu.setRolAdi(RolAdi.HR);
        hrRolu = rolRepository.save(hrRolu);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        admin = new Personel();
        admin.setKullaniciAdi("test_admin");
        admin.setParolaHash("test-hash");
        admin.setAdi("Test");
        admin.setSoyadi("Admin");
        admin.setDogumTarihi(LocalDate.of(1990, 1, 1));
        admin.setePosta("admin@test.local");
        admin.setSicilNumarasi(1001);
        admin.setDepartman(departman);
        admin.setRol(adminRolu);
        admin.setCalisiyorMu(true);
        admin.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        admin = personelRepository.save(admin);

        hedef = new Personel();
        hedef.setKullaniciAdi("hedef_personel");
        hedef.setParolaHash("test-hash");
        hedef.setAdi("Hedef");
        hedef.setSoyadi("Personel");
        hedef.setDogumTarihi(LocalDate.of(1995, 1, 1));
        hedef.setePosta("hedef@test.local");
        hedef.setSicilNumarasi(1002);
        hedef.setDepartman(departman);
        hedef.setRol(personelRolu);
        hedef.setCalisiyorMu(true);
        hedef.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        hedef = personelRepository.save(hedef);
    }

    //security method kontrol preauthorize
    @Test
    void personelRoluRolDegistirmeEndpointineErisemez() throws Exception {
        String token = jwtService.generateToken(
                "aktor-personel-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/admin/personel/hedef-id/rol")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "rol": "HR"
                                        }
                                        """))
                .andExpect(status().isForbidden())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Bu işlem için yetkiniz bulunmuyor."))
                .andExpect(jsonPath("$.path")
                        .value("/api/admin/personel/hedef-id/rol"));
    }

    //notnull + valid akışı kontrol
    @Test
    void rolAlaniBosOldugunda400Doner() throws Exception {
        String token = jwtService.generateToken("admin-id", RolAdi.ADMIN);

        mockMvc.perform(patch("/api/admin/personel/hedef-id/rol")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "rol": null
                                    }
                                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Geçersiz istek"))
                .andExpect(jsonPath("$.path")
                        .value("/api/admin/personel/hedef-id/rol"));
    }

    @Test
    void adminPersonelinRolunuDegistirinceTokenlarSilinirVeAuditOlusur() throws Exception {
        //refresh token oluştur
        refreshTokenService.createToken(hedef);
        refreshTokenService.createToken(hedef);
        //tokenlar oluştumu
        assertEquals(2L, refreshTokenRepository.count());
        //admin için jwt oluştur
        String accessToken = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);
        //authentication + authorization
        mockMvc.perform(patch("/api/admin/personel/{personelId}/rol",hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "rol": "HR"
                                    }
                                    """)).andExpect(status().isNoContent());

        //rolu degisen peronseli al
        Personel guncelHedef = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        //rol değiştimi
        assertEquals(hrRolu.getRolId(), guncelHedef.getRol().getRolId());

        //refresh tokenları silindimi
        assertEquals(0L, refreshTokenRepository.count());

        //loglandımı rol değişimi
        List<AuditLog> auditKayitlari = auditLogRepository.findAll();
        assertEquals(1, auditKayitlari.size());

        //log doğruluğu kontrol
        AuditLog auditLog = auditKayitlari.getFirst();
        assertEquals(AuditEylem.ROL_DEGISTIRILDI, auditLog.getEylemTipi());
        assertEquals(admin.getPersonelId(), auditLog.getPersonel().getPersonelId());
        assertEquals(hedef.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
        assertTrue(auditLog.getDetay().contains("PERSONEL -> HR"));
    }

    @Test
    void selfServiceDisiRoleGecisBekleyenEkipmanTalepleriniIptalEder()
            throws Exception {
        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolId(3);
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        rolRepository.saveAndFlush(yoneticiRolu);
        int tipId = ekipmanTipiKaydet("Rol Değişimi Laptop");
        String bekleyenTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAY_BEKLIYOR", "Rol değişimi"
        );

        mockMvc.perform(patch(
                        "/api/admin/personel/{personelId}/rol",
                        hedef.getPersonelId()
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtService.generateToken(
                                admin.getPersonelId(), RolAdi.ADMIN
                        )
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "rol": "YONETICI"
                            }
                            """))
                .andExpect(status().isNoContent());

        assertEquals(
                "YONETICI",
                jdbcTemplate.queryForObject(
                        """
                        SELECT r.rol_adi
                        FROM personel p
                        JOIN rol r ON r.rol_id = p.rol_id
                        WHERE p.personel_id = ?
                        """,
                        String.class,
                        hedef.getPersonelId()
                )
        );
        var iptalEdilen = jdbcTemplate.queryForMap(
                """
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi,
                       t.karar_notu
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                bekleyenTalepId
        );
        assertEquals("IPTAL_EDILDI", iptalEdilen.get("durum_adi"));
        assertEquals(admin.getPersonelId(),
                iptalEdilen.get("islem_yapan_id"));
        assertNotNull(iptalEdilen.get("islem_tarihi"));
        assertNull(iptalEdilen.get("karar_notu"));

        List<AuditLog> auditler = auditLogRepository.findAll();
        assertEquals(2, auditler.size());
        AuditLog iptalAuditi = auditler.stream()
                .filter(a -> a.getEylemTipi()
                        == AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI)
                .findFirst()
                .orElseThrow();
        assertEquals(admin.getPersonelId(),
                iptalAuditi.getPersonel().getPersonelId());
        assertEquals(hedef.getPersonelId(),
                iptalAuditi.getHedefPersonel().getPersonelId());
        assertEquals("Ekipman talebi iptal edildi.", iptalAuditi.getDetay());
    }

    @Test
    void selfServiceIciRoleGecisBekleyenEkipmanTalebiniDegistirmez()
            throws Exception {
        int tipId = ekipmanTipiKaydet("Self-service Laptop");
        String bekleyenTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAY_BEKLIYOR", "Değişmeden kalmalı"
        );

        mockMvc.perform(patch(
                        "/api/admin/personel/{personelId}/rol",
                        hedef.getPersonelId()
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtService.generateToken(
                                admin.getPersonelId(), RolAdi.ADMIN
                        )
                ).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "rol": "HR"
                            }
                            """))
                .andExpect(status().isNoContent());

        var degismeyenTalep = jdbcTemplate.queryForMap(
                """
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                bekleyenTalepId
        );
        assertEquals("ONAY_BEKLIYOR", degismeyenTalep.get("durum_adi"));
        assertNull(degismeyenTalep.get("islem_yapan_id"));
        assertNull(degismeyenTalep.get("islem_tarihi"));
        assertEquals(1L, auditLogRepository.count());
        assertEquals(
                AuditEylem.ROL_DEGISTIRILDI,
                auditLogRepository.findAll().getFirst().getEylemTipi()
        );
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disTransactionHataVerirseRolTokenVeAuditBirlikteRollbackOlur() {
        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolId(3);
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        rolRepository.saveAndFlush(yoneticiRolu);
        int tipId = ekipmanTipiKaydet("Rol Rollback Laptop");
        String bekleyenTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAY_BEKLIYOR", "Rollback talebi"
        );
        refreshTokenService.createToken(hedef);
        refreshTokenService.createToken(hedef);

        assertEquals(2L, refreshTokenRepository.count());
        assertThrows(IllegalStateException.class, () -> rollbackTestService.rolDegistirVeHataFirlat(admin.getPersonelId(),hedef.getPersonelId(),RolAdi.YONETICI));

        Personel veritabanindakiHedef = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        // Rol değişikliği geri alınmış olmalı
        assertEquals(personelRolu.getRolId(), veritabanindakiHedef.getRol().getRolId());

        // Token silme işlemi geri alınmış olmalı
        assertEquals(2L, refreshTokenRepository.count());

        // Ortak transaction audit kaydı da geri alınmış olmalı
        assertEquals(0L, auditLogRepository.count());

        var rollbackTalebi = jdbcTemplate.queryForMap(
                """
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                bekleyenTalepId
        );
        assertEquals("ONAY_BEKLIYOR", rollbackTalebi.get("durum_adi"));
        assertNull(rollbackTalebi.get("islem_yapan_id"));
        assertNull(rollbackTalebi.get("islem_tarihi"));
    }

    @Test
    void pasifAdminPersonelRoluDegistiremez() throws Exception {
        String token = jwtService.generateToken(
                admin.getPersonelId(),
                RolAdi.ADMIN
        );

        admin.setCalisiyorMu(false);
        personelRepository.saveAndFlush(admin);

        mockMvc.perform(
                        patch(
                                "/api/admin/personel/{personelId}/rol",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "rol": "HR"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertEquals(
                personelRolu.getRolId(),
                degismeyen.getRol().getRolId()
        );
        assertEquals(0L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {
        @Bean
        RolDegistirmeRollbackTestService rolDegistirmeRollbackTestService(PersonelService personelService) {
            return new RolDegistirmeRollbackTestService(personelService);
        }
    }

    static class RolDegistirmeRollbackTestService {
        private final PersonelService personelService;
        RolDegistirmeRollbackTestService(PersonelService personelService) {
            this.personelService = personelService;
        }
        @Transactional
        public void rolDegistirVeHataFirlat(String aktorId, String hedefPersonelId, RolAdi yeniRol) {
            personelService.rolDegistir(aktorId, hedefPersonelId, yeniRol);
            throw new IllegalStateException("Rollback davranışını doğrulamak için test hatası.");
        }
    }

    private int ekipmanTipiKaydet(String tipAdi) {
        jdbcTemplate.update(
                "INSERT INTO ekipman_tipi (tip_adi, aktif_mi) VALUES (?, true)",
                tipAdi
        );
        return jdbcTemplate.queryForObject(
                "SELECT ekipman_tipi_id FROM ekipman_tipi WHERE tip_adi = ?",
                Integer.class,
                tipAdi
        );
    }

    private String ekipmanTalebiKaydet(
            Personel owner,
            int tipId,
            String durum,
            String aciklama
    ) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO durum_turu (durum_adi) VALUES ('IPTAL_EDILDI')"
        );
        jdbcTemplate.update(
                "INSERT IGNORE INTO durum_turu (durum_adi) VALUES (?)",
                durum
        );
        Integer durumId = jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                durum
        );
        String talepId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO ekipman_talep (
                    talep_id, personel_id, ekipman_tipi_id, aciklama,
                    talep_durum_id, talep_tarihi
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                talepId,
                owner.getPersonelId(),
                tipId,
                aciklama,
                durumId,
                LocalDateTime.of(2026, 8, 31, 9, 0)
        );
        return talepId;
    }
}
