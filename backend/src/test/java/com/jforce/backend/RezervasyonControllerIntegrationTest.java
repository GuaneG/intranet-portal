package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.dto.request.RezervasyonRequest;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.RezervasyonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(RezervasyonControllerIntegrationTest.SabitClockConfig.class)
class RezervasyonControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate BASLANGIC = LocalDate.of(2026, 9, 1);
    private static final LocalDate BITIS = LocalDate.of(2026, 9, 30);
    private static final LocalDate SABIT_BUGUN = LocalDate.of(2026, 9, 10);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private RezervasyonRollbackTestService rollbackTestService;

    private Departman departman;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM rezervasyon");
        jdbcTemplate.update("DELETE FROM audit_log");
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM tatil");
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM oda");

        for (RolAdi rolAdi : RolAdi.values()) {
            rolKaydet(rolAdi);
        }
        departman = departmanKaydet("Bilgi Teknolojileri");
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifRollerOdalariGuvenliGorur(RolAdi rolAdi) throws Exception {
        Personel aktor = personelKaydet("aktor." + rolAdi.name().toLowerCase(), rolAdi, true);
        odaKaydet("Küçük Oda", 6);
        odaKaydet("Büyük Salon", 12);

        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rolAdi)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].ad").value("Büyük Salon"))
                .andExpect(jsonPath("$[0].kapasite").value(12))
                .andExpect(jsonPath("$[1].ad").value("Küçük Oda"))
                .andExpect(jsonPath("$[1].kapasite").value(6))
                .andExpect(jsonPath("$[0].odaAd").doesNotExist())
                .andExpect(jsonPath("$[0].odaKapasite").doesNotExist());

        assertEquals(0, auditSayisi());
        assertEquals(0, rezervasyonSayisi());
    }

    @Test
    void aktifPersonelRezervasyonTakviminiGorur() throws Exception {
        Personel aktor = personelKaydet("aktif.personel", RolAdi.PERSONEL, true);
        Personel olusturan = personelKaydet("olusturan.personel", RolAdi.HR, true);
        int buyukOda = odaKaydet("Büyük Salon", 12);
        int kucukOda = odaKaydet("Küçük Oda", 6);

        String ilk = rezervasyonKaydet(
                olusturan, buyukOda, BASLANGIC.plusDays(2), 9, 10, "Gizli İlk", 5, "AKTIF"
        );
        String ikinci = rezervasyonKaydet(
                olusturan, kucukOda, BASLANGIC.plusDays(2), 9, 11, "Gizli İkinci", 4, "AKTIF"
        );
        rezervasyonKaydet(
                olusturan, buyukOda, BASLANGIC.plusDays(1), 9, 10, "İptal", 2, "IPTAL_EDILDI"
        );
        rezervasyonKaydet(
                olusturan, buyukOda, BITIS.plusDays(1), 9, 10, "Dışarıda", 2, "AKTIF"
        );

        int rezervasyonOnce = rezervasyonSayisi();
        int auditOnce = auditSayisi();

        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BASLANGIC.toString())
                        .param("bitisTarihi", BITIS.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(ilk))
                .andExpect(jsonPath("$[1].id").value(ikinci))
                .andExpect(jsonPath("$[0].oda.id").value(buyukOda))
                .andExpect(jsonPath("$[0].oda.ad").value("Büyük Salon"))
                .andExpect(jsonPath("$[0].oda.kapasite").value(12))
                .andExpect(jsonPath("$[0].tarih").value("2026-09-03"))
                .andExpect(jsonPath("$[0].baslangicSaat").value(9))
                .andExpect(jsonPath("$[0].bitisSaat").value(10))
                .andExpect(jsonPath("$[0].olusturan.ad").value("Olusturan"))
                .andExpect(jsonPath("$[0].olusturan.soyad").value("Personel"))
                .andExpect(jsonPath("$[0].benMiOlusturdum").value(false))
                .andExpect(jsonPath("$[0].baslik").value((Object) null))
                .andExpect(jsonPath("$[0].katilimciSayisi").value((Object) null))
                .andExpect(jsonPath("$[0].olusturan.id").doesNotExist())
                .andExpect(jsonPath("$[0].olusturan.kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$[0].olusturan.ePosta").doesNotExist())
                .andExpect(jsonPath("$[0].durum").doesNotExist());

        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BASLANGIC.toString())
                        .param("bitisTarihi", BITIS.toString())
                        .param("odaId", Integer.toString(kucukOda))
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ikinci));

        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BASLANGIC.toString())
                        .param("bitisTarihi", BITIS.toString())
                        .param("odaId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertEquals(rezervasyonOnce, rezervasyonSayisi());
        assertEquals(auditOnce, auditSayisi());
    }

    @Test
    void takvimBaslikGizliligiDbRoluyleUygulanir() throws Exception {
        Personel owner = personelKaydet("owner.personel", RolAdi.PERSONEL, true);
        Personel dbAdmin = personelKaydet("db.admin", RolAdi.ADMIN, true);
        Personel sahteJwtAdmin = personelKaydet("sahte.admin", RolAdi.HR, true);
        int odaId = odaKaydet("Büyük Salon", 12);
        String rezervasyonId = rezervasyonKaydet(
                owner, odaId, BASLANGIC.plusDays(3), 10, 12, "Yönetim Toplantısı", 7, "AKTIF"
        );

        takvimGizlilikDogrula(owner, RolAdi.PERSONEL, rezervasyonId, true);
        takvimGizlilikDogrula(dbAdmin, RolAdi.PERSONEL, rezervasyonId, true);
        takvimGizlilikDogrula(sahteJwtAdmin, RolAdi.ADMIN, rezervasyonId, false);
    }

    @Test
    void pasifVeEksikAktorKontrolluReddedilirTokensizIstek401Doner() throws Exception {
        Personel pasif = personelKaydet("pasif.personel", RolAdi.PERSONEL, false);

        mockMvc.perform(get("/api/rooms")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasif, RolAdi.PERSONEL)))
                .andExpect(status().isForbidden());

        String eksikToken = jwtService.generateToken(UUID.randomUUID().toString(), RolAdi.PERSONEL);
        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BASLANGIC.toString())
                        .param("bitisTarihi", BITIS.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + eksikToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        mockMvc.perform(get("/api/rooms"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void gecersizTakvimParametreleri400Doner() throws Exception {
        Personel aktor = personelKaydet("parametre.aktor", RolAdi.HR, true);
        String token = bearer(aktor, RolAdi.HR);

        mockMvc.perform(get("/api/reservations")
                        .param("bitisTarihi", BITIS.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", "01-09-2026")
                        .param("bitisTarihi", BITIS.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BITIS.toString())
                        .param("bitisTarihi", BASLANGIC.toString())
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", "2026-01-01")
                        .param("bitisTarihi", "2027-01-02")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        for (String odaId : new String[]{"0", "-1", "metin"}) {
            mockMvc.perform(get("/api/reservations")
                            .param("baslangicTarihi", BASLANGIC.toString())
                            .param("bitisTarihi", BITIS.toString())
                            .param("odaId", odaId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifRollerKendiRezervasyonunuOlusturur(RolAdi rolAdi) throws Exception {
        Personel aktor = personelKaydet(
                "create." + rolAdi.name().toLowerCase(),
                rolAdi,
                true
        );
        int odaId = odaKaydet("Büyük Salon", 12);

        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(requestBody(odaId, SABIT_BUGUN.plusDays(1), 9, 12,
                                "  Proje Toplantısı  ", 12))
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rolAdi)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(
                        "/api/reservations/[0-9a-f-]{36}"
                )))
                .andExpect(jsonPath("$.oda.id").value(odaId))
                .andExpect(jsonPath("$.tarih").value("2026-09-11"))
                .andExpect(jsonPath("$.baslangicSaat").value(9))
                .andExpect(jsonPath("$.bitisSaat").value(12))
                .andExpect(jsonPath("$.olusturan.ad").value("Create"))
                .andExpect(jsonPath("$.olusturan.soyad")
                        .value(basHarfiBuyut(rolAdi.name().toLowerCase())))
                .andExpect(jsonPath("$.benMiOlusturdum").value(true))
                .andExpect(jsonPath("$.baslik").value("Proje Toplantısı"))
                .andExpect(jsonPath("$.katilimciSayisi").value(12));

        assertEquals(1, rezervasyonSayisi());
        assertEquals(aktor.getPersonelId(), jdbcTemplate.queryForObject(
                "SELECT olusturan_id FROM rezervasyon",
                String.class
        ));
        assertEquals("AKTIF", jdbcTemplate.queryForObject(
                "SELECT durum FROM rezervasyon",
                String.class
        ));
        assertEquals(1, auditSayisi());
    }

    @Test
    void basariliOlusturmaTekGuvenliAuditOlusturur() throws Exception {
        Personel aktor = personelKaydet("audit.owner", RolAdi.PERSONEL, true);
        int odaId = odaKaydet("Büyük Salon", 12);

        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(requestBody(
                                odaId,
                                SABIT_BUGUN.plusDays(1),
                                9,
                                10,
                                "Çok Gizli Proje Toplantısı",
                                4
                        ))
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isCreated());

        var audit = jdbcTemplate.queryForMap(
                """
                SELECT eylem_tipi, personel_id, hedef_personel_id,
                       kullanici_adi, detay
                FROM audit_log
                """
        );
        assertEquals(AuditEylem.REZERVASYON_OLUSTURULDU.name(),
                audit.get("eylem_tipi"));
        assertEquals(aktor.getPersonelId(), audit.get("personel_id"));
        assertEquals(aktor.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(aktor.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals("Rezervasyon oluşturuldu.", audit.get("detay"));

        String detay = (String) audit.get("detay");
        assertFalse(detay.contains("Çok Gizli Proje Toplantısı"));
        assertFalse(detay.contains(Integer.toString(odaId)));
        assertFalse(detay.contains("2026-09-11"));
        assertFalse(detay.contains("09:00"));
        assertFalse(detay.contains("4"));
    }

    @Test
    void disTransactionHataVerirseRezervasyonVeAuditBirlikteRollbackOlur() {
        Personel aktor = personelKaydet("rollback.owner", RolAdi.HR, true);
        int odaId = odaKaydet("Küçük Oda", 6);
        RezervasyonRequest request = new RezervasyonRequest(
                odaId,
                SABIT_BUGUN.plusDays(1),
                9,
                10,
                "Rollback toplantısı",
                3
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.olusturVeHataFirlat(
                        aktor.getPersonelId(),
                        request
                )
        );

        assertEquals(1, rollbackTestService.transactionIcindekiRezervasyonSayisi());
        assertEquals(1, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(0, rezervasyonSayisi());
        assertEquals(0, auditSayisi());
    }

    @Test
    void kapasiteVeZamanKurallariAtomikUygulanir() throws Exception {
        Personel aktor = personelKaydet("validation.aktor", RolAdi.PERSONEL, true);
        int kucukOda = odaKaydet("Küçük Oda", 6);
        int buyukOda = odaKaydet("Büyük Salon", 12);
        String token = bearer(aktor, RolAdi.PERSONEL);

        postCreateBekle(token, requestBody(kucukOda, SABIT_BUGUN.plusDays(1), 9, 10,
                "Kapasite", 7), 400);
        postCreateBekle(token, requestBody(buyukOda, SABIT_BUGUN.plusDays(1), 9, 10,
                "   ", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, SABIT_BUGUN.plusDays(1), 9, 10,
                "A".repeat(51), 2), 400);
        postCreateBekle(token, requestBody(999999, SABIT_BUGUN.plusDays(1), 9, 10,
                "Bilinmeyen oda", 2), 404);
        postCreateBekle(token, requestBody(buyukOda, SABIT_BUGUN.minusDays(1), 9, 10,
                "Geçmiş", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, SABIT_BUGUN, 10, 11,
                "Başlamış", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, SABIT_BUGUN.plusDays(91), 9, 10,
                "Çok ileri", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 12), 9, 10,
                "Hafta sonu", 2), 400);

        tatilKaydet(LocalDate.of(2026, 9, 14), "TAM_GUN");
        tatilKaydet(LocalDate.of(2026, 9, 15), "YARIM_GUN");
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 14), 9, 10,
                "Tam gün", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 15), 12, 14,
                "Yarım gün sınırı", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 16), 8, 8,
                "Sıfır süre", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 16), 8, 13,
                "Beş saat", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 16), 7, 8,
                "Erken", 2), 400);
        postCreateBekle(token, requestBody(buyukOda, LocalDate.of(2026, 9, 16), 18, 19,
                "Geç", 2), 400);

        assertEquals(0, rezervasyonSayisi());
        assertEquals(0, auditSayisi());
    }

    @Test
    void aktifOdaVeOwnerIntervalCakismalari409Doner() throws Exception {
        Personel owner = personelKaydet("interval.owner", RolAdi.PERSONEL, true);
        Personel baskaOwner = personelKaydet("interval.baska", RolAdi.HR, true);
        int buyukOda = odaKaydet("Büyük Salon", 12);
        int kucukOda = odaKaydet("Küçük Oda", 6);
        LocalDate tarih = LocalDate.of(2026, 9, 16);

        rezervasyonKaydet(owner, buyukOda, tarih, 10, 12, "Mevcut oda", 2, "AKTIF");
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(requestBody(buyukOda, tarih, 11, 13,
                                "Oda çakışması", 2))
                        .header(HttpHeaders.AUTHORIZATION, bearer(baskaOwner, RolAdi.HR)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Seçilen oda bu saat aralığında dolu."));

        rezervasyonKaydet(owner, kucukOda, tarih, 14, 16, "Mevcut owner", 2, "AKTIF");
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(requestBody(buyukOda, tarih, 15, 17,
                                "Owner çakışması", 2))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner, RolAdi.PERSONEL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Bu saat aralığında başka bir rezervasyonunuz bulunuyor."));

        assertEquals(2, rezervasyonSayisi());
        assertEquals(0, auditSayisi());
    }

    @Test
    void sinirSaatlerCakismazVeIptalEdilmisKayitEngellemez() throws Exception {
        Personel owner = personelKaydet("sinir.owner", RolAdi.YONETICI, true);
        int odaId = odaKaydet("Büyük Salon", 12);
        LocalDate tarih = LocalDate.of(2026, 9, 17);
        rezervasyonKaydet(owner, odaId, tarih, 10, 11, "İlk", 12, "AKTIF");
        rezervasyonKaydet(owner, odaId, tarih, 13, 14, "İptal", 12, "IPTAL_EDILDI");

        postCreateBekle(
                bearer(owner, RolAdi.YONETICI),
                requestBody(odaId, tarih, 11, 12, "Komşu", 12),
                201
        );
        postCreateBekle(
                bearer(owner, RolAdi.YONETICI),
                requestBody(odaId, tarih, 13, 14, "İptalin yerine", 12),
                201
        );

        assertEquals(4, rezervasyonSayisi());
        assertEquals(2, auditSayisi());
    }

    @Test
    void createPasifEksikVeTokensizAktorIcinYanEtkisizReddedilir() throws Exception {
        Personel pasif = personelKaydet("create.pasif", RolAdi.PERSONEL, false);
        int odaId = odaKaydet("Büyük Salon", 12);
        String body = requestBody(odaId, SABIT_BUGUN.plusDays(1), 9, 10,
                "Güvenlik", 2);

        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasif, RolAdi.PERSONEL)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + jwtService.generateToken(UUID.randomUUID().toString(), RolAdi.ADMIN)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertEquals(0, rezervasyonSayisi());
        assertEquals(0, auditSayisi());
    }

    @Test
    void personelYalnizKendiRezervasyonGecmisiniGorur() throws Exception {
        Personel aktor = personelKaydet("gecmis.owner", RolAdi.PERSONEL, true);
        Personel yabanci = personelKaydet("gecmis.yabanci", RolAdi.HR, true);
        int odaId = odaKaydet("Büyük Salon", 12);

        String gelecek = rezervasyonKaydet(
                aktor, odaId, SABIT_BUGUN.plusDays(3), 11, 12,
                "Gelecek toplantı", 5, "AKTIF"
        );
        String iptal = rezervasyonKaydet(
                aktor, odaId, SABIT_BUGUN.minusDays(1), 9, 10,
                "İptal edilen toplantı", 3, "IPTAL_EDILDI"
        );
        rezervasyonIptalBilgisiEkle(
                iptal, aktor, "Plan değişti.", "2026-09-08 12:00:00"
        );
        rezervasyonKaydet(
                yabanci, odaId, SABIT_BUGUN.plusDays(4), 9, 10,
                "Yabancı toplantı", 2, "AKTIF"
        );

        int rezervasyonOnce = rezervasyonSayisi();
        int auditOnce = auditSayisi();

        mockMvc.perform(get("/api/reservations/my")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(aktor, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(20))
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(gelecek))
                .andExpect(jsonPath("$.icerik[0].baslik")
                        .value("Gelecek toplantı"))
                .andExpect(jsonPath("$.icerik[0].katilimciSayisi").value(5))
                .andExpect(jsonPath("$.icerik[0].durum").value("AKTIF"))
                .andExpect(jsonPath("$.icerik[0].gecmisMi").value(false))
                .andExpect(jsonPath("$.icerik[0].iptalZamani").value((Object) null))
                .andExpect(jsonPath("$.icerik[0].iptalEden").value((Object) null))
                .andExpect(jsonPath("$.icerik[0].iptalNedeni").value((Object) null))
                .andExpect(jsonPath("$.icerik[1].id").value(iptal))
                .andExpect(jsonPath("$.icerik[1].durum")
                        .value("IPTAL_EDILDI"))
                .andExpect(jsonPath("$.icerik[1].gecmisMi").value(true))
                .andExpect(jsonPath("$.icerik[1].iptalEden.ad").value("Gecmis"))
                .andExpect(jsonPath("$.icerik[1].iptalEden.soyad").value("Owner"))
                .andExpect(jsonPath("$.icerik[1].iptalNedeni")
                        .value("Plan değişti."))
                .andExpect(jsonPath("$.icerik[0].olusturan.id").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].olusturan.kullaniciAdi")
                        .doesNotExist());

        assertEquals(rezervasyonOnce, rezervasyonSayisi());
        assertEquals(auditOnce, auditSayisi());
    }

    @Test
    void adminSirketGeneliRezervasyonGecmisiniGorur() throws Exception {
        Personel admin = personelKaydet("history.admin", RolAdi.ADMIN, true);
        Personel aktifOwner = personelKaydet("aktif.owner", RolAdi.PERSONEL, true);
        Personel pasifOwner = personelKaydet("pasif.owner", RolAdi.HR, false);
        Personel sahteAdmin = personelKaydet("sahte.admin", RolAdi.HR, true);
        int odaId = odaKaydet("Küçük Oda", 6);

        String aktif = rezervasyonKaydet(
                aktifOwner, odaId, SABIT_BUGUN.plusDays(2), 9, 10,
                "Aktif owner başlığı", 4, "AKTIF"
        );
        String pasif = rezervasyonKaydet(
                pasifOwner, odaId, SABIT_BUGUN.minusDays(2), 15, 16,
                "Pasif owner başlığı", 2, "IPTAL_EDILDI"
        );
        rezervasyonIptalBilgisiEkle(
                pasif, admin, "Yönetim iptali.", "2026-09-09 08:00:00"
        );

        mockMvc.perform(get("/api/admin/reservations")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(aktif))
                .andExpect(jsonPath("$.icerik[0].baslik")
                        .value("Aktif owner başlığı"))
                .andExpect(jsonPath("$.icerik[0].olusturan.ad").value("Aktif"))
                .andExpect(jsonPath("$.icerik[1].id").value(pasif))
                .andExpect(jsonPath("$.icerik[1].baslik")
                        .value("Pasif owner başlığı"))
                .andExpect(jsonPath("$.icerik[1].iptalEden.ad").value("History"))
                .andExpect(jsonPath("$.icerik[1].iptalNedeni")
                        .value("Yönetim iptali."));

        mockMvc.perform(get("/api/admin/reservations")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(sahteAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());

        for (RolAdi jwtRolu : new RolAdi[]{
                RolAdi.PERSONEL, RolAdi.YONETICI, RolAdi.HR
        }) {
            Personel aktor = personelKaydet(
                    "history." + jwtRolu.name().toLowerCase(), jwtRolu, true
            );
            mockMvc.perform(get("/api/admin/reservations")
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(aktor, jwtRolu)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rezervasyonGecmisiFiltreVeSayfalamaSozlesmesi() throws Exception {
        Personel owner = personelKaydet("filtre.owner", RolAdi.YONETICI, true);
        Personel yabanci = personelKaydet("filtre.yabanci", RolAdi.PERSONEL, true);
        Personel admin = personelKaydet("filtre.admin", RolAdi.ADMIN, true);
        int buyukOda = odaKaydet("Büyük Salon", 12);
        int kucukOda = odaKaydet("Küçük Oda", 6);
        LocalDate filtreTarihi = SABIT_BUGUN.plusDays(5);

        String dusukId = "00000000-0000-0000-0000-000000000001";
        String yuksekId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        rezervasyonKaydet(
                dusukId, owner, buyukOda, filtreTarihi, 10, 11,
                "Düşük ID", 2, "AKTIF"
        );
        rezervasyonKaydet(
                yuksekId, owner, buyukOda, filtreTarihi, 10, 11,
                "Yüksek ID", 3, "AKTIF"
        );
        rezervasyonKaydet(
                owner, kucukOda, filtreTarihi, 8, 9,
                "İptal", 2, "IPTAL_EDILDI"
        );
        rezervasyonKaydet(
                yabanci, buyukOda, filtreTarihi, 12, 13,
                "Yabancı", 2, "AKTIF"
        );

        mockMvc.perform(get("/api/reservations/my")
                        .param("page", "0")
                        .param("size", "1")
                        .param("durum", "AKTIF")
                        .param("odaId", Integer.toString(buyukOda))
                        .param("baslangic", filtreTarihi.toString())
                        .param("bitis", filtreTarihi.toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(owner, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(yuksekId));

        mockMvc.perform(get("/api/admin/reservations")
                        .param("olusturanId", owner.getPersonelId())
                        .param("durum", "IPTAL_EDILDI")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].baslik").value("İptal"));

        mockMvc.perform(get("/api/admin/reservations")
                        .param("olusturanId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));

        String ownerToken = bearer(owner, RolAdi.YONETICI);
        for (String query : new String[]{
                "?page=-1", "?size=0", "?size=101", "?durum=BOZUK",
                "?odaId=0", "?odaId=-1", "?odaId=metin",
                "?baslangic=2026-09-10", "?bitis=2026-09-10"
        }) {
            mockMvc.perform(get("/api/reservations/my" + query)
                            .header(HttpHeaders.AUTHORIZATION, ownerToken))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(get("/api/admin/reservations")
                        .param("olusturanId", "uuid-degil")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerKendiRezervasyonunuIptalEder() throws Exception {
        Personel owner = personelKaydet("iptal.owner", RolAdi.PERSONEL, true);
        int odaId = odaKaydet("Büyük Salon", 12);
        String id = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN.plusDays(1), 11, 12,
                "Owner iptali", 4, "AKTIF"
        );

        mockMvc.perform(patch("/api/reservations/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(owner, RolAdi.PERSONEL)))
                .andExpect(status().isNoContent());

        var kayit = jdbcTemplate.queryForMap(
                """
                SELECT durum, iptal_eden_id, iptal_nedeni, iptal_zamani
                FROM rezervasyon WHERE rezervasyon_id = ?
                """,
                id
        );
        assertEquals("IPTAL_EDILDI", kayit.get("durum"));
        assertEquals(owner.getPersonelId(), kayit.get("iptal_eden_id"));
        assertEquals(null, kayit.get("iptal_nedeni"));
        assertEquals(LocalDateTime.of(2026, 9, 10, 10, 20),
                kayit.get("iptal_zamani"));
        auditIptaliniDogrula(owner, owner);

        mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", SABIT_BUGUN.toString())
                        .param("bitisTarihi", SABIT_BUGUN.plusDays(2).toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(owner, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/reservations/my")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(owner, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].durum")
                        .value("IPTAL_EDILDI"));
    }

    @Test
    void adminBaskasininRezervasyonunuNedenleIptalEder() throws Exception {
        Personel admin = personelKaydet("iptal.admin", RolAdi.ADMIN, true);
        Personel owner = personelKaydet("hedef.owner", RolAdi.HR, true);
        int odaId = odaKaydet("Küçük Oda", 6);
        String id = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN.plusDays(2), 9, 10,
                "Admin iptali", 3, "AKTIF"
        );

        mockMvc.perform(patch("/api/reservations/{id}/cancel", id)
                        .contentType("application/json")
                        .content("""
                                {"iptalNedeni":"  Oda bakım çalışması.  "}
                                """)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isNoContent());

        assertEquals("Oda bakım çalışması.", jdbcTemplate.queryForObject(
                "SELECT iptal_nedeni FROM rezervasyon WHERE rezervasyon_id = ?",
                String.class,
                id
        ));
        auditIptaliniDogrula(admin, owner);
    }

    @Test
    void nonOwnerDurumdanOnce403Alir() throws Exception {
        Personel owner = personelKaydet("policy.owner", RolAdi.PERSONEL, true);
        Personel yabanci = personelKaydet("policy.yabanci", RolAdi.HR, true);
        Personel sahteAdmin = personelKaydet("policy.sahte", RolAdi.HR, true);
        int odaId = odaKaydet("Büyük Salon", 12);
        String id = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN.minusDays(1), 9, 10,
                "Durum sızmamalı", 2, "IPTAL_EDILDI"
        );

        mockMvc.perform(patch("/api/reservations/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(yabanci, RolAdi.HR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/reservations/{id}/cancel", id)
                        .contentType("application/json")
                        .content("{\"iptalNedeni\":\"Yetkisiz\"}")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(sahteAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());

        assertEquals("IPTAL_EDILDI", rezervasyonDurumu(id));
        assertEquals(0, auditSayisi());
    }

    @Test
    void baslamisVeyaIptalRezervasyon409Doner() throws Exception {
        Personel owner = personelKaydet("durum.owner", RolAdi.YONETICI, true);
        Personel admin = personelKaydet("durum.admin", RolAdi.ADMIN, true);
        int odaId = odaKaydet("Küçük Oda", 6);
        String baslamis = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN, 9, 11,
                "Başlamış", 2, "AKTIF"
        );
        String iptal = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN.plusDays(1), 9, 10,
                "Zaten iptal", 2, "IPTAL_EDILDI"
        );

        iptal409Bekle(owner, RolAdi.YONETICI, baslamis, null);
        iptal409Bekle(
                admin, RolAdi.ADMIN, iptal,
                "{\"iptalNedeni\":\"Tekrar iptal\"}"
        );

        for (String bozukId : new String[]{"uuid-degil", "00000000"}) {
            mockMvc.perform(patch("/api/reservations/{id}/cancel", bozukId)
                            .header(HttpHeaders.AUTHORIZATION,
                                    bearer(owner, RolAdi.YONETICI)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(patch("/api/reservations/{id}/cancel",
                        UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(owner, RolAdi.YONETICI)))
                .andExpect(status().isNotFound());

        assertEquals(2, rezervasyonSayisi());
        assertEquals(0, auditSayisi());
    }

    @Test
    @WithMockUser(roles = "HR")
    void iptalDisTransactionHatasindaDurumMetadataVeAuditRollbackOlur() {
        Personel owner = personelKaydet("rollback.iptal", RolAdi.HR, true);
        int odaId = odaKaydet("Büyük Salon", 12);
        String id = rezervasyonKaydet(
                owner, odaId, SABIT_BUGUN.plusDays(1), 11, 12,
                "Rollback iptali", 3, "AKTIF"
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.iptalEtVeHataFirlat(
                        owner.getPersonelId(), id
                )
        );

        assertEquals("IPTAL_EDILDI",
                rollbackTestService.transactionIcindekiIptalDurumu());
        assertEquals(1L,
                rollbackTestService.transactionIcindekiIptalAuditSayisi());
        assertEquals("AKTIF", rezervasyonDurumu(id));
        var kayit = jdbcTemplate.queryForMap(
                """
                SELECT iptal_eden_id, iptal_nedeni, iptal_zamani
                FROM rezervasyon WHERE rezervasyon_id = ?
                """,
                id
        );
        assertEquals(null, kayit.get("iptal_eden_id"));
        assertEquals(null, kayit.get("iptal_nedeni"));
        assertEquals(null, kayit.get("iptal_zamani"));
        assertEquals(0, auditSayisi());
    }

    private void takvimGizlilikDogrula(
            Personel aktor,
            RolAdi jwtRolu,
            String rezervasyonId,
            boolean detayGorunur
    ) throws Exception {
        var sonuc = mockMvc.perform(get("/api/reservations")
                        .param("baslangicTarihi", BASLANGIC.toString())
                        .param("bitisTarihi", BITIS.toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(rezervasyonId))
                .andExpect(jsonPath("$[0].benMiOlusturdum")
                        .value(aktor.getPersonelId().equals(
                                personelIdByKullaniciAdi("owner.personel")
                        )));

        if (detayGorunur) {
            sonuc.andExpect(jsonPath("$[0].baslik").value("Yönetim Toplantısı"))
                    .andExpect(jsonPath("$[0].katilimciSayisi").value(7));
        } else {
            sonuc.andExpect(jsonPath("$[0].baslik").value((Object) null))
                    .andExpect(jsonPath("$[0].katilimciSayisi").value((Object) null));
        }
    }

    private String personelIdByKullaniciAdi(String kullaniciAdi) {
        return personelRepository.findByKullaniciAdi(kullaniciAdi)
                .orElseThrow()
                .getPersonelId();
    }

    private String bearer(Personel aktor, RolAdi jwtRolu) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), jwtRolu);
    }

    private void postCreateBekle(String token, String body, int statusCode) throws Exception {
        mockMvc.perform(post("/api/reservations")
                        .contentType("application/json")
                        .content(body)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().is(statusCode));
    }

    private void iptal409Bekle(
            Personel aktor,
            RolAdi jwtRolu,
            String rezervasyonId,
            String body
    ) throws Exception {
        var istek = patch("/api/reservations/{id}/cancel", rezervasyonId)
                .header(HttpHeaders.AUTHORIZATION, bearer(aktor, jwtRolu));
        if (body != null) {
            istek.contentType("application/json").content(body);
        }
        mockMvc.perform(istek)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Rezervasyon artık iptal edilemez."));
    }

    private void auditIptaliniDogrula(Personel aktor, Personel hedef) {
        var audit = jdbcTemplate.queryForMap(
                """
                SELECT eylem_tipi, personel_id, hedef_personel_id,
                       kullanici_adi, detay
                FROM audit_log
                """
        );
        assertEquals(AuditEylem.REZERVASYON_IPTAL_EDILDI.name(),
                audit.get("eylem_tipi"));
        assertEquals(aktor.getPersonelId(), audit.get("personel_id"));
        assertEquals(hedef.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(aktor.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals("Rezervasyon iptal edildi.", audit.get("detay"));
    }

    private String rezervasyonDurumu(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT durum FROM rezervasyon WHERE rezervasyon_id = ?",
                String.class,
                id
        );
    }

    private String requestBody(
            int odaId,
            LocalDate tarih,
            int baslangicSaat,
            int bitisSaat,
            String baslik,
            int katilimciSayisi
    ) {
        return """
                {
                  "odaId": %d,
                  "tarih": "%s",
                  "baslangicSaat": %d,
                  "bitisSaat": %d,
                  "baslik": "%s",
                  "katilimciSayisi": %d
                }
                """.formatted(
                odaId,
                tarih,
                baslangicSaat,
                bitisSaat,
                baslik,
                katilimciSayisi
        );
    }

    private void tatilKaydet(LocalDate tarih, String gunSuresi) {
        jdbcTemplate.update(
                """
                INSERT INTO tatil (
                    ad, tarih, bitis_tarihi, kategori, gun_suresi, version
                ) VALUES (?, ?, ?, 'SIRKET', ?, 0)
                """,
                "Test tatili " + tarih,
                tarih,
                tarih,
                gunSuresi
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

    private String rezervasyonKaydet(
            Personel olusturan,
            int odaId,
            LocalDate tarih,
            int baslangic,
            int bitis,
            String baslik,
            int katilimci,
            String durum
    ) {
        return rezervasyonKaydet(
                UUID.randomUUID().toString(), olusturan, odaId, tarih,
                baslangic, bitis, baslik, katilimci, durum
        );
    }

    private String rezervasyonKaydet(
            String id,
            Personel olusturan,
            int odaId,
            LocalDate tarih,
            int baslangic,
            int bitis,
            String baslik,
            int katilimci,
            String durum
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO rezervasyon (
                    rezervasyon_id, olusturan_id, baslik, tarih,
                    baslangic_saat, bitis_saat, oda_id,
                    katilimci_sayisi, durum, olusturma_zamani
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                id,
                olusturan.getPersonelId(),
                baslik,
                tarih,
                baslangic,
                bitis,
                odaId,
                katilimci,
                durum
        );
        return id;
    }

    private void rezervasyonIptalBilgisiEkle(
            String rezervasyonId,
            Personel iptalEden,
            String iptalNedeni,
            String iptalZamani
    ) {
        jdbcTemplate.update(
                """
                UPDATE rezervasyon
                SET iptal_eden_id = ?, iptal_nedeni = ?, iptal_zamani = ?
                WHERE rezervasyon_id = ?
                """,
                iptalEden.getPersonelId(),
                iptalNedeni,
                iptalZamani,
                rezervasyonId
        );
    }

    private Rol rolKaydet(RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        });
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman sonuc = new Departman();
        sonuc.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(sonuc);
    }

    private Personel personelKaydet(String kullaniciAdi, RolAdi rolAdi, boolean aktif) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        String[] adParcalari = kullaniciAdi.split("\\.");
        personel.setAdi(basHarfiBuyut(adParcalari[0]));
        personel.setSoyadi(basHarfiBuyut(adParcalari[1]));
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rolRepository.findById(switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        }).orElseThrow());
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        return personelRepository.saveAndFlush(personel);
    }

    private String basHarfiBuyut(String deger) {
        return Character.toUpperCase(deger.charAt(0)) + deger.substring(1);
    }

    private int rezervasyonSayisi() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rezervasyon", Integer.class);
    }

    private int auditSayisi() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
    }

    @TestConfiguration
    static class SabitClockConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-09-10T07:20:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }

        @Bean
        RezervasyonRollbackTestService rezervasyonRollbackTestService(
                RezervasyonService rezervasyonService,
                RezervasyonRepository rezervasyonRepository,
                AuditLogRepository auditLogRepository
        ) {
            return new RezervasyonRollbackTestService(
                    rezervasyonService,
                    rezervasyonRepository,
                    auditLogRepository
            );
        }
    }

    static class RezervasyonRollbackTestService {

        private final RezervasyonService rezervasyonService;
        private final RezervasyonRepository rezervasyonRepository;
        private final AuditLogRepository auditLogRepository;
        private long transactionIcindekiRezervasyonSayisi;
        private long transactionIcindekiAuditSayisi;
        private String transactionIcindekiIptalDurumu;
        private long transactionIcindekiIptalAuditSayisi;

        RezervasyonRollbackTestService(
                RezervasyonService rezervasyonService,
                RezervasyonRepository rezervasyonRepository,
                AuditLogRepository auditLogRepository
        ) {
            this.rezervasyonService = rezervasyonService;
            this.rezervasyonRepository = rezervasyonRepository;
            this.auditLogRepository = auditLogRepository;
        }

        @Transactional
        public void olusturVeHataFirlat(
                String aktorId,
                RezervasyonRequest request
        ) {
            rezervasyonService.rezervasyonOlustur(aktorId, request);
            transactionIcindekiRezervasyonSayisi = rezervasyonRepository.count();
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        @Transactional
        public void iptalEtVeHataFirlat(String aktorId, String rezervasyonId) {
            rezervasyonService.rezervasyonIptalEt(
                    aktorId, rezervasyonId, null
            );
            transactionIcindekiIptalDurumu = rezervasyonRepository.findById(
                    rezervasyonId
            ).orElseThrow().getDurum().name();
            transactionIcindekiIptalAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException(
                    "İptal rollback davranışını doğrulamak için test hatası."
            );
        }

        long transactionIcindekiRezervasyonSayisi() {
            return transactionIcindekiRezervasyonSayisi;
        }

        long transactionIcindekiAuditSayisi() {
            return transactionIcindekiAuditSayisi;
        }

        String transactionIcindekiIptalDurumu() {
            return transactionIcindekiIptalDurumu;
        }

        long transactionIcindekiIptalAuditSayisi() {
            return transactionIcindekiIptalAuditSayisi;
        }
    }
}
