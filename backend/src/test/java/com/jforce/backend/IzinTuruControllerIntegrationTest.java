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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IzinTuruControllerIntegrationTest extends AbstractIntegrationTest {

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

    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;
    private Rol adminRolu;
    private Departman departman;
    private Personel personel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM izin_turu");

        personelRolu = rolKaydet(1, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(2, RolAdi.YONETICI);
        hrRolu = rolKaydet(3, RolAdi.HR);
        adminRolu = rolKaydet(4, RolAdi.ADMIN);
        departman = departmanKaydet("Yazılım");
        personel = personelKaydet(
                "izin.personel",
                "İzin",
                "Personel",
                personelRolu,
                true,
                null
        );
    }

    @Test
    void aktifPersonelIzinTurleriniAlfabetikVeGuvenliGorur()
            throws Exception {
        izinTuruOlustur("Yıllık İzin", true);
        izinTuruOlustur("Mazeret İzni", false);
        Integer hastalikIzniId = izinTuruOlustur("Hastalık İzni", false);

        mockMvc.perform(get("/api/leave-types")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(personel, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(hastalikIzniId))
                .andExpect(jsonPath("$[0].ad").value("Hastalık İzni"))
                .andExpect(jsonPath("$[0].yillikHaktanDuserMi").value(false))
                .andExpect(jsonPath("$[1].ad").value("Mazeret İzni"))
                .andExpect(jsonPath("$[2].ad").value("Yıllık İzin"))
                .andExpect(jsonPath("$[2].yillikHaktanDuserMi").value(true))
                .andExpect(jsonPath("$[0].izinTuruId").doesNotExist())
                .andExpect(jsonPath("$[0].izinTuruAdi").doesNotExist())
                .andExpect(jsonPath("$[0].parolaHash").doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void uygunYoneticiHrVeAdminIzinTurleriniGorur() throws Exception {
        Personel ustYonetici = personelKaydet(
                "ust.yonetici",
                "Üst",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel uygunYonetici = personelKaydet(
                "uygun.yonetici",
                "Uygun",
                "Yönetici",
                yoneticiRolu,
                true,
                ustYonetici
        );
        Personel hr = personelKaydet(
                "izin.hr",
                "İzin",
                "HR",
                hrRolu,
                true,
                null
        );
        Personel admin = personelKaydet(
                "izin.admin",
                "İzin",
                "Admin",
                adminRolu,
                true,
                null
        );

        izinTuruOlustur("Mazeret İzni", false);

        izinTurleriniAlabilir(uygunYonetici, RolAdi.YONETICI);
        izinTurleriniAlabilir(hr, RolAdi.HR);
        izinTurleriniAlabilir(admin, RolAdi.ADMIN);

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void kokYoneticiIzinTurleriniGoremez() throws Exception {
        Personel kokYonetici = personelKaydet(
                "kok.yonetici",
                "Kök",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/leave-types")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(kokYonetici, RolAdi.YONETICI)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifPersonelIzinTurleriniGoremez() throws Exception {
        String token = token(personel, RolAdi.PERSONEL);
        personel.setCalisiyorMu(false);
        personelRepository.saveAndFlush(personel);

        mockMvc.perform(get("/api/leave-types")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner() throws Exception {
        mockMvc.perform(get("/api/leave-types")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        "olmayan-personel-id",
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanIzinTurleriGoruntulenemez() throws Exception {
        mockMvc.perform(get("/api/leave-types"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertEquals(0L, auditLogRepository.count());
    }

    private void izinTurleriniAlabilir(Personel aktor, RolAdi rolAdi)
            throws Exception {
        mockMvc.perform(get("/api/leave-types")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, rolAdi)
                        ))
                .andExpect(status().isOk());
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
            boolean aktif,
            Personel yonetici
    ) {
        Personel yeniPersonel = new Personel();
        yeniPersonel.setKullaniciAdi(kullaniciAdi);
        yeniPersonel.setParolaHash("test-hash");
        yeniPersonel.setAdi(ad);
        yeniPersonel.setSoyadi(soyad);
        yeniPersonel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        yeniPersonel.setePosta(kullaniciAdi + "@test.local");
        yeniPersonel.setDepartman(departman);
        yeniPersonel.setRol(rol);
        yeniPersonel.setCalisiyorMu(aktif);
        yeniPersonel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        yeniPersonel.setYillikIzinHakki(14);
        yeniPersonel.setProfilFoto("def_pfp.png");
        yeniPersonel.setParolaDegistirilmeliMi(false);
        yeniPersonel.setYonetici(yonetici);
        return personelRepository.saveAndFlush(yeniPersonel);
    }

    private Integer izinTuruOlustur(String ad, boolean yillikHaktanDuserMi) {
        jdbcTemplate.update(
                """
                INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi)
                VALUES (?, ?)
                """,
                ad,
                yillikHaktanDuserMi
        );

        return jdbcTemplate.queryForObject(
                "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = ?",
                Integer.class,
                ad
        );
    }

    private String token(Personel aktor, RolAdi rolAdi) {
        return jwtService.generateToken(aktor.getPersonelId(), rolAdi);
    }
}
