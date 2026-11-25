package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.YetenekRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(YoneticiHiyerarsisiControllerIntegrationTest.SabitSaatTestConfig.class)
class YoneticiHiyerarsisiControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate SABIT_BUGUN = LocalDate.of(2026, 8, 25);

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
    private YetenekRepository yetenekRepository;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Departman departman;
    private Rol adminRolu;
    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;
    private Personel hrAktor;
    private int siradakiSicilNumarasi;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM tatil");
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");
        personelYetenekRepository.deleteAll();
        yetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        adminRolu = rolKaydet(1, RolAdi.ADMIN);
        personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        hrRolu = rolKaydet(4, RolAdi.HR);

        departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.saveAndFlush(departman);

        siradakiSicilNumarasi = 7000;
        hrAktor = personelKaydet(
                "zeynep.hr",
                "Zeynep",
                "HR",
                hrRolu,
                true,
                null
        );
    }

    @Test
    void hrTumAktifOrganizasyonForestiniGuvenliAlanlarlaGorur() throws Exception {
        Personel kokYonetici = personelKaydet(
                "ada.lider",
                "Ada",
                "Lider",
                yoneticiRolu,
                true,
                null
        );
        Personel altYonetici = personelKaydet(
                "bora.yonetici",
                "Bora",
                "Yönetici",
                yoneticiRolu,
                true,
                kokYonetici
        );
        personelKaydet(
                "cem.personel",
                "Cem",
                "Personel",
                personelRolu,
                true,
                altYonetici
        );
        personelKaydet(
                "derya.admin",
                "Derya",
                "Admin",
                adminRolu,
                true,
                null
        );
        personelKaydet(
                "pasif.personel",
                "Pasif",
                "Personel",
                personelRolu,
                false,
                altYonetici
        );
        Personel hrYoneticisi = personelKaydet(
                "yasemin.hr.yoneticisi",
                "Yasemin",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        hrAktor.setYonetici(hrYoneticisi);
        hrAktor = personelRepository.saveAndFlush(hrAktor);

        String token = jwtService.generateToken(hrAktor.getPersonelId(), RolAdi.HR);

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(3))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].soyad").value("Lider"))
                .andExpect(jsonPath("$.kokler[0].departman").value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$.kokler[0].rol").value("YONETICI"))
                .andExpect(jsonPath("$.kokler[0].profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Cem"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].yoneticinMi")
                        .value(false))
                .andExpect(jsonPath("$.kokler[1].ad").value("Derya"))
                .andExpect(jsonPath("$.kokler[1].rol").value("ADMIN"))
                .andExpect(jsonPath("$.kokler[1].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[2].ad").value("Yasemin"))
                .andExpect(jsonPath("$.kokler[2].yoneticinMi").value(true))
                .andExpect(jsonPath("$.kokler[2].dogrudanBaglilar[0].ad").value("Zeynep"))
                .andExpect(jsonPath("$.kokler[2].dogrudanBaglilar[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$..personelId").doesNotExist())
                .andExpect(jsonPath("$..yoneticiId").doesNotExist())
                .andExpect(jsonPath("$..kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$..ePosta").doesNotExist())
                .andExpect(jsonPath("$..parolaHash").doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrHiyerarsisindeBugunkuIzinDurumuHerDugumdeDoner() throws Exception {
        Personel kokYonetici = personelKaydet(
                "izin.kok.yonetici",
                "Ada",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel izinliPersonel = personelKaydet(
                "izinli.hiyerarsi.personel",
                "Bora",
                "İzinli",
                personelRolu,
                true,
                kokYonetici
        );
        hrAktor.setYonetici(kokYonetici);
        personelRepository.saveAndFlush(hrAktor);
        onayliIzinKaydet(izinliPersonel, SABIT_BUGUN, SABIT_BUGUN);

        String token = jwtService.generateToken(
                hrAktor.getPersonelId(),
                RolAdi.HR
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].bugunIzinDurumu").value(nullValue()))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].bugunIzinDurumu")
                        .value("IZINLI"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[1].ad").value("Zeynep"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[1].bugunIzinDurumu")
                        .value(nullValue()));
    }

    @Test
    void adminHiyerarsisindeYarimGunIzinDurumuDoner() throws Exception {
        Personel adminAktor = personelKaydet(
                "izin.hiyerarsi.admin",
                "AAdmin",
                "Aktör",
                adminRolu,
                true,
                null
        );
        Personel izinliPersonel = personelKaydet(
                "yarim.gun.hiyerarsi.personel",
                "Bora",
                "İzinli",
                personelRolu,
                true,
                null
        );
        onayliIzinKaydet(izinliPersonel, SABIT_BUGUN, SABIT_BUGUN);
        yarimGunTatilKaydet(SABIT_BUGUN);

        String token = jwtService.generateToken(
                adminAktor.getPersonelId(),
                RolAdi.ADMIN
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler[1].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[1].bugunIzinDurumu")
                        .value("YARIM_GUN_IZINLI"));
    }

    @Test
    void yoneticisiOlmayanAdminTumDugumlerdeYoneticinMiFalseGorur() throws Exception {
        Personel adminAktor = personelKaydet(
                "hiyerarsi.admin",
                "Aktif",
                "Admin",
                adminRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        adminAktor.getPersonelId(),
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(2))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[1].yoneticinMi").value(false));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yoneticiHiyerarsisindeUstAktorVeAstlarBugunkuIzinDurumunuTasır()
            throws Exception {
        Personel ustYonetici = personelKaydet(
                "izin.durumlu.ust",
                "Ada",
                "Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel yoneticiAktor = personelKaydet(
                "izin.durumlu.aktor",
                "Bora",
                "Aktör",
                yoneticiRolu,
                true,
                ustYonetici
        );
        Personel izinliAst = personelKaydet(
                "izin.durumlu.ast",
                "Cem",
                "Ast",
                personelRolu,
                true,
                yoneticiAktor
        );
        onayliIzinKaydet(ustYonetici, SABIT_BUGUN, SABIT_BUGUN);
        onayliIzinKaydet(izinliAst, SABIT_BUGUN, SABIT_BUGUN);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].bugunIzinDurumu")
                        .value("IZINLI"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad")
                        .value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].bugunIzinDurumu")
                        .value(nullValue()))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Cem"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].bugunIzinDurumu")
                        .value("IZINLI"));
    }

    @Test
    void hrAramaFiltresindeYalnizEslesenKisiVeKokeKadarZinciriKalir()
            throws Exception {
        Personel kokYonetici = personelKaydet(
                "filtre.kok",
                "Ada",
                "Kök",
                yoneticiRolu,
                true,
                null
        );
        Personel araYonetici = personelKaydet(
                "filtre.ara",
                "Bora",
                "Ara",
                yoneticiRolu,
                true,
                kokYonetici
        );
        personelKaydet(
                "filtre.hedef",
                "Hedef",
                "Personel",
                personelRolu,
                true,
                araYonetici
        );
        personelKaydet(
                "filtre.kardes",
                "İlgisiz",
                "Kardeş",
                personelRolu,
                true,
                araYonetici
        );
        personelKaydet(
                "filtre.ayri.kok",
                "Zara",
                "Ayrı",
                yoneticiRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param("arama", "  hEdEf  ")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Hedef"));
    }

    @Test
    void hrAramaDepartmanVeYetenekFiltreleriniAndUygular()
            throws Exception {
        Personel kokYonetici = personelKaydet(
                "and.kok",
                "Kök",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel eslesen = personelKaydet(
                "and.eslesen",
                "Ayşe",
                "Java",
                personelRolu,
                true,
                kokYonetici
        );
        personelKaydet(
                "and.yeteneksiz",
                "Ayşe",
                "Yeteneksiz",
                personelRolu,
                true,
                kokYonetici
        );
        Personel aramaUyusmayan = personelKaydet(
                "and.arama.uyusmayan",
                "Deniz",
                "Java",
                personelRolu,
                true,
                kokYonetici
        );
        Departman digerDepartman = new Departman();
        digerDepartman.setDepartmanAdi("İnsan Kaynakları");
        digerDepartman = departmanRepository.saveAndFlush(digerDepartman);
        Personel yanlisDepartman = personelKaydet(
                "and.yanlis.departman",
                "Ayşe",
                "İK",
                personelRolu,
                true,
                null
        );
        yanlisDepartman.setDepartman(digerDepartman);
        yanlisDepartman = personelRepository.saveAndFlush(yanlisDepartman);

        Yetenek java = yetenekKaydet("Java");
        yetenekAta(eslesen, java);
        yetenekAta(aramaUyusmayan, java);
        yetenekAta(yanlisDepartman, java);

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param("arama", "  aYŞe  ")
                        .param("departmanId", departman.getDepartmanId().toString())
                        .param("yetenekId", java.getYetenekId().toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Kök"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Ayşe"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].soyad").value("Java"));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bosAramaFiltresizTamForestiKorur() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param("arama", "   ")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Zeynep"));
    }

    @ParameterizedTest
    @CsvSource({"departmanId, 999999", "yetenekId, 999999"})
    void bulunmayanPozitifHiyerarsiFiltreIdsiBosForestDoner(
            String parametre,
            String deger
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param(parametre, deger)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "departmanId, 0",
            "departmanId, -1",
            "yetenekId, 0",
            "yetenekId, -1",
            "departmanId, abc",
            "yetenekId, abc"
    })
    void gecersizHiyerarsiFiltreIdsi400Doner(
            String parametre,
            String deger
    ) throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param(parametre, deger)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void gercekYoneticiFiltreyleBudanirsaTumYoneticinMiDegerleriFalseOlur()
            throws Exception {
        Personel hrYoneticisi = personelKaydet(
                "budanan.hr.yoneticisi",
                "Budanan",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        hrAktor.setYonetici(hrYoneticisi);
        hrAktor = personelRepository.saveAndFlush(hrAktor);
        personelKaydet(
                "budama.hedef",
                "Hedef",
                "Kök",
                personelRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .param("arama", "Hedef")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Hedef"))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(false));
    }

    @Test
    void yoneticiEnFazlaIkiUstunuKendisiniVeYalnizKendiAktifAstAgaciniGorur()
            throws Exception {
        Personel ikinciUst = personelKaydet(
                "yonetici.hiyerarsi.ikinci.ust",
                "Ada",
                "İkinci Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel dogrudanUst = personelKaydet(
                "yonetici.hiyerarsi.dogrudan.ust",
                "Bora",
                "Doğrudan Üst",
                yoneticiRolu,
                true,
                ikinciUst
        );
        Personel yoneticiAktor = personelKaydet(
                "yonetici.hiyerarsi.aktor",
                "Cem",
                "Aktör",
                yoneticiRolu,
                true,
                dogrudanUst
        );
        Personel dogrudanAst = personelKaydet(
                "yonetici.hiyerarsi.dogrudan.ast",
                "Deniz",
                "Doğrudan Ast",
                yoneticiRolu,
                true,
                yoneticiAktor
        );
        personelKaydet(
                "yonetici.hiyerarsi.dolayli.ast",
                "Ece",
                "Dolaylı Ast",
                personelRolu,
                true,
                dogrudanAst
        );
        Personel aktorKardesi = personelKaydet(
                "yonetici.hiyerarsi.kardes",
                "Fırat",
                "Kardeş",
                yoneticiRolu,
                true,
                dogrudanUst
        );
        personelKaydet(
                "yonetici.hiyerarsi.kardes.asti",
                "Gül",
                "Başka Ekip",
                personelRolu,
                true,
                aktorKardesi
        );
        personelKaydet(
                "yonetici.hiyerarsi.baska.kok",
                "Zara",
                "Başka Kök",
                yoneticiRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktorDepartman.id")
                        .value(departman.getDepartmanId()))
                .andExpect(jsonPath("$.aktorDepartman.ad")
                        .value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].yoneticinMi")
                        .value(true))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Cem"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Deniz"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Ece"))
                .andExpect(jsonPath("$..personelId").doesNotExist())
                .andExpect(jsonPath("$..yoneticiId").doesNotExist())
                .andExpect(jsonPath("$..kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$..ePosta").doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "ADMIN"})
    void personelHrVeAdminYoneticiHiyerarsisiniGoremez(RolAdi aktorRolu)
            throws Exception {
        Personel aktor = aktorRolu == RolAdi.HR
                ? hrAktor
                : personelKaydet(
                        "yonetici.hiyerarsi.yetkisiz." + aktorRolu.name().toLowerCase(),
                        "Yetkisiz",
                        aktorRolu.name(),
                        rolGetir(aktorRolu),
                        true,
                        null
                );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        aktorRolu
                                )
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void pasifYoneticiYoneticiHiyerarsisiniGoremez() throws Exception {
        Personel pasifYonetici = personelKaydet(
                "yonetici.hiyerarsi.pasif",
                "Pasif",
                "Yönetici",
                yoneticiRolu,
                false,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        pasifYonetici.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokendakiYoneticiBulunamazsaYoneticiHiyerarsisi404Doner()
            throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        "olmayan-yonetici-id",
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));
    }

    @Test
    void tokenOlmadanYoneticiHiyerarsisi401Doner() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokendaYoneticiAmaVeritabanindaHrIse403Doner() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        hrAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void ucuncuUstVePasifAstDislanirUstuOlmayanYoneticiKokOlur()
            throws Exception {
        Personel ucuncuUst = personelKaydet(
                "yonetici.hiyerarsi.ucuncu.ust",
                "A",
                "Üçüncü Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel ikinciUst = personelKaydet(
                "yonetici.hiyerarsi.ikinci.ust.sinir",
                "B",
                "İkinci Üst",
                yoneticiRolu,
                true,
                ucuncuUst
        );
        Personel birinciUst = personelKaydet(
                "yonetici.hiyerarsi.birinci.ust.sinir",
                "C",
                "Birinci Üst",
                yoneticiRolu,
                true,
                ikinciUst
        );
        Personel yoneticiAktor = personelKaydet(
                "yonetici.hiyerarsi.aktor.sinir",
                "D",
                "Aktör",
                yoneticiRolu,
                true,
                birinciUst
        );
        personelKaydet(
                "yonetici.hiyerarsi.aktif.ast",
                "E",
                "Aktif Ast",
                personelRolu,
                true,
                yoneticiAktor
        );
        personelKaydet(
                "yonetici.hiyerarsi.pasif.ast",
                "F",
                "Pasif Ast",
                personelRolu,
                false,
                yoneticiAktor
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("B"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("C"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("D"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("E"));

        Personel kokYonetici = personelKaydet(
                "yonetici.hiyerarsi.ustsuz",
                "Kök",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        personelKaydet(
                "yonetici.hiyerarsi.ustsuz.asti",
                "Kök",
                "Astı",
                personelRolu,
                true,
                kokYonetici
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        kokYonetici.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Kök"))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()")
                        .value(1));
    }

    @Test
    void yoneticiFiltreliGorunumdeUstZincirAktorVeYalnizEslesenAstBaglantisiniGorur()
            throws Exception {
        Personel ikinciUst = personelKaydet(
                "yonetici.filtre.ikinci.ust",
                "Ada",
                "İkinci Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel dogrudanUst = personelKaydet(
                "yonetici.filtre.dogrudan.ust",
                "Bora",
                "Doğrudan Üst",
                yoneticiRolu,
                true,
                ikinciUst
        );
        Personel yoneticiAktor = personelKaydet(
                "yonetici.filtre.aktor",
                "Cem",
                "Aktör",
                yoneticiRolu,
                true,
                dogrudanUst
        );
        Personel araYonetici = personelKaydet(
                "yonetici.filtre.ara",
                "Deniz",
                "Ara Yönetici",
                yoneticiRolu,
                true,
                yoneticiAktor
        );
        Personel eslesenAst = personelKaydet(
                "yonetici.filtre.eslesen",
                "Hedef",
                "Personel",
                personelRolu,
                true,
                araYonetici
        );
        Personel eslesmeyenKardes = personelKaydet(
                "yonetici.filtre.eslesmeyen",
                "İlgisiz",
                "Kardeş Ast",
                personelRolu,
                true,
                yoneticiAktor
        );
        Personel baskaEkipYoneticisi = personelKaydet(
                "yonetici.filtre.baska.ekip",
                "Zara",
                "Başka Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel baskaEkipEslesmesi = personelKaydet(
                "yonetici.filtre.baska.eslesme",
                "Hedef",
                "Başka Ekip",
                personelRolu,
                true,
                baskaEkipYoneticisi
        );
        Yetenek java = yetenekKaydet("Java");
        yetenekAta(eslesenAst, java);
        yetenekAta(eslesmeyenKardes, java);
        yetenekAta(baskaEkipEslesmesi, java);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .param("arama", "  hEdEf  ")
                        .param("departmanId", departman.getDepartmanId().toString())
                        .param("yetenekId", java.getYetenekId().toString())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad").value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Cem"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Deniz"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].dogrudanBaglilar[0].ad")
                        .value("Hedef"));
    }

    @Test
    void yoneticiFiltreEslesmesiYoksaUstZincirVeAktorKalirAstlarBudanir()
            throws Exception {
        Personel ust = personelKaydet(
                "yonetici.filtre.bos.ust",
                "Ada",
                "Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel yoneticiAktor = personelKaydet(
                "yonetici.filtre.bos.aktor",
                "Bora",
                "Aktör",
                yoneticiRolu,
                true,
                ust
        );
        personelKaydet(
                "yonetici.filtre.bos.ast",
                "Cem",
                "Ast",
                personelRolu,
                true,
                yoneticiAktor
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .param("yetenekId", "999999")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Ada"))
                .andExpect(jsonPath("$.kokler[0].yoneticinMi").value(true))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()")
                        .value(1))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].ad")
                        .value("Bora"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar[0].dogrudanBaglilar")
                        .isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
            "departmanId, 0",
            "departmanId, -1",
            "yetenekId, 0",
            "yetenekId, -1",
            "departmanId, abc",
            "yetenekId, abc"
    })
    void gecersizYoneticiHiyerarsisiFiltreIdsi400Doner(
            String parametre,
            String deger
    ) throws Exception {
        Personel yoneticiAktor = personelKaydet(
                "yonetici.filtre.gecersiz." + parametre + deger,
                "Geçersiz",
                "Filtre",
                yoneticiRolu,
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .param(parametre, deger)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void yoneticiKapsamindakiLegacyDongu409Doner() throws Exception {
        Personel ust = personelKaydet(
                "yonetici.hiyerarsi.dongu.ust",
                "Döngü",
                "Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel yoneticiAktor = personelKaydet(
                "yonetici.hiyerarsi.dongu.aktor",
                "Döngü",
                "Aktör",
                yoneticiRolu,
                true,
                ust
        );
        ust.setYonetici(yoneticiAktor);
        personelRepository.saveAndFlush(ust);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Yönetici hiyerarşisinde tutarsızlık tespit edildi."
                ));
    }

    @Test
    void ilgisizDaldakiLegacyDonguYoneticiGuvenliAgaciniEngellemez()
            throws Exception {
        Personel yoneticiAktor = personelKaydet(
                "yonetici.hiyerarsi.ilgisiz.dongu.aktor",
                "Güvenli",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        personelKaydet(
                "yonetici.hiyerarsi.ilgisiz.dongu.ast",
                "Güvenli",
                "Ast",
                personelRolu,
                true,
                yoneticiAktor
        );
        Personel donguBir = personelKaydet(
                "yonetici.hiyerarsi.ilgisiz.dongu.bir",
                "İlgisiz",
                "Bir",
                yoneticiRolu,
                true,
                null
        );
        Personel donguIki = personelKaydet(
                "yonetici.hiyerarsi.ilgisiz.dongu.iki",
                "İlgisiz",
                "İki",
                yoneticiRolu,
                true,
                donguBir
        );
        donguBir.setYonetici(donguIki);
        personelRepository.saveAndFlush(donguBir);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-hiyerarsim")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        yoneticiAktor.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kokler.length()").value(1))
                .andExpect(jsonPath("$.kokler[0].ad").value("Güvenli"))
                .andExpect(jsonPath("$.kokler[0].dogrudanBaglilar.length()")
                        .value(1));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI"})
    void personelVeYoneticiTumHiyerarsiyiGoremez(RolAdi aktorRolu) throws Exception {
        Personel aktor = personelKaydet(
                "hiyerarsi." + aktorRolu.name().toLowerCase(),
                "Yetkisiz",
                aktorRolu.name(),
                rolGetir(aktorRolu),
                true,
                null
        );

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        aktorRolu
                                )
                        ))
                .andExpect(status().isForbidden());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"HR", "ADMIN"})
    void pasifHrVeAdminTumHiyerarsiyiGoremez(RolAdi aktorRolu) throws Exception {
        Personel aktor = aktorRolu == RolAdi.HR
                ? hrAktor
                : personelKaydet(
                        "pasif.hiyerarsi.admin",
                        "Pasif",
                        "Admin",
                        adminRolu,
                        true,
                        null
                );
        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        aktorRolu
                                )
                        ))
                .andExpect(status().isForbidden());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaTumHiyerarsi404Doner() throws Exception {
        String token = jwtService.generateToken("olmayan-aktor-id", RolAdi.HR);

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanTumHiyerarsi401Doner() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi"))
                .andExpect(status().isUnauthorized());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void personelYalnizIlkIkiYoneticisiniYakinlikSirasiylaGorur() throws Exception {
        Personel ucuncuUst = personelKaydet(
                "ucuncu.ust",
                "Üçüncü",
                "Üst",
                yoneticiRolu,
                true,
                null
        );
        Personel ikinciUst = personelKaydet(
                "ikinci.ust",
                "İkinci",
                "Üst",
                yoneticiRolu,
                true,
                ucuncuUst
        );
        Personel birinciUst = personelKaydet(
                "birinci.ust",
                "Birinci",
                "Üst",
                yoneticiRolu,
                true,
                ikinciUst
        );
        Personel personelAktor = personelKaydet(
                "zincir.personel",
                "Zincir",
                "Personel",
                personelRolu,
                true,
                birinciUst
        );

        String token = jwtService.generateToken(
                personelAktor.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoneticiler.length()").value(2))
                .andExpect(jsonPath("$.yoneticiler[0].seviye").value(1))
                .andExpect(jsonPath("$.yoneticiler[0].ad").value("Birinci"))
                .andExpect(jsonPath("$.yoneticiler[0].departman").value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$.yoneticiler[0].rol").value("YONETICI"))
                .andExpect(jsonPath("$.yoneticiler[0].profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.yoneticiler[1].seviye").value(2))
                .andExpect(jsonPath("$.yoneticiler[1].ad").value("İkinci"))
                .andExpect(jsonPath("$..personelId").doesNotExist())
                .andExpect(jsonPath("$..kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$..ePosta").doesNotExist())
                .andExpect(jsonPath("$..parolaHash").doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yoneticiKendiDogrudanYoneticisiniGorur() throws Exception {
        Personel ustYonetici = personelKaydet(
                "yonetici.ustu",
                "Üst",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel yoneticiAktor = personelKaydet(
                "zincir.yonetici",
                "Zincir",
                "Yönetici",
                yoneticiRolu,
                true,
                ustYonetici
        );

        String token = jwtService.generateToken(
                yoneticiAktor.getPersonelId(),
                RolAdi.YONETICI
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoneticiler.length()").value(1))
                .andExpect(jsonPath("$.yoneticiler[0].seviye").value(1))
                .andExpect(jsonPath("$.yoneticiler[0].ad").value("Üst"));

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void yoneticiYoksaVeyaPasifseZincirBosDoner(boolean pasifYoneticiAta) throws Exception {
        Personel yonetici = null;
        if (pasifYoneticiAta) {
            yonetici = personelKaydet(
                    "pasif.zincir.yoneticisi",
                    "Pasif",
                    "Yönetici",
                    yoneticiRolu,
                    false,
                    null
            );
        }
        Personel personelAktor = personelKaydet(
                "bos.zincir." + pasifYoneticiAta,
                "Zincirsiz",
                "Personel",
                personelRolu,
                true,
                yonetici
        );

        String token = jwtService.generateToken(
                personelAktor.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yoneticiler").isEmpty());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"HR", "ADMIN"})
    void hrVeAdminKisiselYoneticiZinciriEndpointiniKullanamaz(RolAdi aktorRolu)
            throws Exception {
        Personel aktor = aktorRolu == RolAdi.HR
                ? hrAktor
                : personelKaydet(
                        "zincir.admin",
                        "Zincir",
                        "Admin",
                        adminRolu,
                        true,
                        null
                );

        String token = jwtService.generateToken(aktor.getPersonelId(), aktorRolu);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI"})
    void pasifPersonelVeYoneticiKisiselZinciriGoremez(RolAdi aktorRolu)
            throws Exception {
        Personel aktor = personelKaydet(
                "pasif.zincir." + aktorRolu.name().toLowerCase(),
                "Pasif",
                aktorRolu.name(),
                rolGetir(aktorRolu),
                false,
                null
        );
        String token = jwtService.generateToken(aktor.getPersonelId(), aktorRolu);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaKisiselZincir404Doner() throws Exception {
        String token = jwtService.generateToken("olmayan-aktor-id", RolAdi.PERSONEL);

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanKisiselZincir401Doner() throws Exception {
        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim"))
                .andExpect(status().isUnauthorized());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void legacyDonguVarsaTumHiyerarsi409Doner() throws Exception {
        Personel birinci = personelKaydet(
                "dongu.birinci",
                "Döngü",
                "Birinci",
                yoneticiRolu,
                true,
                null
        );
        Personel ikinci = personelKaydet(
                "dongu.ikinci",
                "Döngü",
                "İkinci",
                yoneticiRolu,
                true,
                birinci
        );
        birinci.setYonetici(ikinci);
        personelRepository.saveAndFlush(birinci);

        String token = jwtService.generateToken(hrAktor.getPersonelId(), RolAdi.HR);

        mockMvc.perform(get("/api/ekip-dizini/hiyerarsi")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Yönetici hiyerarşisinde tutarsızlık tespit edildi."
                ));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void legacyDonguAktoreDonuyorsaKisiselZincir409Doner() throws Exception {
        Personel yonetici = personelKaydet(
                "dongu.yonetici",
                "Döngü",
                "Yönetici",
                yoneticiRolu,
                true,
                null
        );
        Personel personelAktor = personelKaydet(
                "dongu.personel",
                "Döngü",
                "Personel",
                personelRolu,
                true,
                yonetici
        );
        yonetici.setYonetici(personelAktor);
        personelRepository.saveAndFlush(yonetici);

        String token = jwtService.generateToken(
                personelAktor.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/ekip-dizini/yonetici-zincirim")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Yönetici hiyerarşisinde tutarsızlık tespit edildi."
                ));

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

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            boolean aktif,
            Personel yonetici
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setSicilNumarasi(siradakiSicilNumarasi++);
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        personel.setYonetici(yonetici);
        return personelRepository.saveAndFlush(personel);
    }

    private Yetenek yetenekKaydet(String ad) {
        Yetenek yetenek = new Yetenek();
        yetenek.setYetenekAdi(ad);
        return yetenekRepository.saveAndFlush(yetenek);
    }

    private void yetenekAta(Personel personel, Yetenek yetenek) {
        PersonelYetenek personelYetenek = new PersonelYetenek();
        personelYetenek.setId(new PersonelYetenekId(
                personel.getPersonelId(),
                yetenek.getYetenekId()
        ));
        personelYetenek.setPersonel(personel);
        personelYetenek.setYetenek(yetenek);
        personelYetenekRepository.saveAndFlush(personelYetenek);
    }

    private void onayliIzinKaydet(
            Personel personel,
            LocalDate baslangic,
            LocalDate bitis
    ) {
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?) "
                        + "ON DUPLICATE KEY UPDATE durum_adi = VALUES(durum_adi)",
                TalepDurum.ONAYLANDI.name()
        );
        Integer durumId = jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                TalepDurum.ONAYLANDI.name()
        );
        jdbcTemplate.update(
                "INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi) "
                        + "VALUES ('Hiyerarşi test izni', false) "
                        + "ON DUPLICATE KEY UPDATE izin_turu_adi = VALUES(izin_turu_adi)"
        );
        Integer izinTuruId = jdbcTemplate.queryForObject(
                "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = 'Hiyerarşi test izni'",
                Integer.class
        );
        jdbcTemplate.update(
                """
                INSERT INTO izin_bilgileri (
                    izin_id, personel_id, izin_turu_id,
                    baslangic_tarihi, bitis_tarihi, izin_durum_id,
                    is_gunu_sayisi, talep_tarihi
                ) VALUES (?, ?, ?, ?, ?, ?, 1.0, ?)
                """,
                UUID.randomUUID().toString(),
                personel.getPersonelId(),
                izinTuruId,
                baslangic,
                bitis,
                durumId,
                SABIT_BUGUN.atStartOfDay()
        );
    }

    private void yarimGunTatilKaydet(LocalDate tarih) {
        jdbcTemplate.update(
                """
                INSERT INTO tatil (
                    ad, tarih, bitis_tarihi, kategori, gun_suresi, version
                ) VALUES ('Yarım gün test tatili', ?, ?, 'SIRKET', 'YARIM_GUN', 0)
                """,
                tarih,
                tarih
        );
    }

    @TestConfiguration
    static class SabitSaatTestConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-25T09:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }
}
