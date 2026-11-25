package com.jforce.backend;


import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.ProfilService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import com.jforce.backend.model.dto.request.ProfilUpdateRequest;
import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.enums.AuditEylem;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import java.time.Year;
import java.util.List;
import java.math.BigDecimal;


import java.time.LocalDate;

@AutoConfigureMockMvc
class ProfilControllerIntegrationTest extends AbstractIntegrationTest {
    private static final LocalDate SABIT_BUGUN = LocalDate.of(2026, 8, 24);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;

    @Autowired
    private IzinBilgileriRepository izinBilgileriRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ProfilGuncelleRollbackTestService profilGuncelleRollbackTestService;

    private Personel personel;
    @BeforeEach
     void setUp() {
        auditLogRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");
        jdbcTemplate.update("DELETE FROM tatil");
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

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
        personel.setMezunOkul("Test Üniversitesi");
        personel.setMezunBolum("Bilgisayar Mühendisliği");
        personel.setTelNo("05550000000");

        personel = personelRepository.saveAndFlush(personel);
    }
    @Test
    void personelKendiProfiliniGoruntuler() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kullaniciAdi").value("test.personel"))
                .andExpect(jsonPath("$.ad").value("Test"))
                .andExpect(jsonPath("$.soyad").value("Personel"))
                .andExpect(jsonPath("$.ePosta").value("test.personel@test.local"))
                .andExpect(jsonPath("$.departman").value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$.rol").value("PERSONEL"))
                .andExpect(jsonPath("$.yillikIzinHakki").value(20))
                .andExpect(jsonPath("$.profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.kalanIzin").value(20))
                .andExpect(jsonPath("$.yetenekler").isEmpty())
                .andExpect(jsonPath("$.yonetici").doesNotExist());
    }
    @Test
    void tokenOlmadanProfilGoruntulenemez() throws Exception {
        mockMvc.perform(get("/api/profil"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
    @Test
    void yalnizOnaylananVeYillikHaktanDusenIzinlerKalanIzindenDusulur() throws Exception {

        Integer yillikIzinTuruId = izinTuruOlustur("Yıllık izin", true);

        Integer mazeretIzinTuruId = izinTuruOlustur("Mazeret izni", false);

        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);

        Integer onayBekliyorId = durumOlustur(TalepDurum.ONAY_BEKLIYOR);

        izinOlustur(yillikIzinTuruId, onaylandiId, 4);
        izinOlustur(yillikIzinTuruId, onayBekliyorId, 3);
        izinOlustur(mazeretIzinTuruId, onaylandiId, 2);

        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yillikIzinHakki").value(20))
                .andExpect(jsonPath("$.kalanIzin").value(16));
    }

    @Test
    void yeniBekleyenTalepProfildekiKalanIzinAnlaminiDegistirmez()
            throws Exception {
        Integer yillikIzinTuruId = izinTuruOlustur("Yıllık izin", true);
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);
        durumOlustur(TalepDurum.ONAY_BEKLIYOR);
        izinOlustur(yillikIzinTuruId, onaylandiId, 4);

        LocalDate baslangicTarihi = SABIT_BUGUN
                .plusMonths(2)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate bitisTarihi = baslangicTarihi.plusDays(2);
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(post("/api/leave-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "izinTuruId": %d,
                                  "baslangicTarihi": "%s",
                                  "bitisTarihi": "%s",
                                  "yoneticiyeNot": null
                                }
                                """.formatted(
                                yillikIzinTuruId,
                                baslangicTarihi,
                                bitisTarihi
                        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isGunuSayisi").value(3.0));

        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yillikIzinHakki").value(20))
                .andExpect(jsonPath("$.kalanIzin").value(16));
    }

    @Test
    void yeteneklerAlfabetikSiraylaDoner() throws Exception {
        Integer javaId = yetenekOlustur("Java");
        Integer angularId = yetenekOlustur("Angular");
        Integer dockerId = yetenekOlustur("Docker");

        personelYetenekOlustur(javaId);
        personelYetenekOlustur(angularId);
        personelYetenekOlustur(dockerId);

        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yetenekler.length()").value(3))
                .andExpect(jsonPath("$.yetenekler[0].ad").value("Angular"))
                .andExpect(jsonPath("$.yetenekler[1].ad").value("Docker"))
                .andExpect(jsonPath("$.yetenekler[2].ad").value("Java"));
    }

    @Test
    void hrIcinKalanIzinHesaplanir() throws Exception {
        personelinRolunuDegistir(4, RolAdi.HR);

        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.HR);
        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("HR"))
                .andExpect(jsonPath("$.kalanIzin").value(20));
    }

    @Test
    void yarimGunOnayliIzinKalanBakiyeyiOndalikliAzaltir()
            throws Exception {

        Integer yillikIzinTuruId =
                izinTuruOlustur(
                        "Yıllık izin",
                        true
                );

        Integer onaylandiId =
                durumOlustur(
                        TalepDurum.ONAYLANDI
                );

        izinOlustur(
                yillikIzinTuruId,
                onaylandiId,
                new BigDecimal("3.5")
        );

        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(
                        get("/api/profil")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.yillikIzinHakki")
                                .value(20)
                )
                .andExpect(
                        jsonPath("$.kalanIzin")
                                .value(16.5)
                );
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"ADMIN", "YONETICI"})
    void yillikIzinKullanamayanRollerdeKalanIzinNullDoner(RolAdi rolAdi) throws Exception {

        int rolId = rolAdi == RolAdi.ADMIN ? 1 : 3;
        personelinRolunuDegistir(rolId, rolAdi);

        String token = jwtService.generateToken(personel.getPersonelId(), rolAdi);

        mockMvc.perform(get("/api/profil")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value(rolAdi.name()))
                .andExpect(jsonPath("$.kalanIzin").value(nullValue()));
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner() throws Exception {
        String token = jwtService.generateToken("veritabaninda-olmayan-id", RolAdi.PERSONEL);

        mockMvc.perform(get("/api/profil").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Personel bulunamadı."));
    }

    private Integer izinTuruOlustur(String izinTuruAdi, boolean yillikHaktanDuserMi) {
        jdbcTemplate.update(
                """
                INSERT INTO izin_turu (
                    izin_turu_adi,
                    yillik_haktan_duser_mi
                )
                VALUES (?, ?)
                """,
                izinTuruAdi,
                yillikHaktanDuserMi
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT izin_turu_id
                FROM izin_turu
                WHERE izin_turu_adi = ?
                """,
                Integer.class,
                izinTuruAdi
        );
    }

    private Integer durumOlustur(TalepDurum durum) {
        jdbcTemplate.update(
                """
                INSERT INTO durum_turu (durum_adi)
                VALUES (?)
                """,
                durum.name()
        );

        return jdbcTemplate.queryForObject(
                """
                SELECT durum_id
                FROM durum_turu
                WHERE durum_adi = ?
                """,
                Integer.class,
                durum.name()
        );
    }

    private void izinOlustur(Integer izinTuruId, Integer durumId, Number isGunuSayisi) {
        jdbcTemplate.update(
                """
                INSERT INTO izin_bilgileri (
                    izin_id,
                    personel_id,
                    izin_turu_id,
                    baslangic_tarihi,
                    bitis_tarihi,
                    is_gunu_sayisi,
                    izin_durum_id,
                    talep_tarihi
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                personel.getPersonelId(),
                izinTuruId,
                LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 7),
                isGunuSayisi,
                durumId,
                LocalDateTime.now()
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

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void kimligiDogrulanmisTumRollerKendiProfiliniGuncelleyebilir(
            RolAdi rolAdi
    ) throws Exception {
        int rolId = switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };

        personelinRolunuDegistir(rolId, rolAdi);

        String token = jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": "05551112233"
                            }
                            """))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Personel guncellenenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05551112233", guncellenenPersonel.getTelNo());
    }

