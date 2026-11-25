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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.jforce.backend.service.PersonelService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;



import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(HrPersonelControllerIntegrationTest.RollbackTestConfiguration.class)
class HrPersonelControllerIntegrationTest extends AbstractIntegrationTest {
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
    private RezervasyonRepository rezervasyonRepository;

    @Autowired
    private OdaRepository odaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PersonelPasiflestirmeRollbackTestService rollbackTestService;

    private Personel hr;
    private Departman departman;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        jdbcTemplate.update("DELETE FROM ekipman_talep");
        jdbcTemplate.update("DELETE FROM ekipman_bilgileri");
        jdbcTemplate.update("DELETE FROM ekipman_tipi");
        jdbcTemplate.update("DELETE FROM durum_turu");
        rezervasyonRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelRepository.deleteAll();
        odaRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        Rol personelRolu = new Rol();
        personelRolu.setRolId(2);
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        rolRepository.save(personelRolu);

        Rol hrRolu = new Rol();
        hrRolu.setRolId(4);
        hrRolu.setRolAdi(RolAdi.HR);
        hrRolu = rolRepository.save(hrRolu);

        departman = new Departman();
        departman.setDepartmanAdi("İnsan Kaynakları");
        departman = departmanRepository.save(departman);

        hr = new Personel();
        hr.setKullaniciAdi("test.hr");
        hr.setParolaHash(passwordEncoder.encode("Test1234!"));
        hr.setAdi("Test");
        hr.setSoyadi("HR");
        hr.setDogumTarihi(LocalDate.of(1990, 1, 1));
        hr.setePosta("hr@test.local");
        hr.setDepartman(departman);
        hr.setRol(hrRolu);
        hr.setCalisiyorMu(true);
        hr.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        hr.setParolaDegistirilmeliMi(false);

