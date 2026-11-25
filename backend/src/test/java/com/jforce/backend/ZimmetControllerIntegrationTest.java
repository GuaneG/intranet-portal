package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.EkipmanTipiRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.ZimmetService;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import({
        ZimmetControllerIntegrationTest.SabitSaatConfig.class,
        ZimmetControllerIntegrationTest.RollbackTestConfig.class
})
class ZimmetControllerIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime SABIT_ZAMAN =
            LocalDateTime.of(2026, 8, 31, 14, 0);

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private ZimmetlemeBilgileriRepository zimmetRepository;
    @Autowired private EkipmanBilgileriRepository ekipmanRepository;
    @Autowired private EkipmanTipiRepository tipRepository;
    @Autowired private EkipmanTalepRepository talepRepository;
    @Autowired private DurumTuruRepository durumRepository;
    @Autowired private AuditLogRepository auditRepository;
    @Autowired private PersonelRepository personelRepository;
    @Autowired private DepartmanRepository departmanRepository;
    @Autowired private RolRepository rolRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ZimmetRollbackTestService rollbackTestService;

    private Rol personelRolu;
    private Rol hrRolu;
    private Rol yoneticiRolu;
    private Rol adminRolu;
    private Departman departman;
    private EkipmanTipi laptop;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        talepRepository.deleteAll();
        ekipmanRepository.deleteAll();
        tipRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        durumRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        personelRolu = rolKaydet(9701, RolAdi.PERSONEL);
        hrRolu = rolKaydet(9702, RolAdi.HR);
        yoneticiRolu = rolKaydet(9703, RolAdi.YONETICI);
        adminRolu = rolKaydet(9704, RolAdi.ADMIN);
        departman = departmanKaydet("Zimmet Test Departmanı");
        laptop = tipKaydet("Laptop");
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void tumAktifRollerYalnizKendiAcikVeGecmisZimmetleriniGuvenliGorur(RolAdi rol)
            throws Exception {
        Personel aktor = personelKaydet(
                "zimmet.my." + rol.name().toLowerCase(), rol(rol), true
        );
        Personel yabanci = personelKaydet("zimmet.my.yabanci", personelRolu, true);
        EkipmanBilgileri eskiCihaz = cihazKaydet(laptop, "MY-OLD-" + rol, EkipmanDurum.DEPODA);
        EkipmanBilgileri yeniCihaz = cihazKaydet(laptop, "MY-NEW-" + rol, EkipmanDurum.PERSONELDE);
        EkipmanBilgileri yabanciCihaz = cihazKaydet(laptop, "MY-OTHER-" + rol, EkipmanDurum.PERSONELDE);
        ZimmetlemeBilgileri eski = zimmetKaydet(
                eskiCihaz, aktor, SABIT_ZAMAN.minusDays(3), SABIT_ZAMAN.minusDays(2)
        );
        ZimmetlemeBilgileri yeni = zimmetKaydet(
                yeniCihaz, aktor, SABIT_ZAMAN.minusDays(1), null
        );
        zimmetKaydet(yabanciCihaz, yabanci, SABIT_ZAMAN, null);

        mockMvc.perform(get("/api/assignments/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(yeni.getZimmetId()))
                .andExpect(jsonPath("$.icerik[1].id").value(eski.getZimmetId()))
                .andExpect(jsonPath("$.icerik[0].ekipmanTipi").value("Laptop"))
                .andExpect(jsonPath("$.icerik[0].seriNo").value("MY-NEW-" + rol))
                .andExpect(jsonPath("$.icerik[0].personel").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].ePosta").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].talep").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kullanimDisiNedeni").doesNotExist());
    }

    @Test
    void adminSirketGeneliniPasifOwnerVeAndFiltrelerleGuvenliGorur() throws Exception {
        Personel admin = personelKaydet("zimmet.admin.list", adminRolu, true);
        Personel aktif = personelKaydet("zimmet.admin.aktif", personelRolu, true);
        Personel pasif = personelKaydet("zimmet.admin.pasif", personelRolu, false);
        EkipmanTipi monitor = tipKaydet("Monitor");
        EkipmanBilgileri pasifLaptop = cihazKaydet(laptop, "ADMIN-PASSIVE-1", EkipmanDurum.PERSONELDE);
        EkipmanBilgileri aktifLaptop = cihazKaydet(laptop, "ADMIN-ACTIVE-1", EkipmanDurum.PERSONELDE);
        EkipmanBilgileri pasifMonitor = cihazKaydet(monitor, "ADMIN-PASSIVE-2", EkipmanDurum.DEPODA);
        ZimmetlemeBilgileri beklenen = zimmetKaydet(
                pasifLaptop, pasif, SABIT_ZAMAN.minusHours(1), null
        );
        zimmetKaydet(aktifLaptop, aktif, SABIT_ZAMAN.minusHours(2), null);
        zimmetKaydet(pasifMonitor, pasif, SABIT_ZAMAN.minusHours(3), SABIT_ZAMAN.minusHours(2));

        mockMvc.perform(get("/api/admin/assignments")
                        .param("aktif", "false")
                        .param("personelId", pasif.getPersonelId())
                        .param("ekipmanTipiId", laptop.getEkipmanTipiId().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(beklenen.getZimmetId()))
                .andExpect(jsonPath("$.icerik[0].personel.ad").value("Test"))
                .andExpect(jsonPath("$.icerik[0].personel.soyad").value("zimmet.admin.pasif"))
                .andExpect(jsonPath("$.icerik[0].personel.departman").value("Zimmet Test Departmanı"))
                .andExpect(jsonPath("$.icerik[0].personel.aktifMi").value(false))
                .andExpect(jsonPath("$.icerik[0].personel.kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].personel.ePosta").doesNotExist());

        mockMvc.perform(get("/api/admin/assignments")
                        .param("ekipmanTipiId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @Test
    void adminAcikZimmetiIadeEderCihaziDepoyaAlirGecmisiVeGuvenliAuditiKorur()
            throws Exception {
        Personel admin = personelKaydet("zimmet.return.admin", adminRolu, true);
        Personel pasifOwner = personelKaydet("zimmet.return.pasif", personelRolu, false);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "RETURN-SECRET-SERIAL", EkipmanDurum.PERSONELDE);
        cihaz.setKullanimDisiNedeni("Legacy kullanım dışı nedeni");
        ekipmanRepository.saveAndFlush(cihaz);
        ZimmetlemeBilgileri zimmet = zimmetKaydet(
                cihaz, pasifOwner, SABIT_ZAMAN.minusDays(10), null
        );

        mockMvc.perform(patch("/api/admin/assignments/{id}/return", zimmet.getZimmetId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isNoContent());

        ZimmetlemeBilgileri guncel = zimmetRepository.findById(zimmet.getZimmetId()).orElseThrow();
        assertEquals(SABIT_ZAMAN, guncel.getIadeTarihi());
        assertEquals(1L, zimmetRepository.count());
        EkipmanBilgileri guncelCihaz = ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow();
        assertEquals(EkipmanDurum.DEPODA, guncelCihaz.getEkipmanDurum());
        assertNull(guncelCihaz.getKullanimDisiNedeni());

        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT eylem_tipi, personel_id, hedef_personel_id, detay FROM audit_log"
        );
        assertEquals(AuditEylem.ZIMMET_IADE_EDILDI.name(), audit.get("eylem_tipi").toString());
        assertEquals(admin.getPersonelId(), audit.get("personel_id"));
        assertEquals(pasifOwner.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals("Zimmet iade edildi.", audit.get("detay"));
    }

    @Test
    void kapaliMissingVeGecersizZimmetIadesiKontrolluVeYanEtkisizdir() throws Exception {
        Personel admin = personelKaydet("zimmet.return.validation", adminRolu, true);
        Personel owner = personelKaydet("zimmet.return.owner", personelRolu, true);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "RETURN-CLOSED-1", EkipmanDurum.DEPODA);
        ZimmetlemeBilgileri kapali = zimmetKaydet(
                cihaz, owner, SABIT_ZAMAN.minusDays(2), SABIT_ZAMAN.minusDays(1)
        );

        mockMvc.perform(patch("/api/admin/assignments/{id}/return", kapali.getZimmetId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/admin/assignments/{id}/return", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/admin/assignments/{id}/return", "bozuk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/assignments/{id}/return",
                        "ABCDEFAB-1234-1234-1234-ABCDEFABCDEF")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());

        assertEquals(SABIT_ZAMAN.minusDays(1), zimmetRepository.findById(kapali.getZimmetId())
                .orElseThrow().getIadeTarihi());
        assertEquals(EkipmanDurum.DEPODA, ekipmanRepository.findById(cihaz.getEkipmanId())
                .orElseThrow().getEkipmanDurum());
        assertEquals(0L, auditRepository.count());
    }

    @Test
    void listeFiltreVeSayfalamaDogrulamasiSozlesmeyeUyar() throws Exception {
        Personel admin = personelKaydet("zimmet.list.validation", adminRolu, true);
        Personel personel = personelKaydet("zimmet.list.personel", personelRolu, true);

        for (String yol : new String[]{
                "/api/assignments/my?page=-1",
                "/api/assignments/my?size=0",
                "/api/assignments/my?size=101"
        }) {
            mockMvc.perform(get(yol)
                            .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL)))
                    .andExpect(status().isBadRequest());
        }
        for (String yol : new String[]{
                "/api/admin/assignments?page=-1",
                "/api/admin/assignments?size=0",
                "/api/admin/assignments?size=101",
                "/api/admin/assignments?aktif=metin",
                "/api/admin/assignments?ekipmanTipiId=0",
                "/api/admin/assignments?ekipmanTipiId=-1",
                "/api/admin/assignments?ekipmanTipiId=metin",
                "/api/admin/assignments?personelId=bozuk",
                "/api/admin/assignments?personelId=ABCDEFAB-1234-1234-1234-ABCDEFABCDEF"
        }) {
            mockMvc.perform(get(yol)
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/admin/assignments")
                        .param("personelId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @Test
    void rolAktiflikPrincipalVeJwtDbUyumuButunZimmetYuzeylerindeKorunur()
            throws Exception {
        Personel personel = personelKaydet("zimmet.security.personel", personelRolu, true);
        Personel hr = personelKaydet("zimmet.security.hr", hrRolu, true);
        Personel yonetici = personelKaydet("zimmet.security.yonetici", yoneticiRolu, true);
        Personel pasifAdmin = personelKaydet("zimmet.security.pasif", adminRolu, false);
        Personel dbYonetici = personelKaydet("zimmet.security.mismatch", yoneticiRolu, true);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "SECURITY-RETURN-1", EkipmanDurum.PERSONELDE);
        ZimmetlemeBilgileri zimmet = zimmetKaydet(
                cihaz, personel, SABIT_ZAMAN.minusDays(1), null
        );

        for (Personel yetkisiz : new Personel[]{personel, hr, yonetici}) {
            RolAdi rol = yetkisiz.getRol().getRolAdi();
            mockMvc.perform(get("/api/admin/assignments")
                            .header(HttpHeaders.AUTHORIZATION, bearer(yetkisiz, rol)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(patch("/api/admin/assignments/{id}/return", zimmet.getZimmetId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(yetkisiz, rol)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get("/api/admin/assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasifAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dbYonetici, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/assignments/my")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(
                                UUID.randomUUID().toString(), RolAdi.PERSONEL
                        )))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/assignments/my"))
                .andExpect(status().isUnauthorized());

        assertNull(zimmetRepository.findById(zimmet.getZimmetId()).orElseThrow().getIadeTarihi());
        assertEquals(0L, auditRepository.count());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disTransactionHatasindaIadeCihazVeAuditBirlikteRollbackOlur() {
        Personel admin = personelKaydet("zimmet.rollback.admin", adminRolu, true);
        Personel owner = personelKaydet("zimmet.rollback.owner", personelRolu, true);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "ROLLBACK-RETURN-1", EkipmanDurum.PERSONELDE);
        cihaz.setKullanimDisiNedeni("Rollback ile korunacak legacy neden");
        ekipmanRepository.saveAndFlush(cihaz);
        ZimmetlemeBilgileri zimmet = zimmetKaydet(
                cihaz, owner, SABIT_ZAMAN.minusDays(4), null
        );

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.iadeEtVeHataFirlat(
                        admin.getPersonelId(), zimmet.getZimmetId()
                )
        );

        assertEquals("rollback-probe", exception.getMessage());
        assertEquals(SABIT_ZAMAN, rollbackTestService.transactionIcindekiIadeTarihi());
        assertEquals(EkipmanDurum.DEPODA, rollbackTestService.transactionIcindekiCihazDurumu());
        assertNull(rollbackTestService.transactionIcindekiKullanimDisiNedeni());
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertNull(jdbcTemplate.queryForObject(
                "SELECT iade_tarihi FROM zimmetleme_bilgileri WHERE zimmet_id = ?",
                LocalDateTime.class,
                zimmet.getZimmetId()
        ));
        assertEquals(EkipmanDurum.PERSONELDE, ekipmanRepository.findById(cihaz.getEkipmanId())
                .orElseThrow().getEkipmanDurum());
        assertEquals("Rollback ile korunacak legacy neden", ekipmanRepository
                .findById(cihaz.getEkipmanId()).orElseThrow().getKullanimDisiNedeni());
        assertEquals(0L, auditRepository.count());
    }

    @Test
    void task7OnayininUrettigiZimmetKendiListesindeGorulurVeAdminTarafindanIadeEdilir()
            throws Exception {
        Personel admin = personelKaydet("zimmet.task7.admin", adminRolu, true);
        Personel owner = personelKaydet("zimmet.task7.owner", personelRolu, true);
        DurumTuru bekliyor = durumKaydet(TalepDurum.ONAY_BEKLIYOR);
        durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTalep talep = talepKaydet(owner, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "TASK7-TASK8-1", EkipmanDurum.DEPODA);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isNoContent());

        ZimmetlemeBilgileri zimmet = zimmetRepository.findByTalepId(
                talep.getTalepId()
        ).orElseThrow();
        mockMvc.perform(get("/api/assignments/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner, RolAdi.PERSONEL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(zimmet.getZimmetId()))
                .andExpect(jsonPath("$.icerik[0].seriNo").value("TASK7-TASK8-1"));

        mockMvc.perform(patch("/api/admin/assignments/{id}/return", zimmet.getZimmetId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isNoContent());

        assertEquals(SABIT_ZAMAN, zimmetRepository.findById(zimmet.getZimmetId())
                .orElseThrow().getIadeTarihi());
        assertEquals(EkipmanDurum.DEPODA, ekipmanRepository.findById(cihaz.getEkipmanId())
                .orElseThrow().getEkipmanDurum());
        assertEquals(2L, auditRepository.count());
    }

    @Test
    void readEndpointleriPersistenceVeyaAuditYanEtkisiOlusturmaz() throws Exception {
        Personel admin = personelKaydet("zimmet.read.admin", adminRolu, true);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "READ-ASSIGNMENT-1", EkipmanDurum.PERSONELDE);
        zimmetKaydet(cihaz, admin, SABIT_ZAMAN.minusHours(1), null);

        mockMvc.perform(get("/api/assignments/my")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/assignments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk());

        assertEquals(1L, zimmetRepository.count());
        assertEquals(0L, auditRepository.count());
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Rol rol(RolAdi rolAdi) {
        return switch (rolAdi) {
            case PERSONEL -> personelRolu;
            case HR -> hrRolu;
            case YONETICI -> yoneticiRolu;
            case ADMIN -> adminRolu;
        };
    }

    private Departman departmanKaydet(String ad) {
        Departman kayit = new Departman();
        kayit.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(kayit);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, boolean aktif) {
        Personel kayit = new Personel();
        kayit.setKullaniciAdi(kullaniciAdi);
        kayit.setParolaHash("test-hash");
        kayit.setAdi("Test");
        kayit.setSoyadi(kullaniciAdi);
        kayit.setDogumTarihi(LocalDate.of(1990, 1, 1));
        kayit.setePosta(kullaniciAdi + "@test.local");
        kayit.setDepartman(departman);
        kayit.setRol(rol);
        kayit.setCalisiyorMu(aktif);
        kayit.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        kayit.setYillikIzinHakki(14);
        kayit.setProfilFoto("def_pfp.png");
        kayit.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(kayit);
    }

    private EkipmanTipi tipKaydet(String ad) {
        EkipmanTipi kayit = new EkipmanTipi();
        kayit.setTipAdi(ad);
        kayit.setAktifMi(true);
        return tipRepository.saveAndFlush(kayit);
    }

    private EkipmanBilgileri cihazKaydet(
            EkipmanTipi tip,
            String seriNo,
            EkipmanDurum durum
    ) {
        EkipmanBilgileri kayit = new EkipmanBilgileri();
        kayit.setEkipmanTipiId(tip);
        kayit.setMarka("Test Marka");
        kayit.setModel("Test Model");
        kayit.setSeriNo(seriNo);
        kayit.setEkipmanDurum(durum);
        return ekipmanRepository.saveAndFlush(kayit);
    }

    private ZimmetlemeBilgileri zimmetKaydet(
            EkipmanBilgileri cihaz,
            Personel owner,
            LocalDateTime teslim,
            LocalDateTime iade
    ) {
        ZimmetlemeBilgileri kayit = new ZimmetlemeBilgileri();
        kayit.setEkipmanId(cihaz);
        kayit.setPersonelId(owner);
        kayit.setTeslimTarihi(teslim);
        kayit.setIadeTarihi(iade);
        return zimmetRepository.saveAndFlush(kayit);
    }

    private DurumTuru durumKaydet(TalepDurum durum) {
        DurumTuru kayit = new DurumTuru();
        kayit.setDurumKodu(durum);
        return durumRepository.saveAndFlush(kayit);
    }

    private EkipmanTalep talepKaydet(
            Personel owner,
            EkipmanTipi tip,
            DurumTuru durum
    ) {
        EkipmanTalep kayit = new EkipmanTalep();
        kayit.setPersonel(owner);
        kayit.setEkipmanTipi(tip);
        kayit.setTalepDurum(durum);
        kayit.setTalepTarih(SABIT_ZAMAN.minusHours(1));
        return talepRepository.saveAndFlush(kayit);
    }

    private String bearer(Personel aktor, RolAdi rol) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rol);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SabitSaatConfig {
        @Bean
        @Primary
        Clock sabitZimmetSaati() {
            return Clock.fixed(
                    Instant.parse("2026-08-31T11:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfig {
        @Bean
        ZimmetRollbackTestService zimmetRollbackTestService(
                ZimmetService service,
                ZimmetlemeBilgileriRepository zimmetRepository,
                EkipmanBilgileriRepository ekipmanRepository,
                AuditLogRepository auditRepository
        ) {
            return new ZimmetRollbackTestService(
                    service,
                    zimmetRepository,
                    ekipmanRepository,
                    auditRepository
            );
        }
    }

    static class ZimmetRollbackTestService {
        private final ZimmetService service;
        private final ZimmetlemeBilgileriRepository zimmetRepository;
        private final EkipmanBilgileriRepository ekipmanRepository;
        private final AuditLogRepository auditRepository;
        private LocalDateTime transactionIcindekiIadeTarihi;
        private EkipmanDurum transactionIcindekiCihazDurumu;
        private String transactionIcindekiKullanimDisiNedeni;
        private long transactionIcindekiAuditSayisi;

        ZimmetRollbackTestService(
                ZimmetService service,
                ZimmetlemeBilgileriRepository zimmetRepository,
                EkipmanBilgileriRepository ekipmanRepository,
                AuditLogRepository auditRepository
        ) {
            this.service = service;
            this.zimmetRepository = zimmetRepository;
            this.ekipmanRepository = ekipmanRepository;
            this.auditRepository = auditRepository;
        }

        @Transactional
        public void iadeEtVeHataFirlat(String aktorId, String zimmetId) {
            service.zimmetiIadeEt(aktorId, zimmetId);
            ZimmetlemeBilgileri zimmet = zimmetRepository.findById(zimmetId).orElseThrow();
            transactionIcindekiIadeTarihi = zimmet.getIadeTarihi();
            EkipmanBilgileri ekipman = ekipmanRepository
                    .findById(zimmet.getEkipmanId().getEkipmanId())
                    .orElseThrow();
            transactionIcindekiCihazDurumu = ekipman.getEkipmanDurum();
            transactionIcindekiKullanimDisiNedeni = ekipman.getKullanimDisiNedeni();
            transactionIcindekiAuditSayisi = auditRepository.count();
            throw new IllegalStateException("rollback-probe");
        }

        LocalDateTime transactionIcindekiIadeTarihi() {
            return transactionIcindekiIadeTarihi;
        }

        EkipmanDurum transactionIcindekiCihazDurumu() {
            return transactionIcindekiCihazDurumu;
        }

        String transactionIcindekiKullanimDisiNedeni() {
            return transactionIcindekiKullanimDisiNedeni;
        }

        long transactionIcindekiAuditSayisi() {
            return transactionIcindekiAuditSayisi;
        }
    }
}
