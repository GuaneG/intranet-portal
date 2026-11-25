package com.jforce.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jforce.backend.model.dto.request.IzinTalepCreateRequest;
import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.IzinBilgileri;
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
import com.jforce.backend.service.IzinTalepService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@AutoConfigureMockMvc
@Import({
        IzinTalepControllerIntegrationTest.SabitSaatTestConfig.class,
        IzinTalepControllerIntegrationTest.RollbackTestConfiguration.class
})
class IzinTalepControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate SABIT_BUGUN = LocalDate.of(2026, 8, 24);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JWTService jwtService;
    @MockitoSpyBean
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
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private IzinTalepRollbackTestService rollbackTestService;

    private Personel personel;
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
        jdbcTemplate.update("DELETE FROM tatil");
        jdbcTemplate.update("DELETE FROM izin_turu");
        jdbcTemplate.update("DELETE FROM durum_turu");

        Rol personelRolu = rolKaydet(1, RolAdi.PERSONEL);
        departman = departmanKaydet("Yazılım");
        personel = personelKaydet(
                "izin.personel",
                personelRolu,
                departman,
                true,
                null
        );

        jdbcTemplate.update(
                "INSERT INTO izin_turu (izin_turu_id, izin_turu_adi, yillik_haktan_duser_mi) VALUES (1, ?, ?)",
                "Mazeret İzni",
                false
        );
        jdbcTemplate.update(
                "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                "ONAY_BEKLIYOR"
        );
    }

    @Test
    void ownerGecmisTarihliBekleyenTalebiniIptalEderVeGuvenliAuditOlusturur()
            throws Exception {
        tumTalepDurumlariniHazirla();
        String talepId = "00000000-0000-0000-0000-000000009001";
        izinOlustur(
                personel,
                talepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                SABIT_BUGUN.minusDays(10),
                SABIT_BUGUN.minusDays(10),
                new BigDecimal("1.0"),
                "yalnız talep sahibi görmeli",
                LocalDateTime.of(2026, 8, 10, 10, 0)
        );

        mockMvc.perform(patch(
                        "/api/leave-requests/{talepId}/cancel",
                        talepId
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(personel, RolAdi.PERSONEL)
                        ))
                .andExpect(status().isNoContent());

        Map<String, Object> izin = jdbcTemplate.queryForMap(
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
        assertEquals("IPTAL_EDILDI", izin.get("durum_adi"));
        assertEquals(personel.getPersonelId(), izin.get("islem_yapan_id"));
        assertEquals(
                LocalDateTime.of(2026, 8, 24, 12, 0),
                izin.get("islem_tarihi")
        );
        assertNull(izin.get("karar_notu"));

        List<Map<String, Object>> auditler = auditKayitlariniGetir();
        assertEquals(1, auditler.size());
        Map<String, Object> audit = auditler.getFirst();
        assertEquals(
                "IZIN_TALEBI_IPTAL_EDILDI",
                audit.get("eylem_tipi")
        );
        assertEquals(personel.getPersonelId(), audit.get("personel_id"));
        assertEquals(
                personel.getPersonelId(),
                audit.get("hedef_personel_id")
        );
        assertEquals(personel.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals("İzin talebi iptal edildi.", audit.get("detay"));
        assertFalse(audit.get("detay").toString().contains(talepId));
        assertFalse(audit.get("detay").toString().contains("yalnız talep"));
    }

    @Test
    void yoneticiHrVeAdminKendiLegacyTalebiniIptalEdebilir()
            throws Exception {
        tumTalepDurumlariniHazirla();
        Rol hrRolu = rolKaydet(2, RolAdi.HR);
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Rol adminRolu = rolKaydet(4, RolAdi.ADMIN);
        Personel hr = personelKaydet(
                "izin.iptal.hr",
                hrRolu,
                departman,
                true,
                null
        );
        Personel yonetici = personelKaydet(
                "izin.iptal.yonetici",
                yoneticiRolu,
                departman,
                true,
                null
        );
        Personel admin = personelKaydet(
                "izin.iptal.admin",
                adminRolu,
                departman,
                true,
                null
        );
        String yoneticiTalepId =
                "00000000-0000-0000-0000-000000009002";
        String hrTalepId =
                "00000000-0000-0000-0000-000000009012";
        String adminTalepId =
                "00000000-0000-0000-0000-000000009003";
        izinOlustur(
                hr,
                hrTalepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 9, 0)
        );
        izinOlustur(
                yonetici,
                yoneticiTalepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        izinOlustur(
                admin,
                adminTalepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 11, 0)
        );

        talebiIptalEt(hr, RolAdi.HR, hrTalepId)
                .andExpect(status().isNoContent());
        talebiIptalEt(yonetici, RolAdi.YONETICI, yoneticiTalepId)
                .andExpect(status().isNoContent());
        talebiIptalEt(admin, RolAdi.ADMIN, adminTalepId)
                .andExpect(status().isNoContent());

        assertEquals(
                3,
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                FROM izin_bilgileri i
                                JOIN durum_turu d
                                  ON d.durum_id = i.izin_durum_id
                                WHERE d.durum_adi = 'IPTAL_EDILDI'
                                """,
                        Integer.class
                )
        );
        assertEquals(3L, auditLogRepository.count());
    }

    @Test
    void baskaOwnerVeAdminBaskasininTalebiniIptalEdemez()
            throws Exception {
        tumTalepDurumlariniHazirla();
        Personel baskaOwner = personelKaydet(
                "izin.iptal.baska",
                personel.getRol(),
                departman,
                true,
                null
        );
        Rol adminRolu = rolKaydet(4, RolAdi.ADMIN);
        Personel admin = personelKaydet(
                "izin.iptal.baska.admin",
                adminRolu,
                departman,
                true,
                null
        );
        String talepId = "00000000-0000-0000-0000-000000009004";
        izinOlustur(
                personel,
                talepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        List<Map<String, Object>> oncekiIzinler = izinKayitlariniGetir();

        talebiIptalEt(baskaOwner, RolAdi.PERSONEL, talepId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Bu işlem için yetkiniz bulunmuyor."
                ));
        talebiIptalEt(admin, RolAdi.ADMIN, talepId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Bu işlem için yetkiniz bulunmuyor."
                ));

        assertEquals(oncekiIzinler, izinKayitlariniGetir());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void onaylanmisReddedilmisVeyaIptalEdilmisTalepTekrarIptalEdilemez()
            throws Exception {
        tumTalepDurumlariniHazirla();
        TalepDurum[] sonDurumlar = {
                TalepDurum.ONAYLANDI,
                TalepDurum.REDDEDILDI,
                TalepDurum.IPTAL_EDILDI
        };

        for (int index = 0; index < sonDurumlar.length; index++) {
            String talepId = String.format(
                    "00000000-0000-0000-0000-%012d",
                    9005 + index
            );
            izinOlustur(
                    personel,
                    talepId,
                    1,
                    sonDurumlar[index],
                    sonrakiPazartesi(),
                    sonrakiPazartesi(),
                    new BigDecimal("1.0"),
                    null,
                    LocalDateTime.of(2026, 8, 20, 10 + index, 0)
            );
        }
        List<Map<String, Object>> oncekiIzinler = izinKayitlariniGetir();

        for (int index = 0; index < sonDurumlar.length; index++) {
            String talepId = String.format(
                    "00000000-0000-0000-0000-%012d",
                    9005 + index
            );
            talebiIptalEt(personel, RolAdi.PERSONEL, talepId)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "Talep artık işlem beklemiyor."
                    ));
        }

        assertEquals(oncekiIzinler, izinKayitlariniGetir());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void iptaldeGecersizId400EksikTalep404Doner()
            throws Exception {
        talebiIptalEt(personel, RolAdi.PERSONEL, "gecersiz-id")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz istek"));
        talebiIptalEt(
                personel,
                RolAdi.PERSONEL,
                "00000000-0000-0000-0000-000000009099"
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "İzin talebi bulunamadı."
                ));

        assertYanEtkiYok();
    }

    @Test
    void pasifEksikVeyaUnauthenticatedOwnerTalebiIptalEdemez()
            throws Exception {
        tumTalepDurumlariniHazirla();
        String talepId = "00000000-0000-0000-0000-000000009008";
        izinOlustur(
                personel,
                talepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );
        List<Map<String, Object>> oncekiIzinler = izinKayitlariniGetir();

        personel = personelRepository.findById(personel.getPersonelId())
                .orElseThrow();
        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);
        talebiIptalEt(personel, RolAdi.PERSONEL, talepId)
                .andExpect(status().isForbidden());

        personel.setCalisiyorMu(true);
        personel = personelRepository.saveAndFlush(personel);
        talebiIptalEt(personel, RolAdi.HR, talepId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Bu işlem için yetkiniz bulunmuyor."
                ));

        String eksikPersonelToken = "Bearer " + jwtService.generateToken(
                UUID.randomUUID().toString(),
                RolAdi.PERSONEL
        );
        mockMvc.perform(patch(
                        "/api/leave-requests/{talepId}/cancel",
                        talepId
                )
                        .header(HttpHeaders.AUTHORIZATION, eksikPersonelToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Personel bulunamadı."
                ));
        mockMvc.perform(patch(
                        "/api/leave-requests/{talepId}/cancel",
                        talepId
                ))
                .andExpect(status().isUnauthorized());

        assertEquals(oncekiIzinler, izinKayitlariniGetir());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHatasindaIptalVeAuditBirlikteRollbackOlur() {
        tumTalepDurumlariniHazirla();
        String talepId = "00000000-0000-0000-0000-000000009009";
        izinOlustur(
                personel,
                talepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 20, 10, 0)
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.talebiIptalEtVeHataFirlat(
                        personel.getPersonelId(),
                        talepId
                )
        );

        assertEquals(
                "IPTAL_EDILDI",
                rollbackTestService.transactionIcindekiIptalDurumu()
        );
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(
                "ONAY_BEKLIYOR",
                izinDurumunuGetir(talepId)
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bekleyenCreateResponseKararAlanlariniNullDoner() throws Exception {
        talepOlustur(personel, RolAdi.PERSONEL, gecerliRequestBody())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kararNotu").value(nullValue()))
                .andExpect(jsonPath("$.islemTarihi").value(nullValue()))
                .andExpect(jsonPath("$.kararVeren").value(nullValue()));
    }

    @Test
    void myKararBilgileriniGuvenliVeNullableDoner() throws Exception {
        tumTalepDurumlariniHazirla();
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Personel kararVeren = personelKaydet(
                "izin.karar.veren",
                yoneticiRolu,
                departman,
                true,
                null
        );
        String reddedilenId = "00000000-0000-0000-0000-000000000010";
        LocalDate tarih = sonrakiPazartesi();
        izinOlustur(
                personel,
                reddedilenId,
                1,
                TalepDurum.REDDEDILDI,
                tarih,
                tarih,
                new BigDecimal("1.0"),
                "owner-notu",
                LocalDateTime.of(2026, 8, 24, 12, 0)
        );
        jdbcTemplate.update(
                """
                        UPDATE izin_bilgileri
                        SET islem_yapan_id = ?,
                            islem_tarihi = ?,
                            karar_notu = ?
                        WHERE izin_id = ?
                        """,
                kararVeren.getPersonelId(),
                LocalDateTime.of(2026, 8, 25, 14, 30),
                "Ekip planlaması nedeniyle uygun değil.",
                reddedilenId
        );
        auditKaydiOlustur(
                "00000000-0000-0000-0000-000000000011",
                personel,
                "karar-okuma-oncesi-audit"
        );
        List<Map<String, Object>> oncekiIzinler = izinKayitlariniGetir();
        List<Map<String, Object>> oncekiAuditler = auditKayitlariniGetir();

        String responseBody = kendiTalepleriniGetir(
                personel,
                RolAdi.PERSONEL,
                "?durum=REDDEDILDI"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].kararNotu").value(
                        "Ekip planlaması nedeniyle uygun değil."
                ))
                .andExpect(jsonPath("$.icerik[0].islemTarihi").value(
                        "2026-08-25T14:30:00"
                ))
                .andExpect(jsonPath("$.icerik[0].kararVeren.ad").value(
                        kararVeren.getAdi()
                ))
                .andExpect(jsonPath("$.icerik[0].kararVeren.soyad").value(
                        kararVeren.getSoyadi()
                ))
                .andExpect(jsonPath("$.icerik[0].kararVeren.personelId")
                        .doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kararVeren.ePosta")
                        .doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kararVeren.kullaniciAdi")
                        .doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains(kararVeren.getPersonelId()));
        assertFalse(responseBody.contains(kararVeren.getePosta()));
        assertFalse(responseBody.contains(kararVeren.getKullaniciAdi()));
        assertEquals(oncekiIzinler, izinKayitlariniGetir());
        assertEquals(oncekiAuditler, auditKayitlariniGetir());
    }

    @Test
    void personelYalnizKendiTalepleriniKararliSayfaylaGorur()
            throws Exception {
        tumTalepDurumlariniHazirla();
        Personel baskaPersonel = personelKaydet(
                "izin.baska.personel",
                personel.getRol(),
                departman,
                true,
                null
        );
        LocalDate tarih = sonrakiPazartesi();
        String enBuyukEsitZamanliId =
                "00000000-0000-0000-0000-000000000004";
        String ikinciEsitZamanliId =
                "00000000-0000-0000-0000-000000000003";

        izinOlustur(
                personel,
                enBuyukEsitZamanliId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                tarih,
                tarih,
                new BigDecimal("1.0"),
                "owner-dorduncu",
                LocalDateTime.of(2026, 8, 24, 12, 0)
        );
        izinOlustur(
                personel,
                ikinciEsitZamanliId,
                1,
                TalepDurum.ONAYLANDI,
                tarih.plusDays(1),
                tarih.plusDays(1),
                new BigDecimal("1.0"),
                "owner-ucuncu",
                LocalDateTime.of(2026, 8, 24, 12, 0)
        );
        izinOlustur(
                personel,
                "00000000-0000-0000-0000-000000000002",
                1,
                TalepDurum.REDDEDILDI,
                tarih.plusDays(2),
                tarih.plusDays(2),
                new BigDecimal("1.0"),
                "owner-ikinci",
                LocalDateTime.of(2026, 8, 24, 11, 0)
        );
        izinOlustur(
                baskaPersonel,
                "00000000-0000-0000-0000-000000000099",
                1,
                TalepDurum.ONAY_BEKLIYOR,
                tarih,
                tarih,
                new BigDecimal("1.0"),
                "baska-personelin-gizli-notu",
                LocalDateTime.of(2026, 8, 24, 13, 0)
        );
        auditKaydiOlustur(
                "00000000-0000-0000-0000-000000000001",
                personel,
                "liste-oncesi-audit"
        );
        List<Map<String, Object>> oncekiKayitlar = izinKayitlariniGetir();
        List<Map<String, Object>> oncekiAuditKayitlari =
                auditKayitlariniGetir();
        long oncekiAuditSayisi = auditLogRepository.count();

        MvcResult result = kendiTalepleriniGetir(
                personel,
                RolAdi.PERSONEL,
                "?page=0&size=2"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(
                        enBuyukEsitZamanliId
                ))
                .andExpect(jsonPath("$.icerik[1].id").value(
                        ikinciEsitZamanliId
                ))
                .andExpect(jsonPath("$.icerik[0].personelId").doesNotExist())
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(2))
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        assertFalse(responseBody.contains("baska-personelin-gizli-notu"));
        assertFalse(responseBody.contains(baskaPersonel.getPersonelId()));
        assertEquals(oncekiKayitlar, izinKayitlariniGetir());
        assertEquals(oncekiAuditKayitlari, auditKayitlariniGetir());
        assertEquals(oncekiAuditSayisi, auditLogRepository.count());
    }

    @Test
    void kendiTalepleriDurumFiltresiVeParametreDogrulamasi()
            throws Exception {
        tumTalepDurumlariniHazirla();
        LocalDate tarih = sonrakiPazartesi();
        for (int sira = 1; sira <= 11; sira++) {
            TalepDurum durum = sira <= 4
                    ? TalepDurum.ONAY_BEKLIYOR
                    : TalepDurum.ONAYLANDI;
            izinOlustur(
                    personel,
                    String.format(
                            "00000000-0000-0000-0000-%012d",
                            sira
                    ),
                    1,
                    durum,
                    tarih.plusDays(sira),
                    tarih.plusDays(sira),
                    new BigDecimal("1.0"),
                    "owner-" + sira,
                    LocalDateTime.of(2026, 8, 24, 10, 0)
                            .plusMinutes(sira)
            );
        }
        Personel baskaPersonel = personelKaydet(
                "izin.filtre.baska.personel",
                personel.getRol(),
                departman,
                true,
                null
        );
        String baskaPersonelTalepId =
                "00000000-0000-0000-0000-000000000999";
        izinOlustur(
                baskaPersonel,
                baskaPersonelTalepId,
                1,
                TalepDurum.ONAY_BEKLIYOR,
                tarih,
                tarih,
                new BigDecimal("1.0"),
                "filtrede-gorunmemesi-gereken-yabanci-not",
                LocalDateTime.of(2026, 8, 24, 13, 0)
        );
        auditKaydiOlustur(
                "00000000-0000-0000-0000-000000000002",
                personel,
                "filtre-oncesi-audit"
        );
        List<Map<String, Object>> oncekiKayitlar = izinKayitlariniGetir();
        List<Map<String, Object>> oncekiAuditKayitlari =
                auditKayitlariniGetir();
        long oncekiAuditSayisi = auditLogRepository.count();

        MvcResult filtreResult = kendiTalepleriniGetir(
                personel,
                RolAdi.PERSONEL,
                "?durum=ONAY_BEKLIYOR"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(4))
                .andExpect(jsonPath("$.icerik[0].durum").value(
                        "ONAY_BEKLIYOR"
                ))
                .andExpect(jsonPath("$.icerik[3].durum").value(
                        "ONAY_BEKLIYOR"
                ))
                .andExpect(jsonPath("$.toplamKayit").value(4))
                .andReturn();

        String filtreResponseBody = filtreResult.getResponse()
                .getContentAsString();
        assertFalse(filtreResponseBody.contains(baskaPersonelTalepId));
        assertFalse(filtreResponseBody.contains(
                "filtrede-gorunmemesi-gereken-yabanci-not"
        ));
        assertFalse(filtreResponseBody.contains(
                baskaPersonel.getPersonelId()
        ));

        kendiTalepleriniGetir(personel, RolAdi.PERSONEL, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(10))
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(10))
                .andExpect(jsonPath("$.toplamKayit").value(11))
                .andExpect(jsonPath("$.toplamSayfa").value(2));

        gecersizKendiTalepleriQuery400Doner("?durum=BOZUK");
        gecersizKendiTalepleriQuery400Doner("?page=-1");
        gecersizKendiTalepleriQuery400Doner("?size=0");
        gecersizKendiTalepleriQuery400Doner("?size=101");

        assertEquals(oncekiKayitlar, izinKayitlariniGetir());
        assertEquals(oncekiAuditKayitlari, auditKayitlariniGetir());
        assertEquals(oncekiAuditSayisi, auditLogRepository.count());
    }

    @Test
    void kendiTalepleriRolVeAktorMatrisiUygular() throws Exception {
        Rol hrRolu = rolKaydet(2, RolAdi.HR);
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Rol adminRolu = rolKaydet(4, RolAdi.ADMIN);
        Personel hr = personelKaydet(
                "izin.liste.hr", hrRolu, departman, true, null
        );
        Personel kokYonetici = personelKaydet(
                "izin.liste.kok.yonetici",
                yoneticiRolu,
                departman,
                true,
                null
        );
        Personel yonetici = personelKaydet(
                "izin.liste.yonetici",
                yoneticiRolu,
                departman,
                true,
                kokYonetici
        );
        Personel admin = personelKaydet(
                "izin.liste.admin", adminRolu, departman, true, null
        );
        Personel pasifPersonel = personelKaydet(
                "izin.liste.pasif",
                personel.getRol(),
                departman,
                false,
                null
        );
        List<Map<String, Object>> oncekiKayitlar = izinKayitlariniGetir();
        List<Map<String, Object>> oncekiAuditKayitlari =
                auditKayitlariniGetir();
        long oncekiAuditSayisi = auditLogRepository.count();

        kendiTalepleriniGetir(personel, RolAdi.PERSONEL, "")
                .andExpect(status().isOk());
        kendiTalepleriniGetir(hr, RolAdi.HR, "")
                .andExpect(status().isOk());
        kendiTalepleriniGetir(yonetici, RolAdi.YONETICI, "")
                .andExpect(status().isOk());
        kendiTalepleriniGetir(kokYonetici, RolAdi.YONETICI, "")
                .andExpect(status().isForbidden());
        kendiTalepleriniGetir(admin, RolAdi.ADMIN, "")
                .andExpect(status().isForbidden());
        kendiTalepleriniGetir(pasifPersonel, RolAdi.PERSONEL, "")
                .andExpect(status().isForbidden());
        kendiTalepleriniGetir(personel, RolAdi.HR, "")
                .andExpect(status().isForbidden());

        String missingToken = jwtService.generateToken(
                UUID.randomUUID().toString(),
                RolAdi.PERSONEL
        );
        mockMvc.perform(
                        get("/api/leave-requests/my")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + missingToken
                                )
                )
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/leave-requests/my"))
                .andExpect(status().isUnauthorized());

        assertEquals(oncekiKayitlar, izinKayitlariniGetir());
        assertEquals(oncekiAuditKayitlari, auditKayitlariniGetir());
        assertEquals(oncekiAuditSayisi, auditLogRepository.count());
    }

    @Test
    void aktifPersonelTalepOlustururVeGuvenliDtoAlir() throws Exception {
        LocalDate baslangicTarihi = sonrakiPazartesi();
        LocalDate bitisTarihi = baslangicTarihi.plusDays(2);

        MvcResult result = talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, baslangicTarihi, bitisTarihi, "  Aile ziyareti  ")
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.izinTuru").value("Mazeret İzni"))
                .andExpect(jsonPath("$.baslangicTarihi").value(baslangicTarihi.toString()))
                .andExpect(jsonPath("$.bitisTarihi").value(bitisTarihi.toString()))
                .andExpect(jsonPath("$.isGunuSayisi").value(3.0))
                .andExpect(jsonPath("$.yoneticiyeNot").value("Aile ziyareti"))
                .andExpect(jsonPath("$.durum").value("ONAY_BEKLIYOR"))
                .andExpect(jsonPath("$.durumAdi").value("Onay bekliyor"))
                .andExpect(jsonPath("$.talepTarihi").value(
                        "2026-08-24T12:00:00"
                ))
                .andExpect(jsonPath("$.personelId").doesNotExist())
                .andExpect(jsonPath("$.islemYapanId").doesNotExist())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        String izinId = response.get("id").asText();
        UUID.fromString(izinId);
        LocalDateTime.parse(response.get("talepTarihi").asText());

        Map<String, Object> izinSatiri = jdbcTemplate.queryForMap(
                """
                        SELECT i.personel_id,
                               i.izin_turu_id,
                               i.is_gunu_sayisi,
                               d.durum_adi,
                               i.yoneticiye_not,
                               DATE_FORMAT(
                                   i.talep_tarihi,
                                   '%Y-%m-%dT%H:%i:%s'
                               ) AS talep_tarihi_exact,
                               i.islem_yapan_id,
                               i.islem_tarihi
                        FROM izin_bilgileri i
                        JOIN durum_turu d ON d.durum_id = i.izin_durum_id
                        WHERE i.izin_id = ?
                        """,
                izinId
        );

        assertEquals(1L, izinBilgileriRepository.count());
        assertEquals(personel.getPersonelId(), izinSatiri.get("personel_id"));
        assertEquals(1, izinSatiri.get("izin_turu_id"));
        assertEquals(
                0,
                new BigDecimal("3.0").compareTo((BigDecimal) izinSatiri.get("is_gunu_sayisi"))
        );
        assertEquals("ONAY_BEKLIYOR", izinSatiri.get("durum_adi"));
        assertEquals("Aile ziyareti", izinSatiri.get("yoneticiye_not"));
        assertEquals(
                "2026-08-24T12:00:00",
                izinSatiri.get("talep_tarihi_exact")
        );
        assertNull(izinSatiri.get("islem_yapan_id"));
        assertNull(izinSatiri.get("islem_tarihi"));
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void gercekTalepTekGuvenliAuditOlusturur() throws Exception {
        LocalDate baslangicTarihi = sonrakiPazartesi();
        LocalDate bitisTarihi = baslangicTarihi.plusDays(2);
        String yoneticiNotu = "Audit detayına girmemesi gereken yönetici notu";

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        1,
                        baslangicTarihi,
                        bitisTarihi,
                        yoneticiNotu
                )
        ).andExpect(status().isCreated());

        List<AuditLog> auditler = auditLogRepository.findAll();
        assertEquals(1, auditler.size());

        AuditLog audit = auditler.getFirst();
        assertEquals("IZIN_TALEBI_OLUSTURULDU", audit.getEylemTipi().name());
        assertEquals(
                personel.getPersonelId(),
                audit.getPersonel().getPersonelId()
        );
        assertEquals(
                personel.getPersonelId(),
                audit.getHedefPersonel().getPersonelId()
        );
        assertEquals(personel.getKullaniciAdi(), audit.getKullaniciAdi());
        assertEquals("İzin talebi oluşturuldu.", audit.getDetay());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHatasindaTalepVeAuditBirlikteRollbackOlur() {
        IzinTalepCreateRequest request = new IzinTalepCreateRequest(
                1,
                sonrakiPazartesi(),
                sonrakiPazartesi(),
                "Rollback testi"
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.talepOlusturVeHataFirlat(
                        personel.getPersonelId(),
                        request
                )
        );

        assertEquals(1L, rollbackTestService.transactionIcindekiTalepSayisi());
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(0L, izinBilgileriRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void persistenceHatasiGuvenli500DonerVeAuditOlusturmaz() throws Exception {
        String constraintAyrintisi =
                "Duplicate entry SQL constraint gizli ayrıntısı";
        DataIntegrityViolationException persistenceHatasi =
                new DataIntegrityViolationException(constraintAyrintisi);
        doThrow(persistenceHatasi)
                .when(izinBilgileriRepository)
                .saveAndFlush(any(IzinBilgileri.class));

        MvcResult result = talepOlustur(
                personel,
                RolAdi.PERSONEL,
                gecerliRequestBody()
        )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value(
                        "Beklenmeyen bir hata oluştu."
                ))
                .andExpect(jsonPath("$.path").value("/api/leave-requests"))
                .andReturn();

        assertFalse(
                result.getResponse()
                        .getContentAsString()
                        .contains(constraintAyrintisi)
        );
        assertEquals(0L, izinBilgileriRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bekleyenTalepBakiyeyiRezerveEder() throws Exception {
        personel.setYillikIzinHakki(14);
        personel = personelRepository.saveAndFlush(personel);
        int yillikIzinTuruId = yillikIzinTuruKaydet();
        tumTalepDurumlariniHazirla();
        LocalDate ilkPazartesi = sonrakiPazartesi();

        bakiyeKayitlariniOlustur(yillikIzinTuruId, ilkPazartesi);
        List<Map<String, Object>> oncekiKayitlar = izinKayitlariniGetir();

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        yillikIzinTuruId,
                        ilkPazartesi.plusDays(21),
                        ilkPazartesi.plusDays(30),
                        null
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Yetersiz izin bakiyesi."
                ));

        assertEquals(oncekiKayitlar, izinKayitlariniGetir());
        assertEquals(0L, auditLogRepository.count());

        izinVeTatilKayitlariniTemizle();
        bakiyeKayitlariniOlustur(yillikIzinTuruId, ilkPazartesi);
        yarimGunTatilKaydet(ilkPazartesi.plusDays(30));

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        yillikIzinTuruId,
                        ilkPazartesi.plusDays(21),
                        ilkPazartesi.plusDays(30),
                        null
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Yetersiz izin bakiyesi."
                ));

        assertEquals(4L, izinBilgileriRepository.count());
        assertEquals(0L, auditLogRepository.count());

        izinVeTatilKayitlariniTemizle();
        bakiyeKayitlariniOlustur(yillikIzinTuruId, ilkPazartesi);

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        yillikIzinTuruId,
                        ilkPazartesi.plusDays(21),
                        ilkPazartesi.plusDays(29),
                        null
                )
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isGunuSayisi").value(7.0));

        assertEquals(5L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void yillikHaktanDusmeyenTurBakiyedenBagimsizdir() throws Exception {
        personel.setYillikIzinHakki(0);
        personel = personelRepository.saveAndFlush(personel);
        int yillikIzinTuruId = yillikIzinTuruKaydet();
        tumTalepDurumlariniHazirla();
        LocalDate ilkPazartesi = sonrakiPazartesi();

        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.ONAYLANDI,
                ilkPazartesi,
                ilkPazartesi.plusDays(3),
                new BigDecimal("20.0")
        );
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.ONAY_BEKLIYOR,
                ilkPazartesi.plusDays(7),
                ilkPazartesi.plusDays(9),
                new BigDecimal("20.0")
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        1,
                        ilkPazartesi.plusDays(21),
                        ilkPazartesi.plusDays(21),
                        null
                )
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.izinTuru").value("Mazeret İzni"));

        assertEquals(3L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void etkiliIsGunuCakismasi409VeAtomiktir() throws Exception {
        int yillikIzinTuruId = yillikIzinTuruKaydet();
        tumTalepDurumlariniHazirla();
        LocalDate ilkPazartesi = sonrakiPazartesi();

        izinOlustur(
                1,
                TalepDurum.ONAY_BEKLIYOR,
                ilkPazartesi,
                ilkPazartesi.plusDays(2),
                new BigDecimal("3.0")
        );
        List<Map<String, Object>> oncekiKayitlar = izinKayitlariniGetir();

        cakisanTalebinReddedildiginiDogrula(
                1,
                ilkPazartesi.plusDays(1),
                ilkPazartesi.plusDays(1)
        );
        assertEquals(oncekiKayitlar, izinKayitlariniGetir());

        izinVeTatilKayitlariniTemizle();
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.ONAYLANDI,
                ilkPazartesi.plusDays(7),
                ilkPazartesi.plusDays(8),
                new BigDecimal("2.0")
        );

        cakisanTalebinReddedildiginiDogrula(
                1,
                ilkPazartesi.plusDays(8),
                ilkPazartesi.plusDays(9)
        );

        izinVeTatilKayitlariniTemizle();
        izinOlustur(
                1,
                TalepDurum.REDDEDILDI,
                ilkPazartesi.plusDays(14),
                ilkPazartesi.plusDays(15),
                new BigDecimal("2.0")
        );
        izinOlustur(
                1,
                TalepDurum.IPTAL_EDILDI,
                ilkPazartesi.plusDays(14),
                ilkPazartesi.plusDays(15),
                new BigDecimal("2.0")
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        1,
                        ilkPazartesi.plusDays(14),
                        ilkPazartesi.plusDays(15),
                        null
                )
        ).andExpect(status().isCreated());

        izinVeTatilKayitlariniTemizle();
        LocalDate yarimGun = ilkPazartesi.plusDays(21);
        yarimGunTatilKaydet(yarimGun);
        izinOlustur(
                1,
                TalepDurum.ONAY_BEKLIYOR,
                yarimGun,
                yarimGun,
                new BigDecimal("0.5")
        );

        cakisanTalebinReddedildiginiDogrula(
                1,
                yarimGun,
                yarimGun.plusDays(1)
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void baskaPersonelinTalepleriBakiyeyeVeCakismayaKatilmaz()
            throws Exception {
        personel.setYillikIzinHakki(1);
        personel = personelRepository.saveAndFlush(personel);
        int yillikIzinTuruId = yillikIzinTuruKaydet();
        tumTalepDurumlariniHazirla();
        Personel baskaPersonel = personelKaydet(
                "izin.bakiye.cakisma.baska",
                personel.getRol(),
                departman,
                true,
                null
        );
        LocalDate ilkPazartesi = sonrakiPazartesi();

        izinOlustur(
                baskaPersonel,
                UUID.randomUUID().toString(),
                yillikIzinTuruId,
                TalepDurum.ONAY_BEKLIYOR,
                ilkPazartesi.plusDays(14),
                ilkPazartesi.plusDays(18),
                new BigDecimal("20.0"),
                null,
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );
        izinOlustur(
                baskaPersonel,
                UUID.randomUUID().toString(),
                1,
                TalepDurum.ONAYLANDI,
                ilkPazartesi,
                ilkPazartesi,
                new BigDecimal("1.0"),
                null,
                LocalDateTime.of(2026, 8, 24, 10, 1)
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        yillikIzinTuruId,
                        ilkPazartesi,
                        ilkPazartesi,
                        null
                )
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isGunuSayisi").value(1.0));

        Integer aktorTalepSayisi = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM izin_bilgileri WHERE personel_id = ?",
                Integer.class,
                personel.getPersonelId()
        );
        assertEquals(1, aktorTalepSayisi);
        assertEquals(3L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void yalnizCalisilmayanGunKesisimiCakismaSayilmaz() throws Exception {
        tumTalepDurumlariniHazirla();
        LocalDate ilkPazartesi = sonrakiPazartesi();
        LocalDate cuma = ilkPazartesi.plusDays(4);

        izinOlustur(
                1,
                TalepDurum.ONAY_BEKLIYOR,
                cuma,
                cuma.plusDays(2),
                new BigDecimal("1.0")
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, cuma.plusDays(1), cuma.plusDays(3), null)
        ).andExpect(status().isCreated());

        assertEquals(2L, izinBilgileriRepository.count());

        izinVeTatilKayitlariniTemizle();
        LocalDate tamGunTatil = ilkPazartesi.plusDays(15);
        tamGunTatilKaydet(tamGunTatil);
        izinOlustur(
                1,
                TalepDurum.ONAYLANDI,
                tamGunTatil.minusDays(1),
                tamGunTatil,
                new BigDecimal("1.0")
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, tamGunTatil, tamGunTatil.plusDays(1), null)
        ).andExpect(status().isCreated());

        assertEquals(2L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void aktifHrYoneticisiOlmadanTalepOlusturur() throws Exception {
        Rol hrRolu = rolKaydet(2, RolAdi.HR);
        Personel hr = personelKaydet("izin.hr", hrRolu, departman, true, null);

        talepOlustur(hr, RolAdi.HR, gecerliRequestBody())
                .andExpect(status().isCreated());

        assertEquals(1L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void uygunUstYoneticisiOlanYoneticiTalepOlusturur() throws Exception {
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Personel ustYonetici = personelKaydet(
                "izin.ust.yonetici", yoneticiRolu, departman, true, null
        );
        Personel yonetici = personelKaydet(
                "izin.yonetici", yoneticiRolu, departman, true, ustYonetici
        );

        talepOlustur(yonetici, RolAdi.YONETICI, gecerliRequestBody())
                .andExpect(status().isCreated());

        assertEquals(1L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void kokYoneticiIzinTuruLookupundanOnceReddedilir() throws Exception {
        Rol yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        Personel kokYonetici = personelKaydet(
                "izin.kok.yonetici", yoneticiRolu, departman, true, null
        );

        talepOlustur(
                kokYonetici,
                RolAdi.YONETICI,
                requestBody(999, sonrakiPazartesi(), sonrakiPazartesi(), null)
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertYanEtkiYok();
    }

    @Test
    void adminTalepOlusturamaz() throws Exception {
        Rol adminRolu = rolKaydet(4, RolAdi.ADMIN);
        Personel admin = personelKaydet("izin.admin", adminRolu, departman, true, null);

        talepOlustur(admin, RolAdi.ADMIN, gecerliRequestBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertYanEtkiYok();
    }

    @Test
    void pasifAktorIzinTuruLookupundanOnceReddedilir() throws Exception {
        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(999, sonrakiPazartesi(), sonrakiPazartesi(), null)
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertYanEtkiYok();
    }

    @Test
    void pasifAktorBusinessValidationdanOnceReddedilir() throws Exception {
        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        1,
                        SABIT_BUGUN.minusDays(1),
                        SABIT_BUGUN,
                        "  " + "a".repeat(1001) + "  "
                )
        )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertYanEtkiYok();
    }

    @Test
    void veritabanindaOlmayanPrincipal404Doner() throws Exception {
        String token = jwtService.generateToken(UUID.randomUUID().toString(), RolAdi.PERSONEL);

        mockMvc.perform(
                        post("/api/leave-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody(
                                        999, sonrakiPazartesi(), sonrakiPazartesi(), null
                                ))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        assertYanEtkiYok();
    }

    @Test
    void jwtVeVeritabaniRoluUyusmazsa403Doner() throws Exception {
        talepOlustur(personel, RolAdi.HR, gecerliRequestBody())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertYanEtkiYok();
    }

    @Test
    void unauthenticatedIstek401Doner() throws Exception {
        mockMvc.perform(
                        post("/api/leave-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliRequestBody())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        assertYanEtkiYok();
    }

    @Test
    void bodyYoksaVeyaBozuksa400Doner() throws Exception {
        mockMvc.perform(
                        post("/api/leave-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/leave-requests")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"izinTuruId\":")
                                .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                )
                .andExpect(status().isBadRequest());

        assertYanEtkiYok();
    }

    @Test
    void izinTuruIdNullSifirVeyaNegatifse400Doner() throws Exception {
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(null, sonrakiPazartesi(), sonrakiPazartesi(), null)
        ).andExpect(status().isBadRequest());
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(0, sonrakiPazartesi(), sonrakiPazartesi(), null)
        ).andExpect(status().isBadRequest());
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(-1, sonrakiPazartesi(), sonrakiPazartesi(), null)
        ).andExpect(status().isBadRequest());

        assertYanEtkiYok();
    }

    @Test
    void baslangicVeyaBitisTarihiNullsa400Doner() throws Exception {
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, null, sonrakiPazartesi(), null)
        ).andExpect(status().isBadRequest());
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, sonrakiPazartesi(), null, null)
        ).andExpect(status().isBadRequest());

        assertYanEtkiYok();
    }

    @Test
    void gecmisVeyaGecersizTarihAraligi400Doner() throws Exception {
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(999, SABIT_BUGUN.minusDays(1), SABIT_BUGUN, null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz tarih aralığı."));
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, SABIT_BUGUN.plusDays(2), SABIT_BUGUN.plusDays(1), null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz tarih aralığı."));
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, SABIT_BUGUN, SABIT_BUGUN.plusDays(366), null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("İzin talebi en fazla 366 gün olabilir."));

        assertYanEtkiYok();
    }

    @Test
    void bugunVeDahil366GunlukAralikKabulEdilir() throws Exception {
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, SABIT_BUGUN, SABIT_BUGUN.plusDays(365), null)
        ).andExpect(status().isCreated());

        assertEquals(1L, izinBilgileriRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void sifirIsGunuTalebi400Doner() throws Exception {
        LocalDate cumartesi = SABIT_BUGUN.with(
                TemporalAdjusters.next(DayOfWeek.SATURDAY)
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, cumartesi, cumartesi.plusDays(1), null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "İzin talebi en az bir iş günü içermelidir."
                ));

        tamGunTatilKaydet(SABIT_BUGUN.plusDays(1));
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, SABIT_BUGUN.plusDays(1), SABIT_BUGUN.plusDays(1), null)
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "İzin talebi en az bir iş günü içermelidir."
                ));

        assertYanEtkiYok();
    }

    @Test
    void bilinmeyenPozitifIzinTuru404Doner() throws Exception {
        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(999, sonrakiPazartesi(), sonrakiPazartesi(), null)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("İzin türü bulunamadı."));

        assertYanEtkiYok();
    }

    @Test
    void bilinmeyenIzinTuruEksikDurumdanOnce404Doner() throws Exception {
        jdbcTemplate.update("DELETE FROM durum_turu");

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(999, sonrakiPazartesi(), sonrakiPazartesi(), null)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("İzin türü bulunamadı."));

        assertYanEtkiYok();
    }

    @Test
    void bilinmeyenDurum404Doner() throws Exception {
        jdbcTemplate.update("DELETE FROM durum_turu");

        talepOlustur(personel, RolAdi.PERSONEL, gecerliRequestBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("İzin durumu bulunamadı."));

        assertYanEtkiYok();
    }

    @Test
    void eksikDurumSifirIsGunuKontrolundenOnce404Doner() throws Exception {
        jdbcTemplate.update("DELETE FROM durum_turu");
        LocalDate cumartesi = SABIT_BUGUN.with(
                TemporalAdjusters.next(DayOfWeek.SATURDAY)
        );

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, cumartesi, cumartesi.plusDays(1), null)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("İzin durumu bulunamadı."));

        assertYanEtkiYok();
    }

    @Test
    void trimSonrasiBosNotNullKaydedilirVeNullDoner() throws Exception {
        MvcResult result = talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(1, sonrakiPazartesi(), sonrakiPazartesi(), "   \t  ")
        )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(response.get("yoneticiyeNot").isNull());
        assertNull(jdbcTemplate.queryForObject(
                "SELECT yoneticiye_not FROM izin_bilgileri",
                String.class
        ));
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void bosluklarlaCevriliTrimSonrasiTam1000KarakterNotKabulEdilir()
            throws Exception {
        String normalizeNot = "a".repeat(1000);

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        1,
                        sonrakiPazartesi(),
                        sonrakiPazartesi(),
                        "  " + normalizeNot + "  "
                )
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yoneticiyeNot").value(normalizeNot));

        assertEquals(
                normalizeNot,
                jdbcTemplate.queryForObject(
                        "SELECT yoneticiye_not FROM izin_bilgileri",
                        String.class
                )
        );
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void trimSonrasiNotUzunluguDogrulanir() throws Exception {
        String uzunNot = "a".repeat(1001);

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        999,
                        sonrakiPazartesi(),
                        sonrakiPazartesi(),
                        "  " + uzunNot + "  "
                )
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Yönetici notu en fazla 1000 karakter olabilir."
                ));

        assertYanEtkiYok();
    }

    private ResultActions talepOlustur(
            Personel aktor,
            RolAdi jwtRolu,
            String body
    ) throws Exception {
        return mockMvc.perform(
                post("/api/leave-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu))
        );
    }

    private ResultActions kendiTalepleriniGetir(
            Personel aktor,
            RolAdi jwtRolu,
            String query
    ) throws Exception {
        return mockMvc.perform(
                get("/api/leave-requests/my" + query)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(aktor, jwtRolu)
                        )
        );
    }

    private void gecersizKendiTalepleriQuery400Doner(String query)
            throws Exception {
        String responseBody = kendiTalepleriniGetir(
                personel,
                RolAdi.PERSONEL,
                query
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz istek"))
                .andExpect(jsonPath("$.path").value(
                        "/api/leave-requests/my"
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(responseBody.contains("kendiTalepleriniGetir"));
        assertFalse(responseBody.contains("must be greater"));
        assertFalse(responseBody.contains("must be less"));
    }

    private String gecerliRequestBody() throws Exception {
        return requestBody(1, sonrakiPazartesi(), sonrakiPazartesi(), null);
    }

    private String requestBody(
            Integer izinTuruId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            String yoneticiyeNot
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("izinTuruId", izinTuruId);
        body.put("baslangicTarihi", baslangicTarihi);
        body.put("bitisTarihi", bitisTarihi);
        body.put("yoneticiyeNot", yoneticiyeNot);
        return objectMapper.writeValueAsString(body);
    }

    private String bearer(Personel aktor, RolAdi rolAdi) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rolAdi);
    }

    private ResultActions talebiIptalEt(
            Personel aktor,
            RolAdi rolAdi,
            String talepId
    ) throws Exception {
        return mockMvc.perform(patch(
                        "/api/leave-requests/{talepId}/cancel",
                        talepId
                )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(aktor, rolAdi)
                        ));
    }

    private String izinDurumunuGetir(String talepId) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT d.durum_adi
                        FROM izin_bilgileri i
                        JOIN durum_turu d
                          ON d.durum_id = i.izin_durum_id
                        WHERE i.izin_id = ?
                        """,
                String.class,
                talepId
        );
    }

    private void assertYanEtkiYok() {
        assertEquals(0L, izinBilgileriRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    private int yillikIzinTuruKaydet() {
        jdbcTemplate.update(
                "INSERT INTO izin_turu (izin_turu_adi, yillik_haktan_duser_mi) VALUES (?, ?)",
                "Yıllık İzin",
                true
        );
        return jdbcTemplate.queryForObject(
                "SELECT izin_turu_id FROM izin_turu WHERE izin_turu_adi = ?",
                Integer.class,
                "Yıllık İzin"
        );
    }

    private void tumTalepDurumlariniHazirla() {
        for (TalepDurum durum : TalepDurum.values()) {
            Integer adet = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM durum_turu WHERE durum_adi = ?",
                    Integer.class,
                    durum.name()
            );
            if (adet != null && adet == 0) {
                jdbcTemplate.update(
                        "INSERT INTO durum_turu (durum_adi) VALUES (?)",
                        durum.name()
                );
            }
        }
    }

    private void bakiyeKayitlariniOlustur(
            int yillikIzinTuruId,
            LocalDate ilkPazartesi
    ) {
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.ONAYLANDI,
                ilkPazartesi,
                ilkPazartesi.plusDays(3),
                new BigDecimal("4.0")
        );
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.ONAY_BEKLIYOR,
                ilkPazartesi.plusDays(7),
                ilkPazartesi.plusDays(9),
                new BigDecimal("3.0")
        );
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.REDDEDILDI,
                ilkPazartesi.plusDays(40),
                ilkPazartesi.plusDays(40),
                new BigDecimal("50.0")
        );
        izinOlustur(
                yillikIzinTuruId,
                TalepDurum.IPTAL_EDILDI,
                ilkPazartesi.plusDays(42),
                ilkPazartesi.plusDays(42),
                new BigDecimal("50.0")
        );
    }

    private void izinOlustur(
            int izinTuruId,
            TalepDurum durum,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            BigDecimal isGunuSayisi
    ) {
        izinOlustur(
                personel,
                UUID.randomUUID().toString(),
                izinTuruId,
                durum,
                baslangicTarihi,
                bitisTarihi,
                isGunuSayisi,
                null,
                LocalDateTime.of(2026, 8, 24, 10, 0)
        );
    }

    private void izinOlustur(
            Personel talepSahibi,
            String izinId,
            int izinTuruId,
            TalepDurum durum,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            BigDecimal isGunuSayisi,
            String yoneticiyeNot,
            LocalDateTime talepTarihi
    ) {
        Integer durumId = jdbcTemplate.queryForObject(
                "SELECT durum_id FROM durum_turu WHERE durum_adi = ?",
                Integer.class,
                durum.name()
        );
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
                            yoneticiye_not,
                            talep_tarihi
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                izinId,
                talepSahibi.getPersonelId(),
                izinTuruId,
                baslangicTarihi,
                bitisTarihi,
                isGunuSayisi,
                durumId,
                yoneticiyeNot,
                talepTarihi
        );
    }

    private void cakisanTalebinReddedildiginiDogrula(
            int izinTuruId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) throws Exception {
        long oncekiKayitSayisi = izinBilgileriRepository.count();

        talepOlustur(
                personel,
                RolAdi.PERSONEL,
                requestBody(
                        izinTuruId,
                        baslangicTarihi,
                        bitisTarihi,
                        null
                )
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor."
                ));

        assertEquals(oncekiKayitSayisi, izinBilgileriRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    private List<Map<String, Object>> izinKayitlariniGetir() {
        return jdbcTemplate.queryForList(
                """
                        SELECT izin_id,
                               personel_id,
                               izin_turu_id,
                               baslangic_tarihi,
                               bitis_tarihi,
                               is_gunu_sayisi,
                               izin_durum_id,
                               yoneticiye_not,
                               talep_tarihi,
                               islem_yapan_id,
                               islem_tarihi,
                               karar_notu
                        FROM izin_bilgileri
                        ORDER BY izin_id
                        """
        );
    }

    private void auditKaydiOlustur(
            String logId,
            Personel aktor,
            String detay
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_log (
                            log_id,
                            personel_id,
                            eylem_tipi,
                            kullanici_adi,
                            detay,
                            olusma_zamani
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                logId,
                aktor.getPersonelId(),
                "LOGIN_BASARILI",
                aktor.getKullaniciAdi(),
                detay,
                LocalDateTime.of(2026, 8, 24, 9, 0)
        );
    }

    private List<Map<String, Object>> auditKayitlariniGetir() {
        return jdbcTemplate.queryForList(
                """
                        SELECT log_id,
                               personel_id,
                               eylem_tipi,
                               kullanici_adi,
                               detay,
                               hedef_personel_id,
                               olusma_zamani
                        FROM audit_log
                        ORDER BY log_id
                        """
        );
    }

    private void izinVeTatilKayitlariniTemizle() {
        izinBilgileriRepository.deleteAll();
        izinBilgileriRepository.flush();
        auditLogRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM tatil");
    }

    private void tamGunTatilKaydet(LocalDate tarih) {
        tatilKaydet(tarih, "TAM_GUN");
    }

    private void yarimGunTatilKaydet(LocalDate tarih) {
        tatilKaydet(tarih, "YARIM_GUN");
    }

    private void tatilKaydet(LocalDate tarih, String gunSuresi) {
        jdbcTemplate.update(
                """
                        INSERT INTO tatil (
                            ad,
                            tarih,
                            bitis_tarihi,
                            kategori,
                            gun_suresi,
                            version
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """,
                gunSuresi + " Şirket Tatili",
                tarih,
                tarih,
                "SIRKET",
                gunSuresi,
                0
        );
    }

    private LocalDate sonrakiPazartesi() {
        return SABIT_BUGUN
                .plusDays(1)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
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
            Rol rol,
            Departman personelDepartmani,
            boolean aktif,
            Personel yonetici
    ) {
        Personel kaydedilecek = new Personel();
        kaydedilecek.setKullaniciAdi(kullaniciAdi);
        kaydedilecek.setParolaHash("test-hash");
        kaydedilecek.setAdi("İzin");
        kaydedilecek.setSoyadi("Personeli");
        kaydedilecek.setDogumTarihi(LocalDate.of(1995, 1, 1));
        kaydedilecek.setePosta(kullaniciAdi + "@test.local");
        kaydedilecek.setDepartman(personelDepartmani);
        kaydedilecek.setRol(rol);
        kaydedilecek.setCalisiyorMu(aktif);
        kaydedilecek.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        kaydedilecek.setYillikIzinHakki(20);
        kaydedilecek.setProfilFoto("def_pfp.png");
        kaydedilecek.setParolaDegistirilmeliMi(false);
        kaydedilecek.setYonetici(yonetici);
        return personelRepository.saveAndFlush(kaydedilecek);
    }

    @TestConfiguration
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

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        @Primary
        IzinTalepRollbackTestService izinTalepRollbackTestService(
                IzinTalepService izinTalepService,
                IzinBilgileriRepository izinBilgileriRepository,
                AuditLogRepository auditLogRepository
        ) {
            return new IzinTalepRollbackTestService(
                    izinTalepService,
                    izinBilgileriRepository,
                    auditLogRepository
            );
        }
    }

    static class IzinTalepRollbackTestService {

        private final IzinTalepService izinTalepService;
        private final IzinBilgileriRepository izinBilgileriRepository;
        private final AuditLogRepository auditLogRepository;
        private long transactionIcindekiTalepSayisi;
        private long transactionIcindekiAuditSayisi;
        private String transactionIcindekiIptalDurumu;

        IzinTalepRollbackTestService(
                IzinTalepService izinTalepService,
                IzinBilgileriRepository izinBilgileriRepository,
                AuditLogRepository auditLogRepository
        ) {
            this.izinTalepService = izinTalepService;
            this.izinBilgileriRepository = izinBilgileriRepository;
            this.auditLogRepository = auditLogRepository;
        }

        @Transactional
        public void talepOlusturVeHataFirlat(
                String aktorId,
                IzinTalepCreateRequest request
        ) {
            izinTalepService.talepOlustur(aktorId, request);
            transactionIcindekiTalepSayisi = izinBilgileriRepository.count();
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        @Transactional
        public void talebiIptalEtVeHataFirlat(
                String aktorId,
                String talepId
        ) {
            izinTalepService.talebiIptalEt(aktorId, talepId);
            transactionIcindekiIptalDurumu =
                    izinBilgileriRepository.findById(talepId)
                            .orElseThrow()
                            .getIzinDurumId()
                            .getDurumKodu()
                            .name();
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        long transactionIcindekiTalepSayisi() {
            return transactionIcindekiTalepSayisi;
        }

        long transactionIcindekiAuditSayisi() {
            return transactionIcindekiAuditSayisi;
        }

        String transactionIcindekiIptalDurumu() {
            return transactionIcindekiIptalDurumu;
        }
    }
}