    @Test
    void acikNullGonderilenAlanlarTemizlenirVeGonderilmeyenAlanlarKorunur()
            throws Exception {
        personel.setMezuniyetYil(2020);
        personel = personelRepository.saveAndFlush(personel);

        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": null,
                              "mezuniyetYili": null
                            }
                            """))
                .andExpect(status().isNoContent());

        Personel guncellenenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertNull(guncellenenPersonel.getTelNo());
        assertNull(guncellenenPersonel.getMezuniyetYil());

        assertEquals(
                "Test Üniversitesi",
                guncellenenPersonel.getMezunOkul()
        );
        assertEquals(
                "Bilgisayar Mühendisliği",
                guncellenenPersonel.getMezunBolum()
        );
    }

    @Test
    void bosMetinGonderilirse400DonerVeDegisiklikOlusturmaz()
            throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "mezunOkul": "   "
                            }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals(
                "Test Üniversitesi",
                degismeyenPersonel.getMezunOkul()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bosRequest400DonerVeAuditOlusturmaz() throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void gelecekMezuniyetYili400DonerVeDegisiklikOlusturmaz()
            throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        int gelecekYil = Year.now().getValue() + 1;

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "mezuniyetYili": %d
                            }
                            """.formatted(gelecekYil)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertNull(degismeyenPersonel.getMezuniyetYil());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void mevcutDegerlerTekrarGonderilirse204DonerVeAuditOlusturmaz()
            throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": " 05550000000 ",
                              "mezunOkul": " Test Üniversitesi "
                            }
                            """))
                .andExpect(status().isNoContent());

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05550000000", degismeyenPersonel.getTelNo());
        assertEquals(
                "Test Üniversitesi",
                degismeyenPersonel.getMezunOkul()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void gercekDegisiklikAuditKaydiOlusturur() throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": " 05551112233 ",
                              "mezunOkul": " Yeni Üniversite "
                            }
                            """))
                .andExpect(status().isNoContent());

        Personel guncellenenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05551112233", guncellenenPersonel.getTelNo());
        assertEquals(
                "Yeni Üniversite",
                guncellenenPersonel.getMezunOkul()
        );

        List<AuditLog> auditKayitlari = auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog auditLog = auditKayitlari.getFirst();

        assertEquals(
                AuditEylem.PROFIL_GUNCELLENDI,
                auditLog.getEylemTipi()
        );
        assertEquals(
                personel.getPersonelId(),
                auditLog.getPersonel().getPersonelId()
        );
        assertEquals(
                personel.getPersonelId(),
                auditLog.getHedefPersonel().getPersonelId()
        );
        assertEquals(
                "test.personel",
                auditLog.getKullaniciAdi()
        );

        assertTrue(auditLog.getDetay().contains("telNo"));
        assertTrue(auditLog.getDetay().contains("mezunOkul"));

        assertFalse(auditLog.getDetay().contains("05550000000"));
        assertFalse(auditLog.getDetay().contains("05551112233"));
        assertFalse(auditLog.getDetay().contains("Test Üniversitesi"));
        assertFalse(auditLog.getDetay().contains("Yeni Üniversite"));
    }

    @Test
    void pasifPersonelGecerliTokenlaProfiliniGuncelleyemez()
            throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": "05551112233"
                            }
                            """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05550000000", degismeyenPersonel.getTelNo());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanProfilGuncellenemez() throws Exception {
        mockMvc.perform(patch("/api/profil")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": "05551112233"
                            }
                            """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05550000000", degismeyenPersonel.getTelNo());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiPersonelBulunamazsaProfilGuncelleme404Doner()
            throws Exception {
        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "telNo": "05551112233"
                            }
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void izinVerilmeyenProfilAlaniTekBasinaGonderilirse400Doner()
            throws Exception {
        String token = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(patch("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "ad": "Değiştirilemez Ad"
                            }
                            """))
                .andExpect(status().isBadRequest());

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("Test", degismeyenPersonel.getAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHataVerirseProfilVeAuditBirlikteRollbackOlur() {
        ProfilUpdateRequest request = new ProfilUpdateRequest();
        request.setTelNo("05551112233");

        assertThrows(
                IllegalStateException.class,
                () -> profilGuncelleRollbackTestService
                        .profilGuncelleVeHataFirlat(
                                personel.getPersonelId(),
                                request
                        )
        );

        Personel degismeyenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();

        assertEquals("05550000000", degismeyenPersonel.getTelNo());
        assertEquals(0L, auditLogRepository.count());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProfilRollbackTestConfiguration {

        @Bean
        ProfilGuncelleRollbackTestService profilGuncelleRollbackTestService(
                ProfilService profilService
        ) {
            return new ProfilGuncelleRollbackTestService(profilService);
        }

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-24T09:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }

    static class ProfilGuncelleRollbackTestService {

        private final ProfilService profilService;

        ProfilGuncelleRollbackTestService(ProfilService profilService) {
            this.profilService = profilService;
        }

        @Transactional
        public void profilGuncelleVeHataFirlat(
                String personelId,
                ProfilUpdateRequest request
        ) {
            profilService.profilimiGuncelle(personelId, request);

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }
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

    private void personelinRolunuDegistir(Integer rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        rol = rolRepository.save(rol);

        personel.setRol(rol);
        personel = personelRepository.saveAndFlush(personel);
    }
}
