package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.service.JWTService;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(EkipDiziniControllerIntegrationTest.SabitSaatTestConfig.class)
class EkipDiziniControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate SABIT_BUGUN = LocalDate.of(2026, 8, 24);

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
    private TatilRepository tatilRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Rol personelRolu;
    private Departman bilgiTeknolojileri;
    private Personel aktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        jdbcTemplate.update("DELETE FROM yetenek");
        tatilRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        personelRolu = rolKaydet(2, RolAdi.PERSONEL);

        bilgiTeknolojileri = departmanKaydet("Bilgi Teknolojileri");

        aktor = personelKaydet("aktif.aktor", "Aktif", "Aktör", personelRolu, bilgiTeknolojileri, true);
    }

    @Test
    void yalnizAktifPersonellerSabitSiraylaListelenir() throws Exception {
        personelKaydet("zeynep.kaya", "Zeynep", "Kaya", personelRolu, bilgiTeknolojileri, true);
        personelKaydet("ayse.yilmaz", "Ayşe", "Yılmaz", personelRolu, bilgiTeknolojileri, true);
        personelKaydet("pasif.personel", "Ahmet", "Pasif", personelRolu, bilgiTeknolojileri, false);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(
                                aktor,
                                RolAdi.PERSONEL
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(20))
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(3))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("Aktif")
                )
                .andExpect(
                        jsonPath("$.icerik[0].soyad")
                                .value("Aktör")
                )
                .andExpect(
                        jsonPath("$.icerik[0].departman")
                                .value("Bilgi Teknolojileri")
                )
                .andExpect(
                        jsonPath("$.icerik[0].profilFoto")
                                .value("def_pfp.png")
                )
                .andExpect(
                        jsonPath("$.icerik[0].yetenekler")
                                .isEmpty()
                )
                .andExpect(
                        jsonPath("$.icerik[0].personelId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.icerik[1].ad")
                                .value("Ayşe")
                )
                .andExpect(
                        jsonPath("$.icerik[2].ad")
                                .value("Zeynep")
                )
                .andExpect(jsonPath("$.icerik[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.icerik[1].yoneticinMi").value(false))
                .andExpect(jsonPath("$.icerik[2].yoneticinMi").value(false))
                .andExpect(
                        jsonPath(
                                "$.icerik[*].ad",
                                not(hasItem("Ahmet"))
                        )
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ikinciSayfaDogruMetadataVeIcerigiDoner() throws Exception {
        personelKaydet("burak.test", "Burak", "Test", personelRolu, bilgiTeknolojileri, true);
        personelKaydet("cem.test", "Cem", "Test", personelRolu, bilgiTeknolojileri, true);
        personelKaydet("deniz.test", "Deniz", "Test", personelRolu, bilgiTeknolojileri, true);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("page", "1")
                        .param("size", "2")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(1))
                .andExpect(jsonPath("$.sayfaBoyutu").value(2))
                .andExpect(jsonPath("$.toplamKayit").value(4))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("Cem")
                )
                .andExpect(
                        jsonPath("$.icerik[1].ad")
                                .value("Deniz")
                );
    }

    @Test
    void yalnizBugunuKapsayanOnayliIzinIzinliOlarakDoner()
            throws Exception {
        Personel izinli = personelKaydet(
                "ayse.izinli",
                "Ayşe",
                "İzinli",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Personel bekleyen = personelKaydet(
                "bekleyen.izin",
                "Bekleyen",
                "İzin",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Personel baskaGun = personelKaydet(
                "baska.gun",
                "Başka",
                "Gün",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Integer izinTuruId = izinTuruOlustur();
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);
        Integer onayBekliyorId = durumOlustur(TalepDurum.ONAY_BEKLIYOR);

        izinOlustur(
                izinli,
                izinTuruId,
                onaylandiId,
                SABIT_BUGUN.minusDays(1),
                SABIT_BUGUN.plusDays(1)
        );
        izinOlustur(
                bekleyen,
                izinTuruId,
                onayBekliyorId,
                SABIT_BUGUN,
                SABIT_BUGUN
        );
        izinOlustur(
                baskaGun,
                izinTuruId,
                onaylandiId,
                SABIT_BUGUN.plusDays(1),
                SABIT_BUGUN.plusDays(1)
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "Ayşe")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].bugunIzinDurumu").value("IZINLI"));

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "Bekleyen")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].bugunIzinDurumu").value(nullValue()));

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "Başka")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].bugunIzinDurumu").value(nullValue()));
    }

    @Test
    void yarimGunTatildeOnayliIzinYarimGunIzinliDoner()
            throws Exception {
        Integer izinTuruId = izinTuruOlustur();
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);
        izinOlustur(
                aktor,
                izinTuruId,
                onaylandiId,
                SABIT_BUGUN,
                SABIT_BUGUN
        );
        tatilKaydet(TatilGunSuresi.YARIM_GUN);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "Aktif")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.icerik[0].bugunIzinDurumu")
                                .value("YARIM_GUN_IZINLI")
                );
    }

    @Test
    void tamGunTatildeOnayliIzinEtiketiDonmez() throws Exception {
        Integer izinTuruId = izinTuruOlustur();
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);
        izinOlustur(
                aktor,
                izinTuruId,
                onaylandiId,
                SABIT_BUGUN,
                SABIT_BUGUN
        );
        tatilKaydet(TatilGunSuresi.TAM_GUN);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "Aktif")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].bugunIzinDurumu").value(nullValue()));
    }

    @Test
    void tokenOlmadanEkipDiziniGoruntulenemez() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner() throws Exception {
        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );
    }

    @Test
    void pasifPrincipalGecerliTokenlaEkipDizininiGoremez() throws Exception {
        String token = token(aktor, RolAdi.PERSONEL);

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void tumRollerEkipDizininiGoruntuleyebilir(RolAdi rolAdi) throws Exception {
        Rol rol = switch (rolAdi) {
            case ADMIN -> rolKaydet(1, RolAdi.ADMIN);
            case PERSONEL -> personelRolu;
            case YONETICI -> rolKaydet(3, RolAdi.YONETICI);
            case HR -> rolKaydet(4, RolAdi.HR);
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        rolAdi
                                )
                        ))
                .andExpect(status().isOk());
    }

    @Test
    void personelYalnizKendiDepartmanindakiAktifKisileriVeYoneticisiniGorur()
            throws Exception {
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");

        Personel yonetici = personelKaydet(
                "bt.yonetici",
                "Burcu",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );
        aktor.setYonetici(yonetici);
        aktor = personelRepository.saveAndFlush(aktor);

        personelKaydet(
                "bt.personel",
                "Cem",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "ik.personel",
                "Deniz",
                "Başka Departman",
                personelRolu,
                insanKaynaklari,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.icerik[*].ad", hasItem("Aktif")))
                .andExpect(jsonPath("$.icerik[*].ad", hasItem("Burcu")))
                .andExpect(jsonPath("$.icerik[*].ad", hasItem("Cem")))
                .andExpect(jsonPath("$.icerik[*].ad", not(hasItem("Deniz"))))
                .andExpect(jsonPath("$.icerik[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.icerik[1].yoneticinMi").value(true))
                .andExpect(jsonPath("$.icerik[2].yoneticinMi").value(false));
    }

    @Test
    void hrYalnizKendiAtanmisYoneticisiniYoneticinOlarakGorur()
            throws Exception {
        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);

        Personel kendiYoneticisi = personelKaydet(
                "burcu.yonetici",
                "Burcu",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "cem.yonetici",
                "Cem",
                "Başka Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        aktor.setRol(hrRolu);
        aktor.setYonetici(kendiYoneticisi);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, RolAdi.HR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].ad").value("Aktif"))
                .andExpect(jsonPath("$.icerik[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.icerik[1].ad").value("Burcu"))
                .andExpect(jsonPath("$.icerik[1].yoneticinMi").value(true))
                .andExpect(jsonPath("$.icerik[2].ad").value("Cem"))
                .andExpect(jsonPath("$.icerik[2].yoneticinMi").value(false));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"YONETICI", "HR"})
    void yoneticiVeHrAdminHaricButunAktifKisileriGorur(RolAdi aktorRolu)
            throws Exception {
        Rol aktorRolKaydi = switch (aktorRolu) {
            case YONETICI -> rolKaydet(3, RolAdi.YONETICI);
            case HR -> rolKaydet(4, RolAdi.HR);
            default -> throw new IllegalArgumentException("Desteklenmeyen rol");
        };
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");

        aktor.setRol(aktorRolKaydi);
        aktor = personelRepository.saveAndFlush(aktor);
        personelKaydet("ik.personel", "Deniz", "Personel", personelRolu, insanKaynaklari, true);
        personelKaydet("admin", "Admin", "Kullanıcı", adminRolu, insanKaynaklari, true);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, aktorRolu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik[*].ad", hasItem("Aktif")))
                .andExpect(jsonPath("$.icerik[*].ad", hasItem("Deniz")))
                .andExpect(jsonPath("$.icerik[*].ad", not(hasItem("Admin"))));
    }

    @Test
    void adminButunAktifKisileriGorur() throws Exception {
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");

        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);
        personelKaydet("ik.personel", "Deniz", "Personel", personelRolu, insanKaynaklari, true);
        personelKaydet("diger.admin", "Başka", "Admin", adminRolu, insanKaynaklari, true);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(aktor, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.icerik.length()").value(3));
    }

    @Test
    void departmanFiltresiYalnizEslesenAktifPersonelleriDoner()
            throws Exception {
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Departman insanKaynaklari =
                departmanKaydet("İnsan Kaynakları");

        personelKaydet(
                "ik.personel",
                "İpek",
                "Kaya",
                personelRolu,
                insanKaynaklari,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param(
                                "departmanId",
                                insanKaynaklari
                                        .getDepartmanId()
                                        .toString()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("İpek")
                )
                .andExpect(
                        jsonPath("$.icerik[0].departman")
                                .value("İnsan Kaynakları")
                );
    }

    @Test
    void departmanVeYetenekFiltreleriBirlikteAndUygular()
            throws Exception {
        Rol adminRolu = rolKaydet(1, RolAdi.ADMIN);
        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Departman insanKaynaklari =
                departmanKaydet("İnsan Kaynakları");

        Integer javaId = yetenekOlustur("Java");

        Personel btJava = personelKaydet(
                "bt.java",
                "Bilge",
                "Tekin",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        Personel ikJava = personelKaydet(
                "ik.java",
                "İpek",
                "Kaya",
                personelRolu,
                insanKaynaklari,
                true
        );

        personelYetenekOlustur(btJava, javaId);
        personelYetenekOlustur(ikJava, javaId);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param(
                                "departmanId",
                                insanKaynaklari
                                        .getDepartmanId()
                                        .toString()
                        )
                        .param(
                                "yetenekId",
                                javaId.toString()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("İpek")
                )
                .andExpect(
                        jsonPath("$.icerik[0].departman")
                                .value("İnsan Kaynakları")
                );
    }

    @Test
    void yetenekFiltresiEslesenPersonelinButunYetenekleriniDoner()
            throws Exception {
        Integer dockerId = yetenekOlustur("Docker");
        Integer javaId = yetenekOlustur("Java");

        personelYetenekOlustur(aktor, javaId);
        personelYetenekOlustur(aktor, dockerId);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param(
                                "yetenekId",
                                javaId.toString()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath(
                                "$.icerik[0].yetenekler.length()"
                        ).value(2)
                )
                .andExpect(
                        jsonPath(
                                "$.icerik[0].yetenekler[0].ad"
                        ).value("Docker")
                )
                .andExpect(
                        jsonPath(
                                "$.icerik[0].yetenekler[1].ad"
                        ).value("Java")
                );
    }

    @Test
    void birdenFazlaYetenekPersoneliCogaltmazVeToplamKaydiBozmaz()
            throws Exception {
        Integer dockerId = yetenekOlustur("Docker");
        Integer javaId = yetenekOlustur("Java");

        personelYetenekOlustur(aktor, dockerId);
        personelYetenekOlustur(aktor, javaId);

        mockMvc.perform(get("/api/ekip-dizini")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath(
                                "$.icerik[0].yetenekler.length()"
                        ).value(2)
                );
    }

    @Test
    void yetenegiOlmayanPersonelinYetenekleriBosDoner()
            throws Exception {
        mockMvc.perform(get("/api/ekip-dizini")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.icerik[0].yetenekler")
                                .isEmpty()
                );
    }

    @ParameterizedTest
    @CsvSource({
            "departmanId, 999999",
            "yetenekId, 999999"
    })
    void bulunmayanPozitifFiltreIdsiBosSayfaDoner(
            String parametre,
            String deger
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini")
                        .param(parametre, deger)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik").isEmpty())
                .andExpect(jsonPath("$.toplamKayit").value(0))
                .andExpect(jsonPath("$.toplamSayfa").value(0));
    }

    @ParameterizedTest
    @CsvSource({
            "departmanId, 0",
            "departmanId, -1",
            "yetenekId, 0",
            "yetenekId, -1"
    })
    void pozitifOlmayanFiltreIdsi400Doner(
            String parametre,
            String deger
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini")
                        .param(parametre, deger)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void personelFarkliDepartmanFiltresiyleKendiDepartmanSiniriniAsamaz()
            throws Exception {
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");
        personelKaydet(
                "ik.kapsam.personel",
                "İpek",
                "Kapsam",
                personelRolu,
                insanKaynaklari,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param(
                                "departmanId",
                                insanKaynaklari.getDepartmanId().toString()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik").isEmpty())
                .andExpect(jsonPath("$.toplamKayit").value(0))
                .andExpect(jsonPath("$.toplamSayfa").value(0));
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101"
    })
    void gecersizPagination400Doner(
            int page,
            int size
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini")
                        .param(
                                "page",
                                String.valueOf(page)
                        )
                        .param(
                                "size",
                                String.valueOf(size)
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @CsvSource({
            "'  AYŞE  ', Ayşe",
            "yılmaz, Ayşe",
            "'AYŞE yılMAZ', Ayşe"
    })
    void aramaAdSoyadVeTamAdUzerindeCaseInsensitiveCalisir(
            String arama,
            String beklenenAd
    ) throws Exception {
        personelKaydet(
                "ayse.yilmaz",
                "Ayşe",
                "Yılmaz",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "deniz.kaya",
                "Deniz",
                "Kaya",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", arama)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value(beklenenAd)
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void bosAramaFiltreUygulanmamisGibiDavranir(
            String arama
    ) throws Exception {
        personelKaydet(
                "ayse.yilmaz",
                "Ayşe",
                "Yılmaz",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", arama)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(2));
    }

    @Test
    void aramaDepartmanVeYetenekFiltreleriAndIleBirlesir() throws Exception {
        Departman insanKaynaklari =
                departmanKaydet("İnsan Kaynakları");
        Integer javaId = yetenekOlustur("Java");

        Personel eslesen = personelKaydet(
                "ayse.java",
                "Ayşe",
                "Java",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Personel yanlisDepartman = personelKaydet(
                "ayse.ik",
                "Ayşe",
                "İK",
                personelRolu,
                insanKaynaklari,
                true
        );

        Personel aramaylaUyusmayan = personelKaydet(
                "deniz.java",
                "Deniz",
                "Java",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        personelKaydet(
                "ayse.yeteneksiz",
                "Ayşe",
                "Yeteneksiz",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        personelYetenekOlustur(eslesen, javaId);
        personelYetenekOlustur(yanlisDepartman, javaId);
        personelYetenekOlustur(aramaylaUyusmayan, javaId);

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "  AYŞE  ")
                        .param(
                                "departmanId",
                                bilgiTeknolojileri
                                        .getDepartmanId()
                                        .toString()
                        )
                        .param("yetenekId", javaId.toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("Ayşe")
                )
                .andExpect(
                        jsonPath("$.icerik[0].soyad")
                                .value("Java")
                );
    }

    @Test
    void aramaCountSorgusuVePaginationIleTutarlidir()
            throws Exception {
        personelKaydet(
                "ayse.a",
                "Ayşe",
                "A",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "ayse.b",
                "Ayşe",
                "B",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "deniz.c",
                "Deniz",
                "C",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "ayşe")
                        .param("page", "1")
                        .param("size", "1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(1))
                .andExpect(jsonPath("$.sayfaBoyutu").value(1))
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].soyad")
                                .value("B")
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page",
            "size",
            "departmanId",
            "yetenekId"
    })
    void sayisalOlmasiGerekenParametreMetinse400Doner(
            String parametre
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini")
                        .param(parametre, "abc")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);

        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);

        return departmanRepository.save(departman);
    }

    private Personel personelKaydet(String kullaniciAdi, String ad, String soyad, Rol rol, Departman departman, boolean aktif) {
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
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");

        return personelRepository.saveAndFlush(personel);
    }

    private String token(Personel personel, RolAdi rolAdi) {
        return jwtService.generateToken(personel.getPersonelId(), rolAdi);
    }

    private Integer izinTuruOlustur() {
        jdbcTemplate.update(
                """
                INSERT INTO izin_turu (
                    izin_turu_adi,
                    yillik_haktan_duser_mi
                )
                VALUES ('Yıllık izin', true)
                """
        );

        return jdbcTemplate.queryForObject(
                "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = 'Yıllık izin'",
                Integer.class
        );
    }

    private Integer durumOlustur(TalepDurum durum) {
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                durum.name()
        );

        return jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                durum.name()
        );
    }

    private void izinOlustur(
            Personel personel,
            Integer izinTuruId,
            Integer durumId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
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
                baslangicTarihi,
                bitisTarihi,
                BigDecimal.ONE,
                durumId,
                LocalDateTime.of(2026, 8, 1, 9, 0)
        );
    }

    private void tatilKaydet(TatilGunSuresi gunSuresi) {
        Tatil tatil = new Tatil();
        tatil.setAd("Bugünkü test tatili");
        tatil.setTarih(SABIT_BUGUN);
        tatil.setBitisTarihi(SABIT_BUGUN);
        tatil.setKategori(TatilKategori.SIRKET);
        tatil.setGunSuresi(gunSuresi);
        tatilRepository.saveAndFlush(tatil);
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

    private void personelYetenekOlustur(
            Personel personel,
            Integer yetenekId
    ) {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class SabitSaatTestConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-24T09:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }
}
