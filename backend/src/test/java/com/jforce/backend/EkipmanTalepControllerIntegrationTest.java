package com.jforce.backend;

import com.jforce.backend.model.entity.*;
import com.jforce.backend.model.enums.*;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Import(EkipmanTalepControllerIntegrationTest.TestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EkipmanTalepControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime SABIT_ZAMAN = LocalDateTime.of(2026, 8, 30, 10, 15);

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private EkipmanTalepRepository ekipmanTalepRepository;
    @Autowired private EkipmanTipiRepository ekipmanTipiRepository;
    @Autowired private EkipmanBilgileriRepository ekipmanBilgileriRepository;
    @Autowired private ZimmetlemeBilgileriRepository zimmetRepository;
    @Autowired private DurumTuruRepository durumTuruRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PersonelRepository personelRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HataEnjekteAuditService hataEnjekteAuditService;

    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;
    private Rol adminRolu;
    private Departman departman;
    private Personel personel;
    private Personel hr;
    private EkipmanTipi laptop;
    private EkipmanTipi monitor;
    private EkipmanTipi pasifTip;
    private DurumTuru bekliyor;
    private DurumTuru onaylandi;
    private DurumTuru reddedildi;
    private DurumTuru iptalEdildi;

    @BeforeEach
    void setUp() {
        hataEnjekteAuditService.hataDurumunuAyarla(false);
        auditLogRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        ekipmanTalepRepository.deleteAll();
        ekipmanBilgileriRepository.deleteAll();
        ekipmanTipiRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        durumTuruRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        personelRolu = rolKaydet(9501, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(9502, RolAdi.YONETICI);
        hrRolu = rolKaydet(9503, RolAdi.HR);
        adminRolu = rolKaydet(9504, RolAdi.ADMIN);
        departman = departmanKaydet("Talep Test Departmanı");
        personel = personelKaydet("talep.personel", personelRolu, true);
        hr = personelKaydet("talep.hr", hrRolu, true);
        laptop = tipKaydet("Laptop", true);
        monitor = tipKaydet("Monitor", true);
        pasifTip = tipKaydet("Pasif Tip", false);
        bekliyor = durumKaydet(TalepDurum.ONAY_BEKLIYOR);
        onaylandi = durumKaydet(TalepDurum.ONAYLANDI);
        reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        iptalEdildi = durumKaydet(TalepDurum.IPTAL_EDILDI);
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR"})
    void personelVeHrAktifTipIcinNormalizeTalepOlusturur(RolAdi rol) throws Exception {
        Personel aktor = rol == RolAdi.PERSONEL ? personel : hr;
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(laptop.getEkipmanTipiId(), "  İkinci ekran  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.ekipmanTipiId").value(laptop.getEkipmanTipiId()))
                .andExpect(jsonPath("$.ekipmanTipi").value("Laptop"))
                .andExpect(jsonPath("$.aciklama").value("İkinci ekran"))
                .andExpect(jsonPath("$.durum").value("ONAY_BEKLIYOR"))
                .andExpect(jsonPath("$.durumAdi").value("Onay bekliyor"))
                .andExpect(jsonPath("$.talepTarihi").value("2026-08-30T10:15:00"))
                .andExpect(jsonPath("$.kararNotu").value((Object) null))
                .andExpect(jsonPath("$.islemTarihi").value((Object) null))
                .andExpect(jsonPath("$.kararVeren").value((Object) null))
                .andExpect(jsonPath("$.zimmet").value((Object) null))
                .andExpect(jsonPath("$.personel").doesNotExist());

        EkipmanTalep kayit = ekipmanTalepRepository.findAll().getFirst();
        assertEquals(aktor.getPersonelId(), kayit.getPersonel().getPersonelId());
        assertEquals(SABIT_ZAMAN, kayit.getTalepTarih());
        assertNull(kayit.getIslemYapanId());
        assertNull(kayit.getIslemTarihi());
        assertNull(kayit.getKararNotu());
        auditDogrula(AuditEylem.EKIPMAN_TALEBI_OLUSTURULDU, aktor,
                "Ekipman talebi oluşturuldu.");
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"YONETICI", "ADMIN"})
    void yoneticiVeAdminSelfServiceEndpointlerineErisemez(RolAdi rol) throws Exception {
        Personel aktor = personelKaydet("talep." + rol.name().toLowerCase(), rol(rol), true);
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(laptop.getEkipmanTipiId(), null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/equipment-requests/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/equipment-requests/{id}", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(laptop.getEkipmanTipiId(), null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/equipment-requests/{id}/cancel", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
        assertEquals(0L, ekipmanTalepRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void selfServiceAktoruAktiflikVarlikAuthenticationVeJwtDbRolUyumunuDogrular() throws Exception {
        Personel pasif = personelKaydet("talep.pasif", personelRolu, false);
        Personel dbHr = personelKaydet("talep.db.hr", hrRolu, true);
        String body = createJson(laptop.getEkipmanTipiId(), null);
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(pasif, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(dbHr, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(UUID.randomUUID().toString(), RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/equipment-requests").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertEquals(0L, ekipmanTalepRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ekipmanTipiId\":null}", "{\"ekipmanTipiId\":0}", "{\"ekipmanTipiId\":-1}"})
    void gecersizCreateBody400Doner(String body) throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0L, ekipmanTalepRepository.count());
    }

    @Test
    void aciklamaBlankNullOlurVeTrimSonrasiSinirUygulanir() throws Exception {
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "   ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aciklama").value((Object) null));
        assertNull(ekipmanTalepRepository.findAll().getFirst().getAciklama());
        EkipmanTalep ilk = ekipmanTalepRepository.findAll().getFirst();
        ilk.setTalepDurum(iptalEdildi);
        ekipmanTalepRepository.saveAndFlush(ilk);
        auditLogRepository.deleteAll();
        String sinir = "A".repeat(255);
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "  " + sinir + "  ")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.aciklama").value(sinir));
        EkipmanTalep ikinci = ekipmanTalepRepository.findAll().stream()
                .filter(t -> !t.getTalepId().equals(ilk.getTalepId()))
                .findFirst().orElseThrow();
        ikinci.setTalepDurum(iptalEdildi);
        ekipmanTalepRepository.saveAndFlush(ikinci);
        auditLogRepository.deleteAll();
        mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "X".repeat(256))))
                .andExpect(status().isBadRequest());
        assertEquals(2L, ekipmanTalepRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifVeOlmayanTipCreateKontrolluReddedilir() throws Exception {
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(pasifTip.getEkipmanTipiId(), null)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(999999, null)))
                .andExpect(status().isNotFound());
        assertEquals(0L, ekipmanTalepRepository.count());
    }

    @Test
    void duplicateBekleyen409TerminalSonrasiYeniCreateMumkundur() throws Exception {
        talepKaydet(personel, laptop, bekliyor, "İlk", SABIT_ZAMAN.minusHours(1));
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "İkinci")))
                .andExpect(status().isConflict());
        assertEquals(0L, auditLogRepository.count());
        EkipmanTalep ilk = ekipmanTalepRepository.findAll().getFirst();
        ilk.setTalepDurum(reddedildi);
        ekipmanTalepRepository.saveAndFlush(ilk);
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "İkinci")))
                .andExpect(status().isCreated());
        assertEquals(2L, ekipmanTalepRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void acikZimmetAyniTipteYeniTalebiEngellemez() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "L-OPEN", EkipmanDurum.PERSONELDE);
        zimmetKaydet(cihaz, personel, null, SABIT_ZAMAN.minusDays(5), null);
        mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), null)))
                .andExpect(status().isCreated());
    }

    @Test
    void paralelAyniOwnerTipCreateIslemlerindenYalnizBiriBasariliOlur() throws Exception {
        CountDownLatch basla = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> ilk = executor.submit(() -> { basla.await(); return createStatus(personel, laptop.getEkipmanTipiId()); });
            Future<Integer> ikinci = executor.submit(() -> { basla.await(); return createStatus(personel, laptop.getEkipmanTipiId()); });
            basla.countDown();
            assertEquals(List.of(201, 409), List.of(ilk.get(), ikinci.get()).stream().sorted().toList());
        }
        assertEquals(1L, ekipmanTalepRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void kendiTalepleriOwnershipAndFiltreSiraSayfalamaVeZimmetOzetiniKorur() throws Exception {
        Personel yabanci = personelKaydet("talep.yabanci", personelRolu, true);
        EkipmanTalep eski = talepKaydet(personel, laptop, reddedildi, "Eski", SABIT_ZAMAN.minusDays(2));
        EkipmanTalep onay = talepKaydet(personel, laptop, onaylandi, "Onay", SABIT_ZAMAN.minusDays(1));
        EkipmanTalep yeni = talepKaydet(personel, monitor, bekliyor, "Yeni", SABIT_ZAMAN);
        talepKaydet(yabanci, monitor, bekliyor, "Sızmamalı", SABIT_ZAMAN.plusDays(1));
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Z-1", EkipmanDurum.PERSONELDE);
        ZimmetlemeBilgileri zimmet = zimmetKaydet(cihaz, personel, onay, SABIT_ZAMAN.minusHours(4), null);
        Personel kararVeren = personelKaydet("talep.karar", adminRolu, true);
        onay.setIslemYapanId(kararVeren);
        onay.setIslemTarihi(SABIT_ZAMAN.minusHours(4));
        onay.setKararNotu("Güvenli karar notu");
        ekipmanTalepRepository.saveAndFlush(onay);

        mockMvc.perform(get("/api/equipment-requests/my").param("page", "0").param("size", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.icerik.length()").value(3))
                .andExpect(jsonPath("$.icerik[0].id").value(yeni.getTalepId()))
                .andExpect(jsonPath("$.icerik[1].id").value(onay.getTalepId()))
                .andExpect(jsonPath("$.icerik[1].kararVeren.ad").value(kararVeren.getAdi()))
                .andExpect(jsonPath("$.icerik[1].kararVeren.soyad").value(kararVeren.getSoyadi()))
                .andExpect(jsonPath("$.icerik[1].zimmet.id").value(zimmet.getZimmetId()))
                .andExpect(jsonPath("$.icerik[1].zimmet.ekipmanId").value(cihaz.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[1].zimmet.seriNo").value("Z-1"))
                .andExpect(jsonPath("$.icerik[2].id").value(eski.getTalepId()))
                .andExpect(jsonPath("$.toplamKayit").value(3));
        mockMvc.perform(get("/api/equipment-requests/my").param("durum", "ONAY_BEKLIYOR")
                        .param("ekipmanTipiId", monitor.getEkipmanTipiId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(yeni.getTalepId()))
                .andExpect(jsonPath("$.toplamKayit").value(1));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pozitifOlmayanOlmayanVeGecersizListeFiltreleriDogruDavranir() throws Exception {
        talepKaydet(personel, laptop, bekliyor, null, SABIT_ZAMAN);
        mockMvc.perform(get("/api/equipment-requests/my").param("ekipmanTipiId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.icerik.length()").value(0));
        for (String yol : List.of("/api/equipment-requests/my?page=-1&size=20",
                "/api/equipment-requests/my?page=0&size=0", "/api/equipment-requests/my?page=0&size=101",
                "/api/equipment-requests/my?ekipmanTipiId=0", "/api/equipment-requests/my?ekipmanTipiId=-1",
                "/api/equipment-requests/my?ekipmanTipiId=metin", "/api/equipment-requests/my?durum=BILINMIYOR")) {
            mockMvc.perform(get(yol).header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void bekleyenOwnerTalebiGercekDegisiklikteAuditsizGunceller() throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, bekliyor, "Eski", SABIT_ZAMAN);
        mockMvc.perform(patch("/api/equipment-requests/{id}", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(monitor.getEkipmanTipiId(), "  Yeni açıklama  ")))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        EkipmanTalep guncel = ekipmanTalepRepository.findById(talep.getTalepId()).orElseThrow();
        assertEquals(monitor.getEkipmanTipiId(), guncel.getEkipmanTipi().getEkipmanTipiId());
        assertEquals("Yeni açıklama", guncel.getAciklama());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void updateNormalizeNoOpAuditsizdir() throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, bekliyor, "Açıklama", SABIT_ZAMAN);
        mockMvc.perform(patch("/api/equipment-requests/{id}", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "  Açıklama  ")))
                .andExpect(status().isNoContent());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void updateOwnershipDurumdanVeNoOptanOnceGuvenli404IleDogrulanir() throws Exception {
        Personel yabanci = personelKaydet("talep.update.yabanci", personelRolu, true);
        EkipmanTalep yabanciTerminal = talepKaydet(yabanci, laptop, reddedildi, "Aynı", SABIT_ZAMAN);
        mockMvc.perform(patch("/api/equipment-requests/{id}", yabanciTerminal.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "Aynı")))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @EnumSource(value = TalepDurum.class, names = {"ONAYLANDI", "REDDEDILDI", "IPTAL_EDILDI"})
    void terminalKendiTalebiAyniDegerleBileGuncellenemez(TalepDurum durum) throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, durum(durum), "Aynı", SABIT_ZAMAN);
        mockMvc.perform(patch("/api/equipment-requests/{id}", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), "Aynı")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUuidVarlikTipAktiflikVeDuplicateKurallariniDogrular() throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, bekliyor, null, SABIT_ZAMAN);
        talepKaydet(personel, monitor, bekliyor, null, SABIT_ZAMAN.minusHours(1));
        patchUpdateBekle("gecersiz", laptop.getEkipmanTipiId(), status().isBadRequest());
        patchUpdateBekle(UUID.randomUUID().toString(), laptop.getEkipmanTipiId(), status().isNotFound());
        patchUpdateBekle(talep.getTalepId(), pasifTip.getEkipmanTipiId(), status().isConflict());
        patchUpdateBekle(talep.getTalepId(), 999999, status().isNotFound());
        patchUpdateBekle(talep.getTalepId(), monitor.getEkipmanTipiId(), status().isConflict());
        assertEquals(laptop.getEkipmanTipiId(), ekipmanTalepRepository.findById(talep.getTalepId())
                .orElseThrow().getEkipmanTipi().getEkipmanTipiId());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ownerBekleyenTalebiniIptalEderIslemAlanlariVeGuvenliAuditOlusur() throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, bekliyor, "İptal", SABIT_ZAMAN.minusDays(1));
        mockMvc.perform(patch("/api/equipment-requests/{id}/cancel", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        Map<String, Object> guncel = jdbcTemplate.queryForMap("""
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi, t.karar_notu
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """, talep.getTalepId());
        assertEquals(TalepDurum.IPTAL_EDILDI.name(), guncel.get("durum_adi"));
        assertEquals(personel.getPersonelId(), guncel.get("islem_yapan_id"));
        assertEquals(SABIT_ZAMAN, guncel.get("islem_tarihi"));
        assertNull(guncel.get("karar_notu"));
        auditDogrula(AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI, personel, "Ekipman talebi iptal edildi.");
    }

    @Test
    void cancelOwnershipDurumdanOnce404TerminalVeTekrarIptal409Doner() throws Exception {
        Personel yabanci = personelKaydet("talep.cancel.yabanci", personelRolu, true);
        EkipmanTalep yabanciTerminal = talepKaydet(yabanci, laptop, reddedildi, null, SABIT_ZAMAN);
        EkipmanTalep kendiTerminal = talepKaydet(personel, monitor, onaylandi, null, SABIT_ZAMAN);
        cancelBekle(yabanciTerminal.getTalepId(), status().isNotFound());
        cancelBekle(kendiTerminal.getTalepId(), status().isConflict());
        kendiTerminal.setTalepDurum(iptalEdildi);
        ekipmanTalepRepository.saveAndFlush(kendiTerminal);
        cancelBekle(kendiTerminal.getTalepId(), status().isConflict());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void cancelUuidVeMissingKaynakKontrolluReddedilir() throws Exception {
        cancelBekle("gecersiz", status().isBadRequest());
        cancelBekle(UUID.randomUUID().toString(), status().isNotFound());
    }

    @Test
    void createAuditHatasiTalebiRollbackEder() throws Exception {
        hataEnjekteAuditService.hataDurumunuAyarla(true);
        try {
            mockMvc.perform(post("/api/equipment-requests").header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                            .contentType(MediaType.APPLICATION_JSON).content(createJson(laptop.getEkipmanTipiId(), null)))
                    .andExpect(status().isInternalServerError());
        } finally { hataEnjekteAuditService.hataDurumunuAyarla(false); }
        assertEquals(0L, ekipmanTalepRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void cancelAuditHatasiDurumVeIslemAlanlariniRollbackEder() throws Exception {
        EkipmanTalep talep = talepKaydet(personel, laptop, bekliyor, null, SABIT_ZAMAN.minusDays(1));
        hataEnjekteAuditService.hataDurumunuAyarla(true);
        try {
            mockMvc.perform(patch("/api/equipment-requests/{id}/cancel", talep.getTalepId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                    .andExpect(status().isInternalServerError());
        } finally { hataEnjekteAuditService.hataDurumunuAyarla(false); }
        Map<String, Object> degismeyen = jdbcTemplate.queryForMap("""
                SELECT d.durum_adi, t.islem_yapan_id, t.islem_tarihi
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """, talep.getTalepId());
        assertEquals(TalepDurum.ONAY_BEKLIYOR.name(), degismeyen.get("durum_adi"));
        assertNull(degismeyen.get("islem_yapan_id"));
        assertNull(degismeyen.get("islem_tarihi"));
        assertEquals(0L, auditLogRepository.count());
    }

    private void patchUpdateBekle(String id, Integer tipId, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        mockMvc.perform(patch("/api/equipment-requests/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(tipId, null)))
                .andExpect(matcher);
    }

    private void cancelBekle(String id, org.springframework.test.web.servlet.ResultMatcher matcher) throws Exception {
        mockMvc.perform(patch("/api/equipment-requests/{id}/cancel", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                .andExpect(matcher);
    }

    private int createStatus(Personel aktor, Integer tipId) throws Exception {
        return mockMvc.perform(post("/api/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson(tipId, null)))
                .andReturn().getResponse().getStatus();
    }

    private String createJson(Integer tipId, String aciklama) {
        return "{\"ekipmanTipiId\":" + tipId + ",\"aciklama\":"
                + (aciklama == null ? "null" : "\"" + aciklama + "\"") + "}";
    }

    private Rol rol(RolAdi rolAdi) {
        return switch (rolAdi) { case PERSONEL -> personelRolu; case YONETICI -> yoneticiRolu; case HR -> hrRolu; case ADMIN -> adminRolu; };
    }

    private DurumTuru durum(TalepDurum durum) {
        return switch (durum) { case ONAY_BEKLIYOR -> bekliyor; case ONAYLANDI -> onaylandi; case REDDEDILDI -> reddedildi; case IPTAL_EDILDI -> iptalEdildi; };
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol(); rol.setRolId(id); rol.setRolAdi(rolAdi); return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman d = new Departman(); d.setDepartmanAdi(ad); return departmanRepository.saveAndFlush(d);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, boolean aktif) {
        Personel p = new Personel(); p.setKullaniciAdi(kullaniciAdi); p.setParolaHash("test-hash"); p.setAdi("Test");
        p.setSoyadi(kullaniciAdi); p.setDogumTarihi(LocalDate.of(1990, 1, 1)); p.setePosta(kullaniciAdi + "@test.local");
        p.setDepartman(departman); p.setRol(rol); p.setCalisiyorMu(aktif); p.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        p.setYillikIzinHakki(14); p.setProfilFoto("def_pfp.png"); p.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(p);
    }

    private EkipmanTipi tipKaydet(String ad, boolean aktif) {
        EkipmanTipi t = new EkipmanTipi(); t.setTipAdi(ad); t.setAktifMi(aktif); return ekipmanTipiRepository.saveAndFlush(t);
    }

    private DurumTuru durumKaydet(TalepDurum durum) {
        DurumTuru d = new DurumTuru(); d.setDurumKodu(durum); return durumTuruRepository.saveAndFlush(d);
    }

    private EkipmanTalep talepKaydet(Personel owner, EkipmanTipi tip, DurumTuru durum, String aciklama, LocalDateTime tarih) {
        EkipmanTalep t = new EkipmanTalep(); t.setPersonel(owner); t.setEkipmanTipi(tip); t.setTalepDurum(durum);
        t.setAciklama(aciklama); t.setTalepTarih(tarih); return ekipmanTalepRepository.saveAndFlush(t);
    }

    private EkipmanBilgileri cihazKaydet(EkipmanTipi tip, String seriNo, EkipmanDurum durum) {
        EkipmanBilgileri e = new EkipmanBilgileri(); e.setEkipmanTipiId(tip); e.setMarka("Test Marka");
        e.setModel("Test Model"); e.setSeriNo(seriNo); e.setEkipmanDurum(durum); return ekipmanBilgileriRepository.saveAndFlush(e);
    }

    private ZimmetlemeBilgileri zimmetKaydet(EkipmanBilgileri cihaz, Personel owner, EkipmanTalep talep,
                                             LocalDateTime teslim, LocalDateTime iade) {
        ZimmetlemeBilgileri z = new ZimmetlemeBilgileri(); z.setEkipmanId(cihaz); z.setPersonelId(owner);
        z.setTalep(talep); z.setTeslimTarihi(teslim); z.setIadeTarihi(iade); return zimmetRepository.saveAndFlush(z);
    }

    private String bearer(Personel aktor, RolAdi rol) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rol);
    }

    private void auditDogrula(AuditEylem eylem, Personel aktor, String detay) {
        assertEquals(1L, auditLogRepository.count());
        Map<String, Object> audit = jdbcTemplate.queryForMap("SELECT eylem_tipi, personel_id, hedef_personel_id, kullanici_adi, detay FROM audit_log");
        assertEquals(eylem.name(), audit.get("eylem_tipi"));
        assertEquals(aktor.getPersonelId(), audit.get("personel_id"));
        assertEquals(aktor.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(aktor.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals(detay, audit.get("detay"));
        assertFalse(detay.contains(laptop.getTipAdi()));
        assertFalse(detay.contains(laptop.getEkipmanTipiId().toString()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestConfig {
        @Bean @Primary Clock ekipmanTalepTestClock() {
            return Clock.fixed(Instant.parse("2026-08-30T07:15:00Z"), ZoneId.of("Europe/Istanbul"));
        }
        @Bean @Primary HataEnjekteAuditService hataEnjekteAuditService(AuditLogRepository repository) {
            return new HataEnjekteAuditService(repository);
        }
    }

    static class HataEnjekteAuditService extends AuditService {
        private volatile boolean hata;
        HataEnjekteAuditService(AuditLogRepository repository) { super(repository); }
        void hataDurumunuAyarla(boolean hata) { this.hata = hata; }
        @Override public void logKaydetOrtakTransaction(AuditEylem eylem, Personel aktor, String kullaniciAdi,
                                                        String detay, Personel hedef) {
            if (hata) throw new IllegalStateException("Audit hata enjeksiyonu.");
            super.logKaydetOrtakTransaction(eylem, aktor, kullaniciAdi, detay, hedef);
        }
    }
}