        hr = personelRepository.saveAndFlush(hr);
    }

    private Personel testPersoneliKaydet(String kullaniciAdi, String ePosta,String ad, Rol rol, Departman personelDepartmani) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash(passwordEncoder.encode("Test1234!"));
        personel.setAdi(ad);
        personel.setSoyadi("Test Soyad");
        personel.setDogumTarihi(LocalDate.of(1995, 5, 10));
        personel.setePosta(ePosta);
        personel.setDepartman(personelDepartmani);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setParolaDegistirilmeliMi(false);

        return personelRepository.saveAndFlush(personel);
    }

    private Rol testRoluKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Departman testDepartmaniKaydet(String departmanAdi) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(departmanAdi);
        return departmanRepository.save(yeniDepartman);
    }

    @Test
    void hrPersonelOlusturuncaSunucuDegerleriVeAuditKaydedilir() throws Exception {
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(post("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "kullaniciAdi": "Yeni.Personel",
                              "ad": "Yeni",
                              "soyad": "Personel",
                              "dogumTarihi": "1995-05-10",
                              "ePosta": "YENI.PERSONEL@TEST.LOCAL",
                              "departmanId": %d,
                              "isGirisTarihi": "2026-07-29"
                            }
                            """.formatted(departman.getDepartmanId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.personelId").isNotEmpty())
                .andExpect(jsonPath("$.kullaniciAdi")
                        .value("yeni.personel"));

        //username normalizasyon çalışıyor mu
        Personel kaydedilen = personelRepository.findByKullaniciAdi("yeni.personel").orElseThrow();

        //rol personel mi
        Integer kaydedilenRolId = kaydedilen.getRol().getRolId();
        Rol kaydedilenRol = rolRepository.findById(kaydedilenRolId).orElseThrow();

        assertEquals(RolAdi.PERSONEL, kaydedilenRol.getRolAdi());

        //eposta normalizasyonu
        assertEquals("yeni.personel@test.local", kaydedilen.getePosta());
        //kullanıcı oluşturulunca paroladegistirilmelimi degeri true olmalı
        assertTrue(kaydedilen.getParolaDegistirilmeliMi());
        assertTrue(kaydedilen.getCalisiyorMu());
        assertNull(kaydedilen.getYonetici());

        //sifre kullanıcı adıyla aynı
        assertTrue(passwordEncoder.matches("yeni.personel", kaydedilen.getParolaHash()));
        //sicil no yazıldımı,auto-increment çalıştımı
        assertNotNull(kaydedilen.getSicilNumarasi());
        assertEquals(hr.getSicilNumarasi() + 1, kaydedilen.getSicilNumarasi());
        //loglandımı + doğru değerlerle loglandımı
        List<AuditLog> auditKayitlari = auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());
        assertEquals(AuditEylem.PERSONEL_EKLENDI, auditKayitlari.getFirst().getEylemTipi());
        assertEquals(hr.getPersonelId(), auditKayitlari.getFirst().getPersonel().getPersonelId());
        assertEquals(kaydedilen.getPersonelId(), auditKayitlari.getFirst().getHedefPersonel().getPersonelId());
    }
    @Test
    void personelRoluPersonelOlusturamaz() throws Exception {
        String token = jwtService.generateToken("personel-id", RolAdi.PERSONEL);

        mockMvc.perform(post("/api/hr/personel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "kullaniciAdi": "yetkisiz.personel",
                              "ad": "Yetkisiz",
                              "soyad": "Personel",
                              "dogumTarihi": "1995-05-10",
                              "ePosta": "yetkisiz.personel@test.local",
                              "departmanId": %d,
                              "isGirisTarihi": "2026-07-29"
                            }
                            """.formatted(departman.getDepartmanId())))
                .andExpect(status().isForbidden());

        // Yalnızca setUp'ta oluşturulan HR bulunmalı.
        assertEquals(1L, personelRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void normalizeEdilmisAyniKullaniciAdiIkinciKezOlusturulamaz() throws Exception {
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        String ilkRequest = """
            {
              "kullaniciAdi": "AYNI.KULLANICI",
              "ad": "Aynı",
              "soyad": "Kullanıcı",
              "dogumTarihi": "1995-05-10",
              "ePosta": "ilk.kullanici@test.local",
              "departmanId": %d,
              "isGirisTarihi": "2026-07-29"
            }
            """.formatted(departman.getDepartmanId());

        String ikinciRequest = """
            {
              "kullaniciAdi": "ayni.kullanici",
              "ad": "Aynı",
              "soyad": "Kullanıcı",
              "dogumTarihi": "1995-05-10",
              "ePosta": "ikinci.kullanici@test.local",
              "departmanId": %d,
              "isGirisTarihi": "2026-07-29"
            }
            """.formatted(departman.getDepartmanId());

        mockMvc.perform(post("/api/hr/personel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ilkRequest))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/hr/personel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ikinciRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        // HR ve yalnızca bir yeni personel.
        assertEquals(2L, personelRepository.count());

        // Yalnız başarılı oluşturma audit üretmeli.
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void hrYalnizPersonelAdiniGuncellerDigerAlanlariKorur() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedefPersonel = new Personel();
        hedefPersonel.setKullaniciAdi("guncellenecek.personel");
        hedefPersonel.setParolaHash(passwordEncoder.encode("Test1234!"));
        hedefPersonel.setAdi("Eski Ad");
        hedefPersonel.setSoyadi("Korunacak Soyad");
        hedefPersonel.setDogumTarihi(LocalDate.of(1995, 5, 10));
        hedefPersonel.setePosta("guncellenecek.personel@test.local");
        hedefPersonel.setDepartman(departman);
        hedefPersonel.setRol(personelRolu);
        hedefPersonel.setCalisiyorMu(true);
        hedefPersonel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        hedefPersonel.setParolaDegistirilmeliMi(false);

        hedefPersonel = personelRepository.saveAndFlush(hedefPersonel);

        String hedefPersonelId = hedefPersonel.getPersonelId();

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedefPersonelId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Yeni Ad"
                                }
                                """)).andExpect(status().isNoContent());

        Personel guncellenenPersonel = personelRepository.findById(hedefPersonelId).orElseThrow();

        assertEquals("Yeni Ad", guncellenenPersonel.getAdi());

        assertEquals("Korunacak Soyad", guncellenenPersonel.getSoyadi());

        assertEquals("guncellenecek.personel@test.local", guncellenenPersonel.getePosta());

        List<AuditLog> auditKayitlari = auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog auditLog = auditKayitlari.getFirst();

        assertEquals(AuditEylem.PERSONEL_GUNCELLENDI, auditLog.getEylemTipi());

        assertEquals(hr.getPersonelId(), auditLog.getPersonel().getPersonelId());

        assertEquals(hedefPersonelId, auditLog.getHedefPersonel().getPersonelId());

        assertTrue(auditLog.getDetay().contains("ad"));
    }

    @Test
    void hrTelefonNumarasiniNullGondererekTemizler() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("telefon.test", "telefon.test@test.local", "Telefon", personelRolu, departman);

        hedef.setTelNo("0555 111 22 33");
        hedef = personelRepository.saveAndFlush(hedef);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "telNo": null
                                }
                                """)).andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertNull(guncellenen.getTelNo());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void bosGuncellemeRequesti400Doner() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("bos.request", "bos.request@test.local", "Boş", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void departmanDegisirkenYoneticiAlaniGonderilmezse400Doner() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("departman.eksik", "departman.eksik@test.local", "Departman", personelRolu, departman);

        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi("Bilgi Teknolojileri");
        yeniDepartman = departmanRepository.save(yeniDepartman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "departmanId": %d
                                }
                                """.formatted(yeniDepartman.getDepartmanId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertEquals(departman.getDepartmanId(), degismeyen.getDepartman().getDepartmanId());

        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void departmanVeAyniDepartmandakiYoneticiBirlikteDegistirilir() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolId(3);
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        yoneticiRolu = rolRepository.save(yoneticiRolu);

        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi("Yazılım");
        yeniDepartman = departmanRepository.save(yeniDepartman);

        Personel yeniYonetici = testPersoneliKaydet("yeni.yonetici", "yeni.yonetici@test.local", "Yönetici", yoneticiRolu, yeniDepartman);

        Personel hedef = testPersoneliKaydet("departman.hedef", "departman.hedef@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "departmanId": %d,
                                  "yoneticiId": "%s"
                                }
                                """.formatted(yeniDepartman.getDepartmanId(), yeniYonetici.getPersonelId()
                                ))).andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertEquals(yeniDepartman.getDepartmanId(), guncellenen.getDepartman().getDepartmanId());

        assertEquals(yeniYonetici.getPersonelId(), guncellenen.getYonetici().getPersonelId());

        AuditLog auditLog = auditLogRepository.findAll().getFirst();

        assertEquals(AuditEylem.PERSONEL_GUNCELLENDI, auditLog.getEylemTipi());

        assertTrue(auditLog.getDetay().contains("departmanId"));

        assertTrue(auditLog.getDetay().contains("yoneticiId"));
    }

    @Test
    void personelRoluPersonelGuncelleyemez() throws Exception {
        String token = jwtService.generateToken("yetkisiz-personel-id", RolAdi.PERSONEL);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", "hedef-personel-id")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Yetkisiz Güncelleme"
                                }
                                """)
                ).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void baskaPersonelinEPostasi409Doner() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("eposta.hedef", "hedef@test.local", "Hedef", personelRolu, departman);

        testPersoneliKaydet("eposta.sahibi", "kullanilan@test.local", "E-posta Sahibi", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ePosta": "KULLANILAN@TEST.LOCAL"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertEquals("hedef@test.local", degismeyen.getePosta());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ayniDegerTekrarGonderilirseAuditOlusmaz() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("ayni.deger", "ayni.deger@test.local", "Mevcut Ad", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Mevcut Ad"
                                }
                                """)).andExpect(status().isNoContent());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adNullGonderilirse400Doner() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("ad.null", "ad.null@test.local", "Mevcut Ad", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": null
                                }
                                """)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertEquals("Mevcut Ad", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yoneticiIdNullGonderilirseYoneticiKaldirilir() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel mevcutYonetici = testPersoneliKaydet("mevcut.yonetici", "mevcut.yonetici@test.local", "Mevcut Yönetici", yoneticiRolu, departman);

        Personel hedef = testPersoneliKaydet("yonetici.kaldir", "yonetici.kaldir@test.local", "Hedef", personelRolu, departman);

        hedef.setYonetici(mevcutYonetici);
        hedef = personelRepository.saveAndFlush(hedef);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": null
                                }
                                """)).andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
        assertNull(guncellenen.getYonetici());
        AuditLog auditLog = auditLogRepository.findAll().getFirst();
        assertEquals(AuditEylem.PERSONEL_GUNCELLENDI, auditLog.getEylemTipi());
        assertTrue(auditLog.getDetay().contains("yoneticiId"));
    }

    @Test
    void yoneticiAyniDepartmandaTekBasinaDegistirilebilir() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel yeniYonetici = testPersoneliKaydet("tek.yonetici", "tek.yonetici@test.local", "Yeni Yönetici", yoneticiRolu, departman);
        Personel hedef = testPersoneliKaydet("yonetici.hedef", "yonetici.hedef@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                        patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(yeniYonetici.getPersonelId()))).andExpect(status().isNoContent());

        Personel guncellenen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();
        assertEquals(departman.getDepartmanId(), guncellenen.getDepartman().getDepartmanId());
        assertEquals(yeniYonetici.getPersonelId(), guncellenen.getYonetici().getPersonelId());
    }
    @Test
    void personelKendisiniYoneticiYapamaz() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("kendi.yoneticisi", "kendi.yoneticisi@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(hedef.getPersonelId()))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifPersonelYoneticiOlarakAtanamaz() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel pasifYonetici = testPersoneliKaydet("pasif.yonetici", "pasif.yonetici@test.local", "Pasif Yönetici", yoneticiRolu, departman);

        pasifYonetici.setCalisiyorMu(false);
        pasifYonetici = personelRepository.saveAndFlush(pasifYonetici);

        Personel hedef = testPersoneliKaydet("pasif.hedef", "pasif.hedef@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(pasifYonetici.getPersonelId()))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yoneticiRolundeOlmayanPersonelYoneticiOlarakAtanamaz()
            throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel normalPersonel = testPersoneliKaydet("normal.personel", "normal.personel@test.local", "Normal Personel", personelRolu, departman);
        Personel hedef = testPersoneliKaydet("rol.hedef", "rol.hedef@test.local", "Hedef", personelRolu, departman);
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(normalPersonel.getPersonelId()))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void farkliDepartmandakiYoneticiAtanamaz() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Departman farkliDepartman = testDepartmaniKaydet("Farklı Departman");

        Personel farkliYonetici = testPersoneliKaydet("farkli.yonetici", "farkli.yonetici@test.local", "Farklı Yönetici", yoneticiRolu, farkliDepartman);
        Personel hedef = testPersoneliKaydet("farkli.hedef", "farkli.hedef@test.local", "Hedef", personelRolu, departman);
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(farkliYonetici.getPersonelId()))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hedefPersonelBulunamazsa404Doner() throws Exception {

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", "olmayan-personel-id")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Yeni Ad"
                                }
                                """)
                ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void departmanBulunamazsa404Doner() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("olmayan.departman", "olmayan.departman@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(
                patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "departmanId": 999999,
                                  "yoneticiId": null
                                }
                                """)
                ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yoneticiBulunamazsa404Doner() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("olmayan.yonetici", "olmayan.yonetici@test.local", "Hedef", personelRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "yoneticiId": "olmayan-yonetici-id"
                                }
                                """)
                ).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void farkliDepartmandakiYoneticiylePersonelOlusturulamaz() throws Exception {

        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Departman farkliDepartman = testDepartmaniKaydet("Operasyon");

        Personel farkliYonetici = testPersoneliKaydet("olusturma.farkli.yonetici", "olusturma.farkli.yonetici@test.local", "Farklı Yönetici", yoneticiRolu, farkliDepartman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(post("/api/hr/personel")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "kullaniciAdi": "farkli.departman.yeni",
                                  "ad": "Yeni",
                                  "soyad": "Personel",
                                  "dogumTarihi": "1995-05-10",
                                  "ePosta": "farkli.departman.yeni@test.local",
                                  "departmanId": %d,
                                  "isGirisTarihi": "2020-01-01",
                                  "yoneticiId": "%s"
                                }
                                """.formatted(departman.getDepartmanId(), farkliYonetici.getPersonelId()))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(personelRepository.findByKullaniciAdi("farkli.departman.yeni").isEmpty());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifYoneticiylePersonelOlusturulamaz() throws Exception {

        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel pasifYonetici = testPersoneliKaydet("olusturma.pasif.yonetici", "olusturma.pasif.yonetici@test.local", "Pasif Yönetici", yoneticiRolu, departman);

        pasifYonetici.setCalisiyorMu(false);
        pasifYonetici = personelRepository.saveAndFlush(pasifYonetici);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(post("/api/hr/personel")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "kullaniciAdi": "pasif.yonetici.yeni",
                                  "ad": "Yeni",
                                  "soyad": "Personel",
                                  "dogumTarihi": "1995-05-10",
                                  "ePosta": "pasif.yonetici.yeni@test.local",
                                  "departmanId": %d,
                                  "isGirisTarihi": "2020-01-01",
                                  "yoneticiId": "%s"
                                }
                                """.formatted(departman.getDepartmanId(), pasifYonetici.getPersonelId()))
                ).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertTrue(personelRepository.findByKullaniciAdi("pasif.yonetici.yeni").isEmpty());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrNormalPersoneliPasiflestirir() throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Personel hedef = testPersoneliKaydet("pasif.hedef", "pasif.hedef@test.local", "Pasif Hedef", personelRolu, departman);
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token
                                )).andExpect(status().isNoContent());

        Personel pasifHedef = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertFalse(pasifHedef.getCalisiyorMu());
        assertEquals(LocalDate.now(), pasifHedef.getIsCikisTarihi());

        List<AuditLog> auditKayitlari = auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());
        AuditLog auditLog = auditKayitlari.getFirst();

        assertEquals(AuditEylem.PERSONEL_PASIFE_ALINDI, auditLog.getEylemTipi());
        assertEquals(hr.getPersonelId(), auditLog.getPersonel().getPersonelId());
        assertEquals(hedef.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
    }

    @Test
    void personelPasiflestirmeBekleyenEkipmanTalepleriniIptalEderAcikZimmetiKorur()
            throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL)
                .orElseThrow();
        Personel hedef = testPersoneliKaydet(
                "ekipman.pasif.hedef",
                "ekipman.pasif.hedef@test.local",
                "Ekipman Pasif Hedef",
                personelRolu,
                departman
        );
        int tipId = ekipmanTipiKaydet("Pasifleştirme Laptop");
        String bekleyenTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAY_BEKLIYOR", "Bekleyen"
        );
        String ikinciBekleyenTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAY_BEKLIYOR", "İkinci bekleyen"
        );
        String onaylanmisTalepId = ekipmanTalebiKaydet(
                hedef, tipId, "ONAYLANDI", "Terminal"
        );
        String[] zimmetVeCihaz = acikZimmetKaydet(hedef, tipId);

        mockMvc.perform(patch(
                        "/api/hr/personel/{personelId}/pasiflestir",
                        hedef.getPersonelId()
                ).header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + jwtService.generateToken(
                                hr.getPersonelId(), RolAdi.HR
                        )
                ))
                .andExpect(status().isNoContent());

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
        assertEquals(hr.getPersonelId(), iptalEdilen.get("islem_yapan_id"));
        assertNotNull(iptalEdilen.get("islem_tarihi"));
        assertNull(iptalEdilen.get("karar_notu"));
        var ikinciIptalEdilen = jdbcTemplate.queryForMap(
                """
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi,
                       t.karar_notu
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                ikinciBekleyenTalepId
        );
        assertEquals("IPTAL_EDILDI", ikinciIptalEdilen.get("durum_adi"));
        assertEquals(hr.getPersonelId(), ikinciIptalEdilen.get("islem_yapan_id"));
        assertEquals(iptalEdilen.get("islem_tarihi"), ikinciIptalEdilen.get("islem_tarihi"));
        assertNull(ikinciIptalEdilen.get("karar_notu"));
        assertEquals(
                "ONAYLANDI",
                ekipmanTalepDurumu(onaylanmisTalepId)
        );
        assertNull(jdbcTemplate.queryForObject(
                "SELECT iade_tarihi FROM zimmetleme_bilgileri WHERE zimmet_id = ?",
                LocalDateTime.class,
                zimmetVeCihaz[0]
        ));
        assertEquals(
                "PERSONELDE",
                jdbcTemplate.queryForObject(
                        "SELECT ekipman_durum FROM ekipman_bilgileri WHERE ekipman_id = ?",
                        String.class,
                        zimmetVeCihaz[1]
                )
        );

        List<AuditLog> auditler = auditLogRepository.findAll();
        assertEquals(3, auditler.size());
        List<AuditLog> iptalAuditleri = auditler.stream()
                .filter(a -> a.getEylemTipi()
                        == AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI)
                .toList();
        assertEquals(2, iptalAuditleri.size());
        assertTrue(iptalAuditleri.stream().allMatch(a ->
                hr.getPersonelId().equals(a.getPersonel().getPersonelId())
                        && hedef.getPersonelId().equals(a.getHedefPersonel().getPersonelId())
                        && "Ekipman talebi iptal edildi.".equals(a.getDetay())
        ));
    }

    @Test
    void adminYoneticiyiPasiflestirinceCalisanTokenVeAuditBirlikteGuncellenir() throws Exception {

        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel admin = testPersoneliKaydet("pasif.admin", "pasif.admin@test.local", "Admin", adminRolu, departman);
        Personel yonetici = testPersoneliKaydet("pasif.yonetici", "pasif.yonetici@test.local", "Yönetici", yoneticiRolu, departman);
        Personel aktifCalisan = testPersoneliKaydet("aktif.calisan", "aktif.calisan@test.local", "Aktif Çalışan", personelRolu, departman);
        Personel pasifCalisan = testPersoneliKaydet("pasif.calisan", "pasif.calisan@test.local", "Pasif Çalışan", personelRolu, departman);

        aktifCalisan.setYonetici(yonetici);

        pasifCalisan.setYonetici(yonetici);
        pasifCalisan.setCalisiyorMu(false);

        personelRepository.saveAllAndFlush(List.of(aktifCalisan, pasifCalisan));

        refreshTokenService.createToken(yonetici);

        assertEquals(1L, refreshTokenRepository.count());
        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", yonetici.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());

        Personel kayitliYonetici = personelRepository.findById(yonetici.getPersonelId()).orElseThrow();
        Personel kayitliAktifCalisan = personelRepository.findById(aktifCalisan.getPersonelId()).orElseThrow();
        Personel kayitliPasifCalisan = personelRepository.findById(pasifCalisan.getPersonelId()).orElseThrow();

        assertFalse(kayitliYonetici.getCalisiyorMu());
        assertEquals(LocalDate.now(), kayitliYonetici.getIsCikisTarihi());

        assertNull(kayitliAktifCalisan.getYonetici());
        assertNull(kayitliPasifCalisan.getYonetici());

        assertEquals(0L, refreshTokenRepository.count());
        List<AuditLog> auditKayitlari = auditLogRepository.findAll();
        assertEquals(1, auditKayitlari.size());

        AuditLog auditLog = auditKayitlari.getFirst();
        assertEquals(AuditEylem.PERSONEL_PASIFE_ALINDI, auditLog.getEylemTipi());
        assertEquals(admin.getPersonelId(), auditLog.getPersonel().getPersonelId());
        assertEquals(yonetici.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
        assertEquals("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 2. İptal edilen rezervasyon sayısı: 0", auditLog.getDetay());
    }

    @Test
    void personelPasiflestirmeBaslamamisRezervasyonlariAtomikIptalEder()
            throws Exception {
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL)
                .orElseThrow();
        Personel hedef = testPersoneliKaydet(
                "rezervasyon.hedef", "rezervasyon.hedef@test.local",
                "Rezervasyon Hedef", personelRolu, departman
        );
        Personel baskaOwner = testPersoneliKaydet(
                "rezervasyon.baska", "rezervasyon.baska@test.local",
                "Rezervasyon Başka", personelRolu, departman
        );
        var oda = new com.jforce.backend.model.entity.Oda();
        oda.setOdaAd("Büyük Salon");
        oda.setOdaKapasite(12);
        oda = odaRepository.saveAndFlush(oda);

        String bugunBaslamamis = rezervasyonKaydet(
                hedef, oda, LocalDate.of(2026, 9, 10), 11, 12, "Bugün"
        );
        String gelecek = rezervasyonKaydet(
                hedef, oda, LocalDate.of(2026, 9, 11), 9, 10, "Gelecek"
        );
        String baslamis = rezervasyonKaydet(
                hedef, oda, LocalDate.of(2026, 9, 10), 9, 11, "Başlamış"
        );
        String gecmis = rezervasyonKaydet(
                hedef, oda, LocalDate.of(2026, 9, 9), 9, 10, "Geçmiş"
        );
        String zatenIptal = rezervasyonKaydet(
                hedef, oda, LocalDate.of(2026, 9, 12), 9, 10, "İptal"
        );
        rezervasyonRepository.findById(zatenIptal).ifPresent(rezervasyon -> {
            rezervasyon.setDurum(
                    com.jforce.backend.model.enums.RezervasyonDurum.IPTAL_EDILDI
            );
            rezervasyonRepository.saveAndFlush(rezervasyon);
        });
        String baskaOwnerGelecek = rezervasyonKaydet(
                baskaOwner, oda, LocalDate.of(2026, 9, 13), 9, 10, "Başka"
        );

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir",
                        hedef.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hr.getPersonelId(), RolAdi.HR
                                )))
                .andExpect(status().isNoContent());

        assertEquals("IPTAL_EDILDI", rezervasyonDurumu(bugunBaslamamis));
        assertEquals("IPTAL_EDILDI", rezervasyonDurumu(gelecek));
        assertEquals("AKTIF", rezervasyonDurumu(baslamis));
        assertEquals("AKTIF", rezervasyonDurumu(gecmis));
        assertEquals("IPTAL_EDILDI", rezervasyonDurumu(zatenIptal));
        assertEquals("AKTIF", rezervasyonDurumu(baskaOwnerGelecek));

        var iptalKayitlari = jdbcTemplate.queryForList(
                """
                SELECT rezervasyon_id, iptal_eden_id, iptal_nedeni,
                       iptal_zamani
                FROM rezervasyon
                WHERE rezervasyon_id IN (?, ?)
                ORDER BY rezervasyon_id
                """,
                bugunBaslamamis,
                gelecek
        );
        assertEquals(2, iptalKayitlari.size());
        iptalKayitlari.forEach(kayit -> {
            assertEquals(hr.getPersonelId(), kayit.get("iptal_eden_id"));
            assertEquals("Personel pasife alındı.", kayit.get("iptal_nedeni"));
            assertEquals(java.time.LocalDateTime.of(2026, 9, 10, 10, 20),
                    kayit.get("iptal_zamani"));
        });

        assertEquals(2L, auditLogRepository.findAll().stream()
                .filter(a -> a.getEylemTipi()
                        == AuditEylem.REZERVASYON_IPTAL_EDILDI)
                .count());
        AuditLog personelAudit = auditLogRepository.findAll().stream()
                .filter(a -> a.getEylemTipi()
                        == AuditEylem.PERSONEL_PASIFE_ALINDI)
                .findFirst()
                .orElseThrow();
        assertEquals(
                "Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 0. İptal edilen rezervasyon sayısı: 2",
                personelAudit.getDetay()
        );
    }

    @Test
    void hrYoneticiyiPasiflestiremez() throws Exception {
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel yonetici = testPersoneliKaydet("yasak.yonetici", "yasak.yonetici@test.local", "Yasak Yönetici", yoneticiRolu, departman);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", yonetici.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                ).andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository.findById(yonetici.getPersonelId()).orElseThrow();

        assertTrue(degismeyen.getCalisiyorMu());
        assertNull(degismeyen.getIsCikisTarihi());
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void adminRolundekiHedefPasiflestirilemez() throws Exception {
        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);

        Personel aktor = testPersoneliKaydet("admin.aktor", "admin.aktor@test.local", "Admin Aktör", adminRolu, departman);
        Personel hedef = testPersoneliKaydet("admin.hedef", "admin.hedef@test.local", "Admin Hedef", adminRolu, departman);

        String token = jwtService.generateToken(aktor.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(
                patch("/api/hr/personel/{personelId}/pasiflestir",
                        hedef.getPersonelId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        assertTrue(personelRepository.findById(hedef.getPersonelId()).orElseThrow().getCalisiyorMu());
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void hrKendisiniPasiflestiremez() throws Exception {
        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", hr.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isForbidden());

        Personel degismeyenHr = personelRepository
                .findById(hr.getPersonelId())
                .orElseThrow();

        assertTrue(degismeyenHr.getCalisiyorMu());
        assertNull(degismeyenHr.getIsCikisTarihi());
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void personelRoluPasiflestirmeEndpointineErisemez() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Personel aktor = testPersoneliKaydet("yetkisiz.aktor", "yetkisiz.aktor@test.local", "Yetkisiz Aktör", personelRolu, departman);
        Personel hedef = testPersoneliKaydet("yetkisiz.hedef", "yetkisiz.hedef@test.local", "Yetkisiz Hedef", personelRolu, departman);

        String token = jwtService.generateToken(aktor.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        ).andExpect(status().isForbidden());

        assertTrue(personelRepository.findById(hedef.getPersonelId()).orElseThrow().getCalisiyorMu());

        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void zatenPasifHedef204DonerVeYanEtkiOlusturmaz() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();

        Personel hedef = testPersoneliKaydet("zaten.pasif", "zaten.pasif@test.local", "Zaten Pasif", personelRolu, departman);

        refreshTokenService.createToken(hedef);

        LocalDate mevcutCikisTarihi = LocalDate.of(2025, 6, 1);

        hedef.setCalisiyorMu(false);
        hedef.setIsCikisTarihi(mevcutCikisTarihi);
        personelRepository.saveAndFlush(hedef);

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir",
                                hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());

        Personel degismeyen = personelRepository.findById(hedef.getPersonelId()).orElseThrow();

        assertFalse(degismeyen.getCalisiyorMu());
        assertEquals(mevcutCikisTarihi, degismeyen.getIsCikisTarihi());
        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    void olmayanHedefPasiflestirme404Doner() throws Exception {

        String token = jwtService.generateToken(hr.getPersonelId(), RolAdi.HR);
        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", "olmayan-personel-id")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        assertEquals(0L, auditLogRepository.count());
    }
    @Test
    @WithMockUser(roles = "ADMIN")
    void disTransactionHataVerirsePasiflikCalisanTokenVeAuditBirlikteRollbackOlur() {
        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);
        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Personel admin = testPersoneliKaydet("rollback.admin", "rollback.admin@test.local", "Rollback Admin", adminRolu, departman);

        Personel yonetici = testPersoneliKaydet("rollback.yonetici", "rollback.yonetici@test.local", "Rollback Yönetici", yoneticiRolu, departman);
        Personel calisan = testPersoneliKaydet("rollback.calisan", "rollback.calisan@test.local", "Rollback Çalışan", personelRolu, departman);

        calisan.setYonetici(yonetici);
        personelRepository.saveAndFlush(calisan);

        refreshTokenService.createToken(yonetici);
        var oda = new com.jforce.backend.model.entity.Oda();
        oda.setOdaAd("Rollback Oda");
        oda.setOdaKapasite(6);
        oda = odaRepository.saveAndFlush(oda);
        String rezervasyonId = rezervasyonKaydet(
                yonetici, oda, LocalDate.of(2026, 9, 11), 9, 10,
                "Rollback rezervasyonu"
        );
        int tipId = ekipmanTipiKaydet("Rollback Laptop");
        String bekleyenTalepId = ekipmanTalebiKaydet(
                yonetici, tipId, "ONAY_BEKLIYOR", "Rollback talebi"
        );

        assertEquals(1L, refreshTokenRepository.count());
        assertThrows(IllegalStateException.class, () -> rollbackTestService.pasiflestirVeHataFirlat(admin.getPersonelId(), yonetici.getPersonelId()));

        Personel kayitliYonetici = personelRepository.findById(yonetici.getPersonelId()).orElseThrow();
        Personel kayitliCalisan = personelRepository.findById(calisan.getPersonelId()).orElseThrow();

        assertTrue(kayitliYonetici.getCalisiyorMu());
        assertNull(kayitliYonetici.getIsCikisTarihi());

        assertEquals(yonetici.getPersonelId(), kayitliCalisan.getYonetici().getPersonelId());
        assertEquals(1L, refreshTokenRepository.count());
        assertEquals(0L, auditLogRepository.count());
        assertEquals("AKTIF", rezervasyonDurumu(rezervasyonId));
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
    void pasifHrPersonelOlusturamaz() throws Exception {
        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        hr.setCalisiyorMu(false);
        personelRepository.saveAndFlush(hr);

        mockMvc.perform(post("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "kullaniciAdi": "pasif.hr.olusturdu",
                              "ad": "Yeni",
                              "soyad": "Personel",
                              "dogumTarihi": "1995-05-10",
                              "ePosta": "pasif.hr.olusturdu@test.local",
                              "departmanId": %d,
                              "isGirisTarihi": "2024-01-01",
                              "yoneticiId": null
                            }
                            """.formatted(
                                departman.getDepartmanId()
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals(1L, personelRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifHrPersonelGuncelleyemez() throws Exception {
        Rol personelRolu = rolRepository
                .findByRolAdi(RolAdi.PERSONEL)
                .orElseThrow();

        Personel hedef = testPersoneliKaydet(
                "pasif.hr.guncelleme.hedef",
                "pasif.hr.guncelleme.hedef@test.local",
                "Değişmeyen",
                personelRolu,
                departman
        );

        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        hr.setCalisiyorMu(false);
        personelRepository.saveAndFlush(hr);

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertEquals("Değişmeyen", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifHrPersonelPasiflestiremez() throws Exception {
        Rol personelRolu = rolRepository
                .findByRolAdi(RolAdi.PERSONEL)
                .orElseThrow();

        Personel hedef = testPersoneliKaydet(
                "pasif.hr.pasiflestirme.hedef",
                "pasif.hr.pasiflestirme.hedef@test.local",
                "Aktif Hedef",
                personelRolu,
                departman
        );

        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        hr.setCalisiyorMu(false);
        personelRepository.saveAndFlush(hr);

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/pasiflestir",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertTrue(degismeyen.getCalisiyorMu());
        assertNull(degismeyen.getIsCikisTarihi());
        assertEquals(0L, auditLogRepository.count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        @Primary
        Clock rezervasyonTestClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-10T07:20:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }

        @Bean
        PersonelPasiflestirmeRollbackTestService personelPasiflestirmeRollbackTestService(PersonelService personelService) {
            return new PersonelPasiflestirmeRollbackTestService(personelService);
        }
    }

    static class PersonelPasiflestirmeRollbackTestService {

        private final PersonelService personelService;
        PersonelPasiflestirmeRollbackTestService(PersonelService personelService) {
            this.personelService = personelService;
        }

        @Transactional
        public void pasiflestirVeHataFirlat(String aktorId, String hedefPersonelId) {
            personelService.personelPasiflestir(aktorId, hedefPersonelId);
            throw new IllegalStateException("Rollback davranışını doğrulamak için test hatası.");}
    }
    @Test
    void adminHrPersoneliniPasiflestirebilir() throws Exception {

        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);
        Personel admin = testPersoneliKaydet("hr.hedef.admin", "hr.hedef.admin@test.local", "Admin", adminRolu, departman);

        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir",
                hr.getPersonelId()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isNoContent());

        Personel pasifHr = personelRepository.findById(hr.getPersonelId()).orElseThrow();

        assertFalse(pasifHr.getCalisiyorMu());
        assertEquals(LocalDate.now(), pasifHr.getIsCikisTarihi());

        AuditLog auditLog = auditLogRepository.findAll().getFirst();

        assertEquals(admin.getPersonelId(), auditLog.getPersonel().getPersonelId());
        assertEquals(hr.getPersonelId(), auditLog.getHedefPersonel().getPersonelId());
    }

    @Test
    void hrKendiKaydiniGuncelleyemez() throws Exception {
        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                hr.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(hr.getPersonelId())
                .orElseThrow();

        assertEquals("Test", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminKendiKaydiniGuncelleyemez() throws Exception {
        Rol adminRolu = testRoluKaydet(
                1,
                RolAdi.ADMIN
        );

        Personel admin = testPersoneliKaydet(
                "guncelleme.admin.aktor",
                "guncelleme.admin.aktor@test.local",
                "Admin",
                adminRolu,
                departman
        );

        String token = jwtService.generateToken(
                admin.getPersonelId(),
                RolAdi.ADMIN
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                admin.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(admin.getPersonelId())
                .orElseThrow();

        assertEquals("Admin", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrYoneticiHedefiGuncelleyemez() throws Exception {
        Rol yoneticiRolu = testRoluKaydet(
                3,
                RolAdi.YONETICI
        );

        Personel yonetici = testPersoneliKaydet(
                "guncelleme.yonetici.hedef",
                "guncelleme.yonetici.hedef@test.local",
                "Yönetici",
                yoneticiRolu,
                departman
        );

        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(yonetici.getPersonelId())
                .orElseThrow();

        assertEquals("Yönetici", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrAdminHedefiGuncelleyemez() throws Exception {
        Rol adminRolu = testRoluKaydet(
                1,
                RolAdi.ADMIN
        );

        Personel admin = testPersoneliKaydet(
                "guncelleme.admin.hedef",
                "guncelleme.admin.hedef@test.local",
                "Admin Hedef",
                adminRolu,
                departman
        );

        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                admin.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(admin.getPersonelId())
                .orElseThrow();

        assertEquals("Admin Hedef", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminBaskaAdminHedefiGuncelleyemez() throws Exception {
        Rol adminRolu = testRoluKaydet(
                1,
                RolAdi.ADMIN
        );

        Personel aktor = testPersoneliKaydet(
                "guncelleme.admin.aktor",
                "guncelleme.admin.aktor@test.local",
                "Admin Aktör",
                adminRolu,
                departman
        );
        Personel hedef = testPersoneliKaydet(
                "guncelleme.admin.hedef",
                "guncelleme.admin.hedef@test.local",
                "Admin Hedef",
                adminRolu,
                departman
        );

        String token = jwtService.generateToken(
                aktor.getPersonelId(),
                RolAdi.ADMIN
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Değiştirildi"
                                    }
                                    """)
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyen = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertEquals("Admin Hedef", degismeyen.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrBaskaHrHedefiGuncelleyebilir() throws Exception {
        Rol hrRolu = rolRepository
                .findByRolAdi(RolAdi.HR)
                .orElseThrow();

        Personel hedef = testPersoneliKaydet(
                "guncelleme.hr.hedef",
                "guncelleme.hr.hedef@test.local",
                "HR Hedef",
                hrRolu,
                departman
        );

        String token = jwtService.generateToken(
                hr.getPersonelId(),
                RolAdi.HR
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Güncel HR"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository
                .findById(hedef.getPersonelId())
                .orElseThrow();

        assertEquals("Güncel HR", guncellenen.getAdi());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void adminYoneticiHedefiGuncelleyebilir() throws Exception {
        Rol adminRolu = testRoluKaydet(
                1,
                RolAdi.ADMIN
        );
        Rol yoneticiRolu = testRoluKaydet(
                3,
                RolAdi.YONETICI
        );

        Personel admin = testPersoneliKaydet(
                "guncelleme.admin.aktor",
                "guncelleme.admin.aktor@test.local",
                "Admin",
                adminRolu,
                departman
        );
        Personel yonetici = testPersoneliKaydet(
                "guncelleme.yonetici.hedef",
                "guncelleme.yonetici.hedef@test.local",
                "Yönetici",
                yoneticiRolu,
                departman
        );

        String token = jwtService.generateToken(
                admin.getPersonelId(),
                RolAdi.ADMIN
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "ad": "Güncel Yönetici"
                                    }
                                    """)
                )
                .andExpect(status().isNoContent());

        Personel guncellenen = personelRepository
                .findById(yonetici.getPersonelId())
                .orElseThrow();

        assertEquals(
                "Güncel Yönetici",
                guncellenen.getAdi()
        );
        assertEquals(1L, auditLogRepository.count());
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

    private String ekipmanTalepDurumu(String talepId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT d.durum_adi
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                String.class,
                talepId
        );
    }

    private String[] acikZimmetKaydet(Personel owner, int tipId) {
        String ekipmanId = UUID.randomUUID().toString();
        String zimmetId = UUID.randomUUID().toString();
        jdbcTemplate.update(
                """
                INSERT INTO ekipman_bilgileri (
                    ekipman_id, ekipman_tipi_id, marka, model,
                    seri_no, ekipman_durum, kullanim_disi_nedeni
                ) VALUES (?, ?, ?, ?, ?, 'PERSONELDE', null)
                """,
                ekipmanId,
                tipId,
                "Task9",
                "Açık Zimmet",
                "TASK9-" + UUID.randomUUID()
        );
        jdbcTemplate.update(
                """
                INSERT INTO zimmetleme_bilgileri (
                    zimmet_id, ekipman_id, personel_id,
                    teslim_tarihi, iade_tarihi, talep_id
                ) VALUES (?, ?, ?, ?, null, null)
                """,
                zimmetId,
                ekipmanId,
                owner.getPersonelId(),
                LocalDateTime.of(2026, 8, 30, 10, 0)
        );
        return new String[]{zimmetId, ekipmanId};
    }

    private String rezervasyonKaydet(
            Personel owner,
            com.jforce.backend.model.entity.Oda oda,
            LocalDate tarih,
            int baslangic,
            int bitis,
            String baslik
    ) {
        var rezervasyon = new com.jforce.backend.model.entity.Rezervasyon();
        rezervasyon.setOlusturan(owner);
        rezervasyon.setOda(oda);
        rezervasyon.setTarih(tarih);
        rezervasyon.setBaslangicSaat(baslangic);
        rezervasyon.setBitisSaat(bitis);
        rezervasyon.setBaslik(baslik);
        rezervasyon.setKatilimciSayisi(2);
        rezervasyon.setDurum(
                com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
        );
        rezervasyon.setOlusturmaZamani(
                java.time.LocalDateTime.of(2026, 9, 1, 9, 0)
        );
        return rezervasyonRepository.saveAndFlush(rezervasyon)
                .getRezervasyonId();
    }

    private String rezervasyonDurumu(String rezervasyonId) {
        return rezervasyonRepository.findById(rezervasyonId)
                .orElseThrow().getDurum().name();
    }

    @Test
    void ikiKisilikYoneticiDongusuOlusturulamaz() throws Exception {
        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel admin = testPersoneliKaydet(
                "dongu.admin", "dongu.admin@test.local", "Admin", adminRolu, departman
        );
        Personel altYonetici = testPersoneliKaydet(
                "dongu.alt", "dongu.alt@test.local", "Alt Yönetici", yoneticiRolu, departman
        );
        Personel ustYonetici = testPersoneliKaydet(
                "dongu.ust", "dongu.ust@test.local", "Üst Yönetici", yoneticiRolu, departman
        );
        altYonetici.setYonetici(ustYonetici);
        personelRepository.saveAndFlush(altYonetici);

        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", ustYonetici.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(altYonetici.getPersonelId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Yönetici ataması döngü oluşturamaz."));

        Personel degismeyenUst = personelRepository.findById(ustYonetici.getPersonelId()).orElseThrow();
        Personel degismeyenAlt = personelRepository.findById(altYonetici.getPersonelId()).orElseThrow();
        assertNull(degismeyenUst.getYonetici());
        assertEquals(ustYonetici.getPersonelId(), degismeyenAlt.getYonetici().getPersonelId());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void uzunYoneticiDongusuOlusturulamaz() throws Exception {
        Rol adminRolu = testRoluKaydet(1, RolAdi.ADMIN);
        Rol yoneticiRolu = testRoluKaydet(3, RolAdi.YONETICI);

        Personel admin = testPersoneliKaydet(
                "uzun.dongu.admin", "uzun.dongu.admin@test.local", "Admin", adminRolu, departman
        );
        Personel altYonetici = testPersoneliKaydet(
                "uzun.dongu.alt", "uzun.dongu.alt@test.local", "Alt Yönetici", yoneticiRolu, departman
        );
        Personel ortaYonetici = testPersoneliKaydet(
                "uzun.dongu.orta", "uzun.dongu.orta@test.local", "Orta Yönetici", yoneticiRolu, departman
        );
        Personel ustYonetici = testPersoneliKaydet(
                "uzun.dongu.ust", "uzun.dongu.ust@test.local", "Üst Yönetici", yoneticiRolu, departman
        );
        ortaYonetici.setYonetici(ustYonetici);
        altYonetici.setYonetici(ortaYonetici);
        personelRepository.saveAllAndFlush(List.of(ortaYonetici, altYonetici));

        String token = jwtService.generateToken(admin.getPersonelId(), RolAdi.ADMIN);

        mockMvc.perform(patch("/api/hr/personel/{personelId}", ustYonetici.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "yoneticiId": "%s"
                                }
                                """.formatted(altYonetici.getPersonelId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Yönetici ataması döngü oluşturamaz."));

        Personel degismeyenUst = personelRepository.findById(ustYonetici.getPersonelId()).orElseThrow();
        Personel degismeyenOrta = personelRepository.findById(ortaYonetici.getPersonelId()).orElseThrow();
        Personel degismeyenAlt = personelRepository.findById(altYonetici.getPersonelId()).orElseThrow();
        assertNull(degismeyenUst.getYonetici());
        assertEquals(ustYonetici.getPersonelId(), degismeyenOrta.getYonetici().getPersonelId());
        assertEquals(ortaYonetici.getPersonelId(), degismeyenAlt.getYonetici().getPersonelId());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void islemiYapanPersonelBulunamazsa404Doner() throws Exception {

        Rol personelRolu = rolRepository.findByRolAdi(RolAdi.PERSONEL).orElseThrow();
        Personel hedef = testPersoneliKaydet("aktor.yok.hedef", "aktor.yok.hedef@test.local", "Hedef", personelRolu, departman);
        String token = jwtService.generateToken("olmayan-aktor-id", RolAdi.HR);

        mockMvc.perform(patch("/api/hr/personel/{personelId}/pasiflestir", hedef.getPersonelId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                ).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        assertTrue(personelRepository.findById(hedef.getPersonelId()).orElseThrow().getCalisiyorMu());
        assertEquals(0L, auditLogRepository.count());
    }
}
