package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(DashboardControllerIntegrationTest.SabitSaatTestConfig.class)
class DashboardControllerIntegrationTest extends AbstractIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    private Departman departman;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM rezervasyon");
        jdbcTemplate.update("DELETE FROM oda");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");
        jdbcTemplate.update("DELETE FROM tatil");
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        departman = departmanKaydet("Bilgi Teknolojileri");
    }

    @Test
    void personelKendiDepartmanindakiToplamiVeIlkBesKisiyiGorur() throws Exception {
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Rol yoneticiRolu = rolKaydet(RolAdi.YONETICI);
        Departman baskaDepartman = departmanKaydet("İnsan Kaynakları");

        Personel aktor = personelKaydet(
                "aktor.personel",
                "Aktif",
                "Aktör",
                personelRolu,
                departman,
                true
        );
        Personel yonetici = personelKaydet("yonetici", "Burcu", "Yönetici", yoneticiRolu, departman, true);
        aktor.setYonetici(yonetici);
        personelRepository.saveAndFlush(aktor);

        personelKaydet("cem.personel", "Cem", "Personel", personelRolu, departman, true);
        personelKaydet("deniz.personel", "Deniz", "Personel", personelRolu, departman, true);
        personelKaydet("eda.personel", "Eda", "Personel", personelRolu, departman, true);
        personelKaydet("fatma.personel", "Fatma", "Personel", personelRolu, departman, true);
        personelKaydet("gizem.personel", "Gizem", "Personel", personelRolu, departman, true);
        personelKaydet("baska.departman", "Ahmet", "Başka", personelRolu, baskaDepartman, true);
        personelKaydet("pasif.personel", "Pasif", "Personel", personelRolu, departman, false);

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktifEkipUyesiSayisi").value(7))
                .andExpect(jsonPath("$.ekipDiziniOzeti.length()").value(5))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].ad").value("Aktif"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[1].ad").value("Burcu"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[1].profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[2].ad").value("Cem"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[3].ad").value("Deniz"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[4].ad").value("Eda"))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.ekipDiziniOzeti[1].yoneticinMi").value(true))
                .andExpect(jsonPath("$.ekipDiziniOzeti[2].yoneticinMi").value(false))
                .andExpect(jsonPath("$.ekipDiziniOzeti[3].yoneticinMi").value(false))
                .andExpect(jsonPath("$.ekipDiziniOzeti[4].yoneticinMi").value(false))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].personelId").doesNotExist())
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].ePosta").doesNotExist())
                .andExpect(jsonPath("$.bugunDogumGunleri").isArray())
                .andExpect(jsonPath("$.sonDuyurular").isArray())
                .andExpect(jsonPath("$.kalanIzin").doesNotExist())
                .andExpect(jsonPath("$.bugunIzinliSayisi").value(0))
                .andExpect(jsonPath("$.odaDoluluk").isArray())
                .andExpect(jsonPath("$.sonTakdirler").isArray())
                .andExpect(jsonPath("$.bekleyenTaleplerim").isArray());

        assertEquals(0, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"YONETICI", "HR"})
    void yoneticiVeHrAdminHaricTumAktifPersonelleriOzetler(RolAdi aktorRolu) throws Exception {
        Rol aktorRolKaydi = rolKaydet(aktorRolu);
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Rol adminRolu = rolKaydet(RolAdi.ADMIN);
        Departman baskaDepartman = departmanKaydet("İnsan Kaynakları");

        Personel aktor = personelKaydet("aktor." + aktorRolu.name().toLowerCase(), "Aktif", "Aktör", aktorRolKaydi, departman, true);
        personelKaydet("personel.baska", "Başka", "Departman", personelRolu, baskaDepartman, true);
        personelKaydet("admin", "Admin", "Kullanıcı", adminRolu, baskaDepartman, true);
        personelKaydet("pasif", "Pasif", "Kullanıcı", personelRolu, departman, false);

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(aktor.getPersonelId(), aktorRolu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktifEkipUyesiSayisi").value(2))
                .andExpect(jsonPath("$.ekipDiziniOzeti.length()").value(2))
                .andExpect(jsonPath("$.ekipDiziniOzeti[*].ad").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Admin"))));
    }

    @Test
    void adminButunAktifPersonelleriOzetler() throws Exception {
        Rol adminRolu = rolKaydet(RolAdi.ADMIN);
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Departman baskaDepartman = departmanKaydet("İnsan Kaynakları");

        Personel aktor = personelKaydet("aktor.admin", "Aktif", "Admin", adminRolu, departman, true);
        personelKaydet("diger.admin", "Diğer", "Admin", adminRolu, baskaDepartman, true);
        personelKaydet("personel", "Aktif", "Personel", personelRolu, baskaDepartman, true);
        personelKaydet("pasif", "Pasif", "Personel", personelRolu, departman, false);

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(aktor.getPersonelId(), RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktifEkipUyesiSayisi").value(3))
                .andExpect(jsonPath("$.ekipDiziniOzeti.length()").value(3));
    }

    @Test
    void personelIzinBakiyesindeOnaylananVeBekleyeniOndalikliAyirir()
            throws Exception {
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Rol yoneticiRolu = rolKaydet(RolAdi.YONETICI);
        Personel yonetici = personelKaydet(
                "bakiye.yonetici",
                "Bakiye",
                "Yönetici",
                yoneticiRolu,
                departman,
                true
        );
        Personel aktor = personelKaydet(
                "bakiye.personel",
                "Bakiye",
                "Personel",
                personelRolu,
                departman,
                true
        );
        aktor.setYonetici(yonetici);
        aktor = personelRepository.saveAndFlush(aktor);

        Integer yillikIzinTuruId = izinTuruOlustur("Yıllık izin", true);
        Integer mazeretIzinTuruId = izinTuruOlustur("Mazeret izni", false);
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);
        Integer onayBekliyorId = durumOlustur(TalepDurum.ONAY_BEKLIYOR);
        Integer reddedildiId = durumOlustur(TalepDurum.REDDEDILDI);

        izinOlustur(aktor, yillikIzinTuruId, onaylandiId, new BigDecimal("4.5"));
        izinOlustur(aktor, yillikIzinTuruId, onayBekliyorId, new BigDecimal("2.0"));
        izinOlustur(aktor, yillikIzinTuruId, reddedildiId, new BigDecimal("7.0"));
        izinOlustur(aktor, mazeretIzinTuruId, onaylandiId, new BigDecimal("1.0"));

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.izinBakiyesi.toplamHak").value(20.0))
                .andExpect(jsonPath("$.izinBakiyesi.kullanilan").value(4.5))
                .andExpect(jsonPath("$.izinBakiyesi.onayBekleyen").value(2.0))
                .andExpect(jsonPath("$.izinBakiyesi.kalan").value(13.5))
                .andExpect(jsonPath("$.kalanIzin").doesNotExist());
    }

    @Test
    void adminVeEnUstYoneticiIzinBakiyesiGoremez() throws Exception {
        Rol adminRolu = rolKaydet(RolAdi.ADMIN);
        Rol yoneticiRolu = rolKaydet(RolAdi.YONETICI);
        Personel admin = personelKaydet(
                "bakiye.admin",
                "Bakiye",
                "Admin",
                adminRolu,
                departman,
                true
        );
        Personel enUstYonetici = personelKaydet(
                "en.ust.yonetici",
                "En Üst",
                "Yönetici",
                yoneticiRolu,
                departman,
                true
        );

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        admin.getPersonelId(),
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.izinBakiyesi").value(nullValue()));

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        enUstYonetici.getPersonelId(),
                                        RolAdi.YONETICI
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.izinBakiyesi").value(nullValue()));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"HR", "YONETICI"})
    void hrVeYoneticisiOlanYoneticiIzinBakiyesiniGorur(RolAdi aktorRolu)
            throws Exception {
        Rol yoneticiRolu = rolKaydet(RolAdi.YONETICI);
        Rol aktorRolKaydi = aktorRolu == RolAdi.YONETICI
                ? yoneticiRolu
                : rolKaydet(RolAdi.HR);
        Personel ustYonetici = personelKaydet(
                "bakiye.ust." + aktorRolu.name().toLowerCase(),
                "Üst",
                "Yönetici",
                yoneticiRolu,
                departman,
                true
        );
        Personel aktor = personelKaydet(
                "bakiye.aktor." + aktorRolu.name().toLowerCase(),
                "Bakiye",
                "Aktör",
                aktorRolKaydi,
                departman,
                true
        );
        aktor.setYonetici(ustYonetici);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        aktorRolu
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.izinBakiyesi.toplamHak").value(20.0))
                .andExpect(jsonPath("$.izinBakiyesi.kullanilan").value(0.0))
                .andExpect(jsonPath("$.izinBakiyesi.onayBekleyen").value(0.0))
                .andExpect(jsonPath("$.izinBakiyesi.kalan").value(20.0));
    }

    @Test
    void dashboardEkipDiziniOzetiBugunkuIzinDurumunuTasır()
            throws Exception {
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Personel aktor = personelKaydet(
                "dashboard.aktor",
                "Dashboard",
                "Aktör",
                personelRolu,
                departman,
                true
        );
        Personel izinli = personelKaydet(
                "dashboard.izinli",
                "İzinli",
                "Personel",
                personelRolu,
                departman,
                true
        );
        Integer izinTuruId = izinTuruOlustur("Yıllık izin", true);
        Integer onaylandiId = durumOlustur(TalepDurum.ONAYLANDI);

        izinOlustur(
                izinli,
                izinTuruId,
                onaylandiId,
                BigDecimal.ONE,
                SABIT_BUGUN,
                SABIT_BUGUN
        );

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ekipDiziniOzeti[1].ad").value("İzinli"))
                .andExpect(
                        jsonPath("$.ekipDiziniOzeti[1].bugunIzinDurumu")
                                .value("IZINLI")
                );
    }

    @Test
    void normalGundeOdaDoluluklariniGuvenliHesaplar() throws Exception {
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Personel aktor = personelKaydet(
                "dashboard.oda.aktor",
                "Oda",
                "Aktör",
                personelRolu,
                departman,
                true
        );
        int buyukOdaId = odaKaydet("Büyük Salon", 12);
        int kucukOdaId = odaKaydet("Küçük Oda", 6);

        rezervasyonKaydet(
                aktor, buyukOdaId, 9, 11, "Gizli toplantı", 5, "AKTIF"
        );
        rezervasyonKaydet(
                aktor, buyukOdaId, 12, 13, "Anlık toplantı", 3, "AKTIF"
        );
        rezervasyonKaydet(
                aktor, buyukOdaId, 15, 17, "İptal toplantı", 4,
                "IPTAL_EDILDI"
        );

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.odaDoluluk.length()").value(2))
                .andExpect(jsonPath("$.odaDoluluk[0].odaId").value(buyukOdaId))
                .andExpect(jsonPath("$.odaDoluluk[0].odaAd").value("Büyük Salon"))
                .andExpect(jsonPath("$.odaDoluluk[0].kapasite").value(12))
                .andExpect(jsonPath("$.odaDoluluk[0].anlikDurum").value("DOLU"))
                .andExpect(jsonPath("$.odaDoluluk[0].dolulukYuzdesi").value(30))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar.length()").value(2))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar[0].baslangicSaat").value(9))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar[0].bitisSaat").value(11))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar[1].baslangicSaat").value(12))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar[1].bitisSaat").value(13))
                .andExpect(jsonPath("$.odaDoluluk[0].baslik").doesNotExist())
                .andExpect(jsonPath("$.odaDoluluk[0].olusturan").doesNotExist())
                .andExpect(jsonPath("$.odaDoluluk[0].katilimciSayisi").doesNotExist())
                .andExpect(jsonPath("$.odaDoluluk[1].odaId").value(kucukOdaId))
                .andExpect(jsonPath("$.odaDoluluk[1].odaAd").value("Küçük Oda"))
                .andExpect(jsonPath("$.odaDoluluk[1].kapasite").value(6))
                .andExpect(jsonPath("$.odaDoluluk[1].anlikDurum").value("MUSAIT"))
                .andExpect(jsonPath("$.odaDoluluk[1].dolulukYuzdesi").value(0))
                .andExpect(jsonPath("$.odaDoluluk[1].doluAraliklar").isEmpty());

        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void yarimGunVeKapaliGundeOdaDurumunuDogruHesaplar() throws Exception {
        Rol personelRolu = rolKaydet(RolAdi.PERSONEL);
        Personel aktor = personelKaydet(
                "dashboard.yarim.aktor",
                "Yarım",
                "Aktör",
                personelRolu,
                departman,
                true
        );
        int odaId = odaKaydet("Büyük Salon", 12);
        rezervasyonKaydet(
                aktor, odaId, 11, 13, "Yarım gün toplantısı", 6, "AKTIF"
        );
        tatilKaydet("YARIM_GUN");

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.odaDoluluk[0].anlikDurum").value("DOLU"))
                .andExpect(jsonPath("$.odaDoluluk[0].dolulukYuzdesi").value(40))
                .andExpect(jsonPath("$.odaDoluluk[0].doluAraliklar.length()").value(1));

        jdbcTemplate.update("DELETE FROM tatil");
        tatilKaydet("TAM_GUN");

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.odaDoluluk[0].anlikDurum").value("KAPALI"))
                .andExpect(jsonPath("$.odaDoluluk[0].dolulukYuzdesi").value(0));

        assertEquals(0, auditLogRepository.count());
    }

    private Rol rolKaydet(RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId(rolAdi));
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private int rolId(RolAdi rolAdi) {
        return switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };
    }

    private Departman departmanKaydet(String ad) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(yeniDepartman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman personelDepartmani,
            boolean aktif
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(personelDepartmani);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        return personelRepository.saveAndFlush(personel);
    }

    private Integer izinTuruOlustur(
            String izinTuruAdi,
            boolean yillikHaktanDuserMi
    ) {
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
                "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = ?",
                Integer.class,
                izinTuruAdi
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
            BigDecimal isGunuSayisi
    ) {
        izinOlustur(
                personel,
                izinTuruId,
                durumId,
                isGunuSayisi,
                SABIT_BUGUN.plusDays(7),
                SABIT_BUGUN.plusDays(7)
        );
    }

    private int odaKaydet(String ad, int kapasite) {
        jdbcTemplate.update(
                "INSERT INTO oda (oda_ad, oda_kapasite) VALUES (?, ?)",
                ad,
                kapasite
        );
        return jdbcTemplate.queryForObject(
                "SELECT oda_id FROM oda WHERE oda_ad = ?",
                Integer.class,
                ad
        );
    }

    private void rezervasyonKaydet(
            Personel olusturan,
            int odaId,
            int baslangicSaat,
            int bitisSaat,
            String baslik,
            int katilimciSayisi,
            String durum
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO rezervasyon (
                    rezervasyon_id, olusturan_id, baslik, tarih,
                    baslangic_saat, bitis_saat, oda_id,
                    katilimci_sayisi, durum, olusturma_zamani
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(),
                olusturan.getPersonelId(),
                baslik,
                SABIT_BUGUN,
                baslangicSaat,
                bitisSaat,
                odaId,
                katilimciSayisi,
                durum,
                LocalDateTime.of(2026, 8, 24, 8, 0)
        );
    }

    private void tatilKaydet(String gunSuresi) {
        jdbcTemplate.update(
                """
                INSERT INTO tatil (
                    ad, tarih, bitis_tarihi, kategori, gun_suresi, version
                ) VALUES (?, ?, ?, 'SIRKET', ?, 0)
                """,
                "Dashboard tatili",
                SABIT_BUGUN,
                SABIT_BUGUN,
                gunSuresi
        );
    }

    private void izinOlustur(
            Personel personel,
            Integer izinTuruId,
            Integer durumId,
            BigDecimal isGunuSayisi,
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
                isGunuSayisi,
                durumId,
                LocalDateTime.of(2026, 8, 1, 9, 0)
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
