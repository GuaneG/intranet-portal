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
import com.jforce.backend.service.IzinTalepKararService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({
        IzinTalepKararControllerIntegrationTest.SabitSaatTestConfig.class,
        IzinTalepKararControllerIntegrationTest.RollbackTestConfig.class
})
class IzinTalepKararControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime SABIT_ISLEM_TARIHI =
            LocalDateTime.of(2026, 8, 25, 12, 0);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IzinBilgileriRepository izinBilgileriRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private IzinKararRollbackTestService rollbackTestService;
    @MockitoSpyBean
    private IzinTalepKararService izinTalepKararService;

    private Personel yonetici;
    private Personel admin;
    private Personel dogrudanAktif;
    private Personel dogrudanPasif;
    private Personel dolayli;
    private Personel baskaEkip;
    private Departman departman;

    @BeforeEach
    void setUp() {
        izinBilgileriRepository.deleteAll();
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        Rol personelRolu = rolKaydet(1, RolAdi.PERSONEL);
        Rol yoneticiRolu = rolKaydet(2, RolAdi.YONETICI);
        Rol hrRolu = rolKaydet(3, RolAdi.HR);
        Rol adminRolu = rolKaydet(4, RolAdi.ADMIN);
        departman = departmanKaydet("Yazılım");
        Departman baskaDepartman = departmanKaydet("Finans");

        yonetici = personelKaydet(
                "karar.yonetici", "Yönetici", "Aktör",
                yoneticiRolu, departman, true, null
        );
        admin = personelKaydet(
                "karar.admin", "Admin", "Aktör",
                adminRolu, departman, true, null
        );
        dogrudanAktif = personelKaydet(
                "karar.dogrudan", "Doğrudan", "Aktif",
                personelRolu, departman, true, yonetici
        );
        dogrudanPasif = personelKaydet(
                "karar.pasif", "Doğrudan", "Pasif",
                hrRolu, departman, false, yonetici
        );
        Personel araYonetici = personelKaydet(
                "karar.ara", "Ara", "Yönetici",
                yoneticiRolu, departman, true, yonetici
        );
        dolayli = personelKaydet(
                "karar.dolayli", "Dolaylı", "Çalışan",
                personelRolu, departman, true, araYonetici
        );
        Personel baskaYonetici = personelKaydet(
                "karar.baska.yonetici", "Başka", "Yönetici",
                yoneticiRolu, baskaDepartman, true, null
        );
        baskaEkip = personelKaydet(
                "karar.baska", "Başka", "Ekip",
                personelRolu, baskaDepartman, true, baskaYonetici
        );

        jdbcTemplate.update(
                "INSERT INTO izin_turu (izin_turu_id, izin_turu_adi, yillik_haktan_duser_mi) VALUES (1, ?, ?)",
                "Yıllık İzin",
                true
        );
        for (TalepDurum durum : TalepDurum.values()) {
            jdbcTemplate.update(
                    "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                    durum.name()
            );
        }
    }

    @AfterEach
    void tearDown() {
        reset(izinTalepKararService);
    }

    @Test
    void yoneticiYalnizAktifDogrudanCalisanTalepleriniGorur()
            throws Exception {
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000104",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 13, 0));
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000103",
                TalepDurum.ONAYLANDI, LocalDateTime.of(2026, 8, 24, 12, 0));
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000102",
                TalepDurum.REDDEDILDI, LocalDateTime.of(2026, 8, 24, 11, 0));
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000101",
                TalepDurum.IPTAL_EDILDI, LocalDateTime.of(2026, 8, 24, 10, 0));
        izinOlustur(dogrudanPasif, "00000000-0000-0000-0000-000000000201",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 15, 0));
        izinOlustur(dolayli, "00000000-0000-0000-0000-000000000301",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 16, 0));
        izinOlustur(baskaEkip, "00000000-0000-0000-0000-000000000401",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 17, 0));
        izinOlustur(yonetici, "00000000-0000-0000-0000-000000000501",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 18, 0));
        auditKaydiOlustur();
        List<Map<String, Object>> izinOnce = izinKayitlari();
        List<Map<String, Object>> auditOnce = auditKayitlari();

        takimTalepleri(yonetici, RolAdi.YONETICI, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(4))
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(10))
                .andExpect(jsonPath("$.toplamKayit").value(4))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(
                        "00000000-0000-0000-0000-000000000104"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.ad").value("Doğrudan"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.soyad").value("Aktif"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.departman").value("Yazılım"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.aktifMi").value(true))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.personelId").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].talepSahibi.ePosta").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].talepSahibi.kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].personelId").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].parolaHash").doesNotExist());

        assertEquals(izinOnce, izinKayitlari());
        assertEquals(auditOnce, auditKayitlari());
    }

    @Test
    void adminAktifPasifSirketGeneliTalepleriGorur() throws Exception {
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000601",
                TalepDurum.ONAYLANDI, LocalDateTime.of(2026, 8, 24, 10, 0));
        izinOlustur(dogrudanPasif, "00000000-0000-0000-0000-000000000602",
                TalepDurum.REDDEDILDI, LocalDateTime.of(2026, 8, 24, 11, 0));
        izinOlustur(baskaEkip, "00000000-0000-0000-0000-000000000603",
                TalepDurum.IPTAL_EDILDI, LocalDateTime.of(2026, 8, 24, 12, 0));

        adminTalepleri(admin, RolAdi.ADMIN, "?page=0&size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(
                        "00000000-0000-0000-0000-000000000603"))
                .andExpect(jsonPath("$.icerik[1].id").value(
                        "00000000-0000-0000-0000-000000000602"))
                .andExpect(jsonPath("$.icerik[1].talepSahibi.aktifMi").value(false))
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.toplamSayfa").value(2));
    }

    @Test
    void durumFiltresiOwnershipIleAndUygulanir() throws Exception {
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000701",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 10, 0));
        izinOlustur(dogrudanAktif, "00000000-0000-0000-0000-000000000702",
                TalepDurum.ONAYLANDI, LocalDateTime.of(2026, 8, 24, 11, 0));
        izinOlustur(dogrudanPasif, "00000000-0000-0000-0000-000000000703",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 12, 0));
        izinOlustur(dolayli, "00000000-0000-0000-0000-000000000704",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 13, 0));
        izinOlustur(baskaEkip, "00000000-0000-0000-0000-000000000705",
                TalepDurum.ONAY_BEKLIYOR, LocalDateTime.of(2026, 8, 24, 14, 0));

        takimTalepleri(
                yonetici,
                RolAdi.YONETICI,
                "?durum=ONAY_BEKLIYOR"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(
                        "00000000-0000-0000-0000-000000000701"))
                .andExpect(jsonPath("$.toplamKayit").value(1));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "ADMIN"})
    void yoneticiListesineYetkisizRollerErisemez(RolAdi jwtRolu)
            throws Exception {
        takimTalepleri(yonetici, jwtRolu, "")
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void adminListesineYetkisizRollerErisemez(RolAdi jwtRolu)
            throws Exception {
        adminTalepleri(admin, jwtRolu, "")
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenOlmadanListeEndpointleri401Doner() throws Exception {
        mockMvc.perform(get("/api/leave-requests/team"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/leave-requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pasifVeyaMissingKararAktoruKontrolluReddedilir() throws Exception {
        Rol yoneticiRolu = rolRepository.findById(2).orElseThrow();
        Personel pasifYonetici = personelKaydet(
                "karar.pasif.yonetici", "Pasif", "Yönetici",
                yoneticiRolu, departman, false, null
        );

        takimTalepleri(pasifYonetici, RolAdi.YONETICI, "")
                .andExpect(status().isForbidden());

        String missingToken = jwtService.generateToken(
                "00000000-0000-0000-0000-000000009999",
                RolAdi.YONETICI
        );
        mockMvc.perform(
                        get("/api/leave-requests/team")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + missingToken
                                )
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void gecersizListeQueryleri400Doner() throws Exception {
        takimTalepleri(yonetici, RolAdi.YONETICI, "?page=-1")
                .andExpect(status().isBadRequest());
        takimTalepleri(yonetici, RolAdi.YONETICI, "?size=0")
                .andExpect(status().isBadRequest());
        takimTalepleri(yonetici, RolAdi.YONETICI, "?size=101")
                .andExpect(status().isBadRequest());
        takimTalepleri(yonetici, RolAdi.YONETICI, "?durum=BILINMIYOR")
                .andExpect(status().isBadRequest());
    }

    @Test
    void yoneticiAktifDogrudanCalisanTalebiniOnaylar() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000001001";
        izinOlustur(
                dogrudanAktif,
                talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isNoContent());

        Map<String, Object> talep = jdbcTemplate.queryForMap(
                """
                        SELECT d.durum_adi,
                               i.islem_yapan_id,
                               i.islem_tarihi,
                               i.karar_notu
                        FROM izin_bilgileri i
                        JOIN durum_turu d
                          ON d.durum_id = i.izin_durum_id
                        WHERE i.izin_id = ?
                        """,
                talepId
        );
        assertEquals(TalepDurum.ONAYLANDI.name(), talep.get("durum_adi"));
        assertEquals(yonetici.getPersonelId(), talep.get("islem_yapan_id"));
        assertEquals(SABIT_ISLEM_TARIHI, talep.get("islem_tarihi"));
        assertNull(talep.get("karar_notu"));

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                        SELECT eylem_tipi, personel_id,
                               hedef_personel_id, detay
                        FROM audit_log
                        """
        );
        assertEquals("IZIN_TALEBI_ONAYLANDI", audit.get("eylem_tipi"));
        assertEquals(yonetici.getPersonelId(), audit.get("personel_id"));
        assertEquals(
                dogrudanAktif.getPersonelId(),
                audit.get("hedef_personel_id")
        );
        assertEquals("İzin talebi onaylandı.", audit.get("detay"));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"YONETICI", "ADMIN"})
    void gecmisteBaslamisBekleyenTalepOnaylanamaz(RolAdi aktorRolu)
            throws Exception {
        Personel aktor = aktorRolu == RolAdi.ADMIN ? admin : yonetici;
        String talepId = aktorRolu == RolAdi.ADMIN
                ? "00000000-0000-0000-0000-000000003003"
                : "00000000-0000-0000-0000-000000003001";
        izinOlustur(
                dogrudanAktif,
                talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 23, 10, 0),
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 24),
                new BigDecimal("1.0")
        );
        List<Map<String, Object>> izinOnce = izinKayitlari();
        List<Map<String, Object>> auditOnce = auditKayitlari();

        talebiOnayla(aktor, aktorRolu, talepId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Başlangıç tarihi geçmiş izin talebi onaylanamaz."
                ));

        assertEquals(izinOnce, izinKayitlari());
        assertEquals(auditOnce, auditKayitlari());
    }

    @Test
    void baslamisAmaBitmemisBekleyenTalepOnaylanamaz() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000003004";
        izinOlustur(
                dogrudanAktif,
                talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 23, 10, 0),
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 26),
                new BigDecimal("3.0")
        );
        List<Map<String, Object>> izinOnce = izinKayitlari();
        List<Map<String, Object>> auditOnce = auditKayitlari();

        talebiOnayla(admin, RolAdi.ADMIN, talepId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Başlangıç tarihi geçmiş izin talebi onaylanamaz."
                ));

        assertEquals(izinOnce, izinKayitlari());
        assertEquals(auditOnce, auditKayitlari());
    }

    @Test
    void bugunBaslayanBekleyenTalepOnaylanabilir() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000003005";
        izinOlustur(
                dogrudanAktif,
                talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0),
                LocalDate.of(2026, 8, 25),
                LocalDate.of(2026, 8, 25),
                new BigDecimal("1.0")
        );

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isNoContent());

        assertEquals(TalepDurum.ONAYLANDI.name(), jdbcTemplate.queryForObject(
                """
                        SELECT d.durum_adi
                        FROM izin_bilgileri i
                        JOIN durum_turu d
                          ON d.durum_id = i.izin_durum_id
                        WHERE i.izin_id = ?
                        """,
                String.class,
                talepId
        ));
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void adminAktifVePasifSahibinTalebiniOnaylayabilir() throws Exception {
        String aktifTalep = "00000000-0000-0000-0000-000000001101";
        String pasifTalep = "00000000-0000-0000-0000-000000001102";
        izinOlustur(dogrudanAktif, aktifTalep, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));
        izinOlustur(dogrudanPasif, pasifTalep, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 11, 0));

        talebiOnayla(admin, RolAdi.ADMIN, aktifTalep)
                .andExpect(status().isNoContent());
        talebiOnayla(admin, RolAdi.ADMIN, pasifTalep)
                .andExpect(status().isNoContent());

        assertEquals(2, jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM izin_bilgileri i
                        JOIN durum_turu d
                          ON d.durum_id = i.izin_durum_id
                        WHERE d.durum_adi = 'ONAYLANDI'
                          AND i.islem_yapan_id = ?
                        """,
                Integer.class,
                admin.getPersonelId()
        ));
        assertEquals(2L, auditLogRepository.count());
    }

    @Test
    void yoneticiBaskaDolayliPasifVeyaKendiTalebineKararVeremez()
            throws Exception {
        Personel[] sahipler = {
                baskaEkip,
                dolayli,
                dogrudanPasif,
                yonetici
        };

        for (int index = 0; index < sahipler.length; index++) {
            String talepId = "00000000-0000-0000-0000-00000000120" + index;
            izinOlustur(
                    sahipler[index],
                    talepId,
                    TalepDurum.ONAY_BEKLIYOR,
                    LocalDateTime.of(2026, 8, 24, 10 + index, 0)
            );
            List<Map<String, Object>> izinOnce = izinKayitlari();
            List<Map<String, Object>> auditOnce = auditKayitlari();

            talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                    .andExpect(status().isForbidden());

            assertEquals(izinOnce, izinKayitlari());
            assertEquals(auditOnce, auditKayitlari());
        }
    }

    @Test
    void kaynakYetkisiDurumKontrolundenOnceUygulanir() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000001301";
        izinOlustur(
                baskaEkip,
                talepId,
                TalepDurum.ONAYLANDI,
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = TalepDurum.class, names = {
            "ONAYLANDI", "REDDEDILDI", "IPTAL_EDILDI"
    })
    void sonuclanmisTalepTekrarOnaylanamaz(TalepDurum durum)
            throws Exception {
        String talepId = switch (durum) {
            case ONAYLANDI -> "00000000-0000-0000-0000-000000001401";
            case REDDEDILDI -> "00000000-0000-0000-0000-000000001402";
            case IPTAL_EDILDI -> "00000000-0000-0000-0000-000000001403";
            default -> throw new IllegalStateException();
        };
        izinOlustur(dogrudanAktif, talepId, durum,
                LocalDateTime.of(2026, 8, 24, 10, 0));

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Talep artık işlem beklemiyor."
                ));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yillikIzinBakiyesiKararAnindaYetersizse409Doner()
            throws Exception {
        dogrudanAktif.setYillikIzinHakki(0);
        personelRepository.saveAndFlush(dogrudanAktif);
        String talepId = "00000000-0000-0000-0000-000000001501";
        izinOlustur(dogrudanAktif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "İzin bakiyesi artık yeterli değil."
                ));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yillikHaktanDusmeyenIzinDusukBakiyedeOnaylanabilir()
            throws Exception {
        dogrudanAktif.setYillikIzinHakki(0);
        personelRepository.saveAndFlush(dogrudanAktif);
        jdbcTemplate.update(
                "INSERT INTO izin_turu (izin_turu_id, izin_turu_adi, yillik_haktan_duser_mi) VALUES (2, ?, ?)",
                "Mazeret İzni",
                false
        );
        String talepId = "00000000-0000-0000-0000-000000001601";
        izinOlustur(
                dogrudanAktif,
                talepId,
                2,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR"})
    void personelVeHrTalepOnaylayamaz(RolAdi jwtRolu) throws Exception {
        String talepId = "00000000-0000-0000-0000-00000000170"
                + jwtRolu.ordinal();
        izinOlustur(dogrudanAktif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));

        talebiOnayla(yonetici, jwtRolu, talepId)
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenOlmadanOnay401Doner() throws Exception {
        mockMvc.perform(patch(
                        "/api/leave-requests/00000000-0000-0000-0000-000000001801/approve"
                ))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gecersizVeEksikTalepIdleriKontrolluReddedilir()
            throws Exception {
        talebiOnayla(yonetici, RolAdi.YONETICI, "uuid-degil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz istek"));
        talebiOnayla(
                yonetici,
                RolAdi.YONETICI,
                "00000000-0000-0000-0000-000000009999"
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "İzin talebi bulunamadı."
                ));
    }

    @Test
    @WithMockUser(roles = "YONETICI")
    void disTransactionHatasindaOnayVeAuditBirlikteRollbackOlur() {
        String talepId = "00000000-0000-0000-0000-000000001901";
        izinOlustur(dogrudanAktif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));
        List<Map<String, Object>> izinOnce = izinKayitlari();
        List<Map<String, Object>> auditOnce = auditKayitlari();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.onaylaVeRollbackEt(
                        yonetici.getPersonelId(),
                        talepId
                )
        );

        assertEquals("rollback-probe", exception.getMessage());
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(TalepDurum.ONAYLANDI.name(),
                rollbackTestService.transactionIcindekiDurum());
        assertEquals(izinOnce, izinKayitlari());
        assertEquals(auditOnce, auditKayitlari());
    }

    @Test
    @WithMockUser(roles = "YONETICI")
    void beklenmeyenKararHatasiGuvenli500DonerVeYanEtkisizdir()
            throws Exception {
        String talepId = "00000000-0000-0000-0000-000000001902";
        izinOlustur(
                dogrudanAktif,
                talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 11, 0)
        );
        auditKaydiOlustur();
        List<Map<String, Object>> izinOnce = izinKayitlari();
        List<Map<String, Object>> auditOnce = auditKayitlari();
        doThrow(new RuntimeException("sql-secret"))
                .when(izinTalepKararService)
                .talebiOnayla(yonetici.getPersonelId(), talepId);

        talebiOnayla(yonetici, RolAdi.YONETICI, talepId)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(
                        "Beklenmeyen bir hata oluştu."
                ))
                .andExpect(content().string(not(containsString(
                        "sql-secret"
                ))));

        assertEquals(izinOnce, izinKayitlari());
        assertEquals(auditOnce, auditKayitlari());
    }

    @Test
    void yoneticiTalebiTrimEdilmisGerekceyleReddeder() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000002001";
        izinOlustur(dogrudanAktif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));

        talebiReddet(
                yonetici,
                RolAdi.YONETICI,
                talepId,
                "{\"kararNotu\":\"  Ekip planlaması uygun değil.  \"}"
        )
                .andExpect(status().isNoContent());

        Map<String, Object> talep = jdbcTemplate.queryForMap(
                """
                        SELECT d.durum_adi, i.islem_yapan_id,
                               i.islem_tarihi, i.karar_notu
                        FROM izin_bilgileri i
                        JOIN durum_turu d
                          ON d.durum_id = i.izin_durum_id
                        WHERE i.izin_id = ?
                        """,
                talepId
        );
        assertEquals(TalepDurum.REDDEDILDI.name(), talep.get("durum_adi"));
        assertEquals(yonetici.getPersonelId(), talep.get("islem_yapan_id"));
        assertEquals(SABIT_ISLEM_TARIHI, talep.get("islem_tarihi"));
        assertEquals("Ekip planlaması uygun değil.", talep.get("karar_notu"));

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                """
                        SELECT eylem_tipi, personel_id,
                               hedef_personel_id, detay
                        FROM audit_log
                        """
        );
        assertEquals("IZIN_TALEBI_REDDEDILDI", audit.get("eylem_tipi"));
        assertEquals(yonetici.getPersonelId(), audit.get("personel_id"));
        assertEquals(dogrudanAktif.getPersonelId(),
                audit.get("hedef_personel_id"));
        assertEquals("İzin talebi reddedildi.", audit.get("detay"));
        assertEquals(false, audit.get("detay").toString()
                .contains("Ekip planlaması"));
    }

    @Test
    void adminPasifSahibinTalebiniReddedebilir() throws Exception {
        String talepId = "00000000-0000-0000-0000-000000002101";
        izinOlustur(dogrudanPasif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));

        talebiReddet(
                admin,
                RolAdi.ADMIN,
                talepId,
                "{\"kararNotu\":\"Şirket kararı.\"}"
        )
                .andExpect(status().isNoContent());
    }

    @Test
    void redKararNotuValidationiYanEtkisiz400Doner() throws Exception {
        String[] gecersizBodyler = {
                "{\"kararNotu\":null}",
                "{\"kararNotu\":\"\"}",
                "{\"kararNotu\":\"   \"}",
                "{\"kararNotu\":\"" + "a".repeat(501) + "\"}",
                "{malformed-json"
        };

        for (int index = 0; index < gecersizBodyler.length; index++) {
            String talepId = "00000000-0000-0000-0000-00000000220" + index;
            izinOlustur(dogrudanAktif, talepId,
                    TalepDurum.ONAY_BEKLIYOR,
                    LocalDateTime.of(2026, 8, 24, 10 + index, 0));
            List<Map<String, Object>> izinOnce = izinKayitlari();
            List<Map<String, Object>> auditOnce = auditKayitlari();

            talebiReddet(
                    yonetici,
                    RolAdi.YONETICI,
                    talepId,
                    gecersizBodyler[index]
            )
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Geçersiz istek"));

            assertEquals(izinOnce, izinKayitlari());
            assertEquals(auditOnce, auditKayitlari());
        }

        String eksikBodyTalepId =
                "00000000-0000-0000-0000-000000002206";
        izinOlustur(dogrudanAktif, eksikBodyTalepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 16, 0));
        mockMvc.perform(
                        patch(
                                "/api/leave-requests/{talepId}/reject",
                                eksikBodyTalepId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(yonetici, RolAdi.YONETICI)
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz istek"));
    }

    @Test
    void besYuzKarakterRedNotuKabulEdilirVeDusukBakiyeEngelDegildir()
            throws Exception {
        dogrudanAktif.setYillikIzinHakki(0);
        personelRepository.saveAndFlush(dogrudanAktif);
        String talepId = "00000000-0000-0000-0000-000000002301";
        izinOlustur(dogrudanAktif, talepId,
                TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));
        String kararNotu = "ğ".repeat(500);

        talebiReddet(
                yonetici,
                RolAdi.YONETICI,
                talepId,
                "{\"kararNotu\":\"" + kararNotu + "\"}"
        )
                .andExpect(status().isNoContent());

        assertEquals(kararNotu, jdbcTemplate.queryForObject(
                "SELECT karar_notu FROM izin_bilgileri WHERE izin_id = ?",
                String.class,
                talepId
        ));
    }

    @Test
    void redAkisiAyniKaynakYetkisiVeDurumKurallariniKullanir()
            throws Exception {
        String baskaTalepId = "00000000-0000-0000-0000-000000002401";
        izinOlustur(baskaEkip, baskaTalepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));
        talebiReddet(
                yonetici,
                RolAdi.YONETICI,
                baskaTalepId,
                "{\"kararNotu\":\"Yetkisiz karar.\"}"
        )
                .andExpect(status().isForbidden());

        String sonuclanmisTalepId =
                "00000000-0000-0000-0000-000000002402";
        izinOlustur(dogrudanAktif, sonuclanmisTalepId,
                TalepDurum.ONAYLANDI,
                LocalDateTime.of(2026, 8, 24, 11, 0));
        talebiReddet(
                yonetici,
                RolAdi.YONETICI,
                sonuclanmisTalepId,
                "{\"kararNotu\":\"Geç karar.\"}"
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Talep artık işlem beklemiyor."
                ));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ownerMyReddedilenTalebinGuvenliKararBilgileriniGorur()
            throws Exception {
        String talepId = "00000000-0000-0000-0000-000000002501";
        izinOlustur(dogrudanAktif, talepId, TalepDurum.ONAY_BEKLIYOR,
                LocalDateTime.of(2026, 8, 24, 10, 0));
        talebiReddet(
                yonetici,
                RolAdi.YONETICI,
                talepId,
                "{\"kararNotu\":\"  Planlama uygun değil.  \"}"
        )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/leave-requests/my")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(dogrudanAktif, RolAdi.PERSONEL)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].durum").value("REDDEDILDI"))
                .andExpect(jsonPath("$.icerik[0].kararNotu").value(
                        "Planlama uygun değil."
                ))
                .andExpect(jsonPath("$.icerik[0].islemTarihi").value(
                        "2026-08-25T12:00:00"
                ))
                .andExpect(jsonPath("$.icerik[0].kararVeren.ad").value(
                        "Yönetici"
                ))
                .andExpect(jsonPath("$.icerik[0].kararVeren.soyad").value(
                        "Aktör"
                ))
                .andExpect(jsonPath(
                        "$.icerik[0].kararVeren.personelId"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.icerik[0].kararVeren.ePosta"
                ).doesNotExist())
                .andExpect(jsonPath(
                        "$.icerik[0].kararVeren.kullaniciAdi"
                ).doesNotExist());
    }

    private ResultActions takimTalepleri(
            Personel aktor,
            RolAdi jwtRolu,
            String query
    ) throws Exception {
        return mockMvc.perform(
                get("/api/leave-requests/team" + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu))
        );
    }

    private ResultActions adminTalepleri(
            Personel aktor,
            RolAdi jwtRolu,
            String query
    ) throws Exception {
        return mockMvc.perform(
                get("/api/admin/leave-requests" + query)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu))
        );
    }

    private ResultActions talebiOnayla(
            Personel aktor,
            RolAdi jwtRolu,
            String talepId
    ) throws Exception {
        return mockMvc.perform(
                patch("/api/leave-requests/{talepId}/approve", talepId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu))
        );
    }

    private ResultActions talebiReddet(
            Personel aktor,
            RolAdi jwtRolu,
            String talepId,
            String body
    ) throws Exception {
        return mockMvc.perform(
                patch("/api/leave-requests/{talepId}/reject", talepId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
        );
    }

    private String bearer(Personel aktor, RolAdi rolAdi) {
        return "Bearer " + jwtService.generateToken(
                aktor.getPersonelId(),
                rolAdi
        );
    }

    private void izinOlustur(
            Personel talepSahibi,
            String izinId,
            TalepDurum durum,
            LocalDateTime talepTarihi
    ) {
        izinOlustur(talepSahibi, izinId, 1, durum, talepTarihi);
    }

    private void izinOlustur(
            Personel talepSahibi,
            String izinId,
            int izinTuruId,
            TalepDurum durum,
            LocalDateTime talepTarihi
    ) {
        LocalDate izinGunu = LocalDate.of(2026, 9, 7);
        izinOlustur(
                talepSahibi,
                izinId,
                izinTuruId,
                durum,
                talepTarihi,
                izinGunu,
                izinGunu,
                new BigDecimal("1.0")
        );
    }

    private void izinOlustur(
            Personel talepSahibi,
            String izinId,
            TalepDurum durum,
            LocalDateTime talepTarihi,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            BigDecimal isGunuSayisi
    ) {
        izinOlustur(
                talepSahibi,
                izinId,
                1,
                durum,
                talepTarihi,
                baslangicTarihi,
                bitisTarihi,
                isGunuSayisi
        );
    }

    private void izinOlustur(
            Personel talepSahibi,
            String izinId,
            int izinTuruId,
            TalepDurum durum,
            LocalDateTime talepTarihi,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            BigDecimal isGunuSayisi
    ) {
        Integer durumId = jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                durum.name()
        );
        jdbcTemplate.update(
                """
                        INSERT INTO izin_bilgileri (
                            izin_id, personel_id, izin_turu_id,
                            baslangic_tarihi, bitis_tarihi,
                            is_gunu_sayisi, izin_durum_id,
                            yoneticiye_not, talep_tarihi
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                izinId,
                talepSahibi.getPersonelId(),
                izinTuruId,
                baslangicTarihi,
                bitisTarihi,
                isGunuSayisi,
                durumId,
                "Güvenli talep notu",
                talepTarihi
        );
    }

    private void auditKaydiOlustur() {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_log (
                            log_id, personel_id, eylem_tipi,
                            kullanici_adi, detay, olusma_zamani
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                "00000000-0000-0000-0000-000000009001",
                admin.getPersonelId(),
                "LOGIN_BASARILI",
                admin.getKullaniciAdi(),
                "Mevcut audit",
                LocalDateTime.of(2026, 8, 24, 9, 0)
        );
    }

    private List<Map<String, Object>> izinKayitlari() {
        return jdbcTemplate.queryForList(
                """
                        SELECT izin_id, personel_id, izin_turu_id,
                               baslangic_tarihi, bitis_tarihi,
                               is_gunu_sayisi, izin_durum_id,
                               yoneticiye_not, talep_tarihi,
                               islem_yapan_id, islem_tarihi, karar_notu
                        FROM izin_bilgileri
                        ORDER BY izin_id
                        """
        );
    }

    private List<Map<String, Object>> auditKayitlari() {
        return jdbcTemplate.queryForList(
                """
                        SELECT log_id, personel_id, eylem_tipi,
                               kullanici_adi, detay,
                               hedef_personel_id, olusma_zamani
                        FROM audit_log
                        ORDER BY log_id
                        """
        );
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman kaydedilecek = new Departman();
        kaydedilecek.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(kaydedilecek);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman personelDepartmani,
            boolean aktif,
            Personel personelYoneticisi
    ) {
        Personel kaydedilecek = new Personel();
        kaydedilecek.setKullaniciAdi(kullaniciAdi);
        kaydedilecek.setParolaHash("test-hash");
        kaydedilecek.setAdi(ad);
        kaydedilecek.setSoyadi(soyad);
        kaydedilecek.setDogumTarihi(LocalDate.of(1995, 1, 1));
        kaydedilecek.setePosta(kullaniciAdi + "@test.local");
        kaydedilecek.setDepartman(personelDepartmani);
        kaydedilecek.setRol(rol);
        kaydedilecek.setCalisiyorMu(aktif);
        kaydedilecek.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        kaydedilecek.setYillikIzinHakki(20);
        kaydedilecek.setProfilFoto("def_pfp.png");
        kaydedilecek.setParolaDegistirilmeliMi(false);
        kaydedilecek.setYonetici(personelYoneticisi);
        return personelRepository.saveAndFlush(kaydedilecek);
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfig {

        @Bean
        IzinKararRollbackTestService izinKararRollbackTestService(
                IzinTalepKararService izinTalepKararService,
                IzinBilgileriRepository izinBilgileriRepository,
                AuditLogRepository auditLogRepository
        ) {
            return new IzinKararRollbackTestService(
                    izinTalepKararService,
                    izinBilgileriRepository,
                    auditLogRepository
            );
        }
    }

    static class IzinKararRollbackTestService {

        private final IzinTalepKararService izinTalepKararService;
        private final IzinBilgileriRepository izinBilgileriRepository;
        private final AuditLogRepository auditLogRepository;
        private long transactionIcindekiAuditSayisi;
        private String transactionIcindekiDurum;

        IzinKararRollbackTestService(
                IzinTalepKararService izinTalepKararService,
                IzinBilgileriRepository izinBilgileriRepository,
                AuditLogRepository auditLogRepository
        ) {
            this.izinTalepKararService = izinTalepKararService;
            this.izinBilgileriRepository = izinBilgileriRepository;
            this.auditLogRepository = auditLogRepository;
        }

        @Transactional
        public void onaylaVeRollbackEt(String aktorId, String talepId) {
            izinTalepKararService.talebiOnayla(aktorId, talepId);
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            transactionIcindekiDurum = izinBilgileriRepository
                    .findById(talepId)
                    .orElseThrow()
                    .getIzinDurumId()
                    .getDurumKodu()
                    .name();
            throw new IllegalStateException("rollback-probe");
        }

        long transactionIcindekiAuditSayisi() {
            return transactionIcindekiAuditSayisi;
        }

        String transactionIcindekiDurum() {
            return transactionIcindekiDurum;
        }
    }
}
