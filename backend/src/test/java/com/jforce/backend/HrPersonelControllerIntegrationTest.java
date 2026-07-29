package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.JWTService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
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

    private Personel hr;
    private Departman departman;

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
}