package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.EkipmanTipiRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(EkipmanEnvanterControllerIntegrationTest.AuditHatasiTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EkipmanEnvanterControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JWTService jwtService;
    @Autowired private EkipmanBilgileriRepository ekipmanRepository;
    @Autowired private EkipmanTipiRepository ekipmanTipiRepository;
    @Autowired private ZimmetlemeBilgileriRepository zimmetRepository;
    @Autowired private EkipmanTalepRepository ekipmanTalepRepository;
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
    private Personel admin;
    private EkipmanTipi laptop;
    private EkipmanTipi monitor;
    private EkipmanTipi pasifTip;

    @BeforeEach
    void setUp() {
        hataEnjekteAuditService.hataDurumunuAyarla(false);
        auditLogRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM zimmetleme_bilgileri");
        ekipmanTalepRepository.deleteAll();
        ekipmanRepository.deleteAll();
        ekipmanTipiRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM refresh_token");
        jdbcTemplate.update("DELETE FROM personel_yetenek");
        jdbcTemplate.update("DELETE FROM izin_bilgileri");
        jdbcTemplate.update("UPDATE personel SET yonetici_id = NULL");
        personelRepository.deleteAll();
        durumTuruRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        personelRolu = rolKaydet(9401, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(9402, RolAdi.YONETICI);
        hrRolu = rolKaydet(9403, RolAdi.HR);
        adminRolu = rolKaydet(9404, RolAdi.ADMIN);
        departman = departmanKaydet("Envanter Test Departmanı");
        admin = personelKaydet("envanter.admin", adminRolu, true);
        laptop = tipKaydet("Laptop", true);
        monitor = tipKaydet("Monitor", true);
        pasifTip = tipKaydet("Pasif Tip", false);
    }

    @Test
    void adminCihaziBodyDurumunaBakmadanDepodaVeNormalizeOlusturur() throws Exception {
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ekipmanTipiId":%d,"marka":"  Dell  ","model":"   ",
                                 "seriNo":"  SERI-001  ","durum":"KULLANIM_DISI","id":"istemci-id"}
                                """.formatted(laptop.getEkipmanTipiId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.ekipmanTipiId").value(laptop.getEkipmanTipiId()))
                .andExpect(jsonPath("$.ekipmanTipi").value("Laptop"))
                .andExpect(jsonPath("$.marka").value("Dell"))
                .andExpect(jsonPath("$.model").value((Object) null))
                .andExpect(jsonPath("$.seriNo").value("SERI-001"))
                .andExpect(jsonPath("$.durum").value("DEPODA"))
                .andExpect(jsonPath("$.kullanimDisiNedeni").value((Object) null))
                .andExpect(jsonPath("$.acikZimmetVarMi").value(false))
                .andExpect(jsonPath("$.personel").doesNotExist())
                .andExpect(jsonPath("$.talep").doesNotExist());

        EkipmanBilgileri kayit = ekipmanRepository.findAll().getFirst();
        assertEquals(EkipmanDurum.DEPODA, kayit.getEkipmanDurum());
        assertNull(kayit.getKullanimDisiNedeni());
        auditDogrula(AuditEylem.EKIPMAN_EKLENDI, "Ekipman eklendi.");
    }

    @Test
    void adminListeyiAndFiltreKararliSayfalamaVeAcikZimmetBilgisiyleGorur() throws Exception {
        EkipmanBilgileri dell = cihazKaydet(laptop, "Dell", "XPS", "L-002", EkipmanDurum.PERSONELDE);
        cihazKaydet(laptop, "Acer", "Swift", "L-001", EkipmanDurum.DEPODA);
        cihazKaydet(monitor, "Dell", "Ultra", "M-001", EkipmanDurum.DEPODA);
        zimmetKaydet(dell, admin, null);

        mockMvc.perform(get("/api/admin/equipment")
                        .param("page", "0").param("size", "10")
                        .param("ekipmanTipiId", laptop.getEkipmanTipiId().toString())
                        .param("durum", "PERSONELDE").param("arama", " dell ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(dell.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[0].acikZimmetVarMi").value(true))
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(10))
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.toplamSayfa").value(1));

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminListesiTipAdiMarkaModelSeriVeIdSirasiniKorur() throws Exception {
        EkipmanBilgileri ilk = cihazKaydet(laptop, "Acer", null, "A-1", EkipmanDurum.DEPODA);
        EkipmanBilgileri ikinci = cihazKaydet(laptop, "Dell", "A", "D-1", EkipmanDurum.DEPODA);
        EkipmanBilgileri ucuncu = cihazKaydet(monitor, "Acer", "A", "M-1", EkipmanDurum.DEPODA);

        mockMvc.perform(get("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].id").value(ilk.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[1].id").value(ikinci.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[2].id").value(ucuncu.getEkipmanId()));
    }

    @Test
    void pozitifAmaOlmayanTipFiltresiBosSayfaDoner() throws Exception {
        cihazKaydet(laptop, "Dell", null, "L-001", EkipmanDurum.DEPODA);
        mockMvc.perform(get("/api/admin/equipment")
                        .param("ekipmanTipiId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(0))
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/equipment?page=-1&size=20",
            "/api/admin/equipment?page=0&size=0",
            "/api/admin/equipment?page=0&size=101",
            "/api/admin/equipment?ekipmanTipiId=0",
            "/api/admin/equipment?ekipmanTipiId=-1",
            "/api/admin/equipment?ekipmanTipiId=metin",
            "/api/admin/equipment?durum=BILINMIYOR"
    })
    void gecersizListeFiltresi400Doner(String path) throws Exception {
        mockMvc.perform(get(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI", "HR"})
    void adminEnvanterEndpointlerineDigerRollerErisemez(RolAdi rol) throws Exception {
        Personel aktor = personelKaydet("envanter." + rol.name().toLowerCase(), rol(rol), true);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", null, "Y-001", EkipmanDurum.DEPODA);

        mockMvc.perform(get("/api/admin/equipment").header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON).content(validCreateJson("Y-002")))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/equipment/{id}", cihaz.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marka\":\"Yeni\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/equipment/{id}/status", cihaz.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"durum\":\"KULLANIM_DISI\",\"neden\":\"Arızalı\"}"))
                .andExpect(status().isForbidden());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminEndpointiDbRoluAktifligiPrincipalVarligiVeAuthenticationDogrular() throws Exception {
        Personel dbPersonel = personelKaydet("jwt.admin.db.personel", personelRolu, true);
        Personel pasifAdmin = personelKaydet("envanter.pasif.admin", adminRolu, false);

        mockMvc.perform(get("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dbPersonel, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasifAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken("olmayan-admin", RolAdi.ADMIN)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/equipment"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"ekipmanTipiId\":0,\"marka\":\"Dell\",\"seriNo\":\"A\"}",
            "{\"ekipmanTipiId\":1,\"marka\":\"   \",\"seriNo\":\"A\"}",
            "{\"ekipmanTipiId\":1,\"marka\":\"Dell\",\"seriNo\":\"   \"}",
            "{}"
    })
    void gecersizCreateBody400Doner(String body) throws Exception {
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(0L, ekipmanRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifVeOlmayanTipCreateKontrolluReddedilir() throws Exception {
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson(pasifTip.getEkipmanTipiId(), "P-1")))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson(999999, "P-2")))
                .andExpect(status().isNotFound());
        assertEquals(0L, ekipmanRepository.count());
    }

    @Test
    void seriNumarasiCaseInsensitiveTekildir() throws Exception {
        cihazKaydet(laptop, "Dell", null, "Unique-Seri", EkipmanDurum.DEPODA);
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson("unique-seri")))
                .andExpect(status().isConflict());
        assertEquals(1L, ekipmanRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void metinSinirlariTrimSonrasiUygulanir() throws Exception {
        String elliKarakterMarka = "M".repeat(50);
        mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ekipmanTipiId":%d,"marka":"  %s  ","model":null,
                                 "seriNo":"SINIR-1"}
                                """.formatted(laptop.getEkipmanTipiId(), elliKarakterMarka)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.marka").value(elliKarakterMarka));

        EkipmanBilgileri cihaz = ekipmanRepository.findAll().getFirst();
        auditLogRepository.deleteAll();
        String besYuzKarakterNeden = "N".repeat(500);
        statusPatch(
                cihaz,
                "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"  "
                        + besYuzKarakterNeden + "  \"}",
                204
        );
        assertEquals(
                besYuzKarakterNeden,
                ekipmanRepository.findById(cihaz.getEkipmanId())
                        .orElseThrow()
                        .getKullanimDisiNedeni()
        );
    }

    @Test
    void paralelAyniSeriCreateIslemlerindenYalnizBiriBasariliOlur()
            throws Exception {
        Personel ikinciAdmin = personelKaydet("envanter.ikinci.admin", adminRolu, true);
        CountDownLatch basla = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> ilk = executor.submit(() -> {
                basla.await();
                return createStatus(
                        admin,
                        laptop.getEkipmanTipiId(),
                        "YARIS-SERI"
                );
            });
            Future<Integer> ikinci = executor.submit(() -> {
                basla.await();
                return createStatus(
                        ikinciAdmin,
                        monitor.getEkipmanTipiId(),
                        "yaris-seri"
                );
            });
            basla.countDown();

            assertEquals(
                    java.util.List.of(201, 409),
                    java.util.List.of(ilk.get(), ikinci.get()).stream()
                            .sorted()
                            .toList()
            );
        }

        assertEquals(1L, ekipmanRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void metadataPartialGuncellemeModelTemizlemeVeGuvenliAuditUretir() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", "XPS", "M-001", EkipmanDurum.KULLANIM_DISI);
        cihaz.setKullanimDisiNedeni("Arızalı");
        ekipmanRepository.saveAndFlush(cihaz);

        mockMvc.perform(patch("/api/admin/equipment/{id}", cihaz.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marka\":\"  Lenovo \",\"model\":\"   \",\"seriNo\":\"  M-002 \"}"))
                .andExpect(status().isNoContent());

        EkipmanBilgileri guncel = ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow();
        assertEquals("Lenovo", guncel.getMarka());
        assertNull(guncel.getModel());
        assertEquals("M-002", guncel.getSeriNo());
        assertEquals(laptop.getEkipmanTipiId(), guncel.getEkipmanTipiId().getEkipmanTipiId());
        assertEquals(EkipmanDurum.KULLANIM_DISI, guncel.getEkipmanDurum());
        assertEquals("Arızalı", guncel.getKullanimDisiNedeni());
        auditDogrula(AuditEylem.EKIPMAN_GUNCELLENDI, "Ekipman güncellendi.");
    }

    @Test
    void metadataBosVeyaGecersizBody400Doner() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", "XPS", "V-001", EkipmanDurum.DEPODA);
        for (String body : new String[]{"{}", "{\"marka\":\"   \"}", "{\"seriNo\":\"   \"}"}) {
            mockMvc.perform(patch("/api/admin/equipment/{id}", cihaz.getEkipmanId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void metadataNormalizeNoOpPersistenceVeAuditYanEtkisiOlusturmaz() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", "XPS", "N-001", EkipmanDurum.DEPODA);
        mockMvc.perform(patch("/api/admin/equipment/{id}", cihaz.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"marka\":\" Dell \",\"model\":\"XPS\",\"seriNo\":\" N-001 \"}"))
                .andExpect(status().isNoContent());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void metadataDuplicateSeri409DonerVeKaydiKorur() throws Exception {
        EkipmanBilgileri ilk = cihazKaydet(laptop, "Dell", null, "DUP-1", EkipmanDurum.DEPODA);
        EkipmanBilgileri ikinci = cihazKaydet(laptop, "Dell", null, "DUP-2", EkipmanDurum.DEPODA);
        mockMvc.perform(patch("/api/admin/equipment/{id}", ikinci.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"seriNo\":\"dup-1\"}"))
                .andExpect(status().isConflict());
        assertEquals("DUP-1", ekipmanRepository.findById(ilk.getEkipmanId()).orElseThrow().getSeriNo());
        assertEquals("DUP-2", ekipmanRepository.findById(ikinci.getEkipmanId()).orElseThrow().getSeriNo());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "0"})
    void canonicalOlmayanMetadataVeStatusIdsi400Doner(String id) throws Exception {
        mockMvc.perform(patch("/api/admin/equipment/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marka\":\"Dell\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/equipment/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durum\":\"DEPODA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void canonicalAmaOlmayanCihaz404Doner() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(patch("/api/admin/equipment/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marka\":\"Dell\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/admin/equipment/{id}/status", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"durum\":\"DEPODA\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void depodakiCihazKullanimDisinaAlinirNedeniGuncellenirVeDepoyaDoner() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", null, "S-001", EkipmanDurum.DEPODA);
        statusPatch(cihaz, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"  Arızalı  \"}", 204);
        assertEquals("Arızalı", ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow().getKullanimDisiNedeni());
        auditLogRepository.deleteAll();

        statusPatch(cihaz, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"Bakımda\"}", 204);
        assertEquals("Bakımda", ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow().getKullanimDisiNedeni());
        auditLogRepository.deleteAll();

        statusPatch(cihaz, "{\"durum\":\"DEPODA\"}", 204);
        EkipmanBilgileri depoda = ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow();
        assertEquals(EkipmanDurum.DEPODA, depoda.getEkipmanDurum());
        assertNull(depoda.getKullanimDisiNedeni());
        auditDogrula(AuditEylem.EKIPMAN_GUNCELLENDI, "Ekipman güncellendi.");
    }

    @Test
    void statusNoOpAuditOlusturmaz() throws Exception {
        EkipmanBilgileri depoda = cihazKaydet(laptop, "Dell", null, "NO-1", EkipmanDurum.DEPODA);
        statusPatch(depoda, "{\"durum\":\"DEPODA\",\"neden\":null}", 204);
        assertEquals(0L, auditLogRepository.count());

        EkipmanBilgileri disi = cihazKaydet(laptop, "Dell", null, "NO-2", EkipmanDurum.KULLANIM_DISI);
        disi.setKullanimDisiNedeni("Arızalı");
        ekipmanRepository.saveAndFlush(disi);
        statusPatch(disi, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\" Arızalı \"}", 204);
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void statusGecersizGecisVeNedenKurallariniKontrolluReddeder() throws Exception {
        EkipmanBilgileri depoda = cihazKaydet(laptop, "Dell", null, "G-1", EkipmanDurum.DEPODA);
        statusPatch(depoda, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"   \"}", 400);
        statusPatch(depoda, "{\"durum\":\"PERSONELDE\"}", 409);

        EkipmanBilgileri disi = cihazKaydet(laptop, "Dell", null, "G-2", EkipmanDurum.KULLANIM_DISI);
        disi.setKullanimDisiNedeni("Arızalı");
        ekipmanRepository.saveAndFlush(disi);
        statusPatch(disi, "{\"durum\":\"DEPODA\",\"neden\":\"taşınmamalı\"}", 400);

        EkipmanBilgileri personelde = cihazKaydet(laptop, "Dell", null, "G-3", EkipmanDurum.PERSONELDE);
        statusPatch(personelde, "{\"durum\":\"DEPODA\"}", 409);
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void acikZimmetliCihazKullanimDisinaAlinamaz() throws Exception {
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "Dell", null, "Z-1", EkipmanDurum.DEPODA);
        zimmetKaydet(cihaz, admin, null);
        statusPatch(cihaz, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"Arızalı\"}", 409);
        assertEquals(EkipmanDurum.DEPODA, ekipmanRepository.findById(cihaz.getEkipmanId()).orElseThrow().getEkipmanDurum());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void auditHatasiCreateMetadataVeStatusIslemleriniRollbackEder() throws Exception {
        hataEnjekteAuditService.hataDurumunuAyarla(true);
        try {
            mockMvc.perform(post("/api/admin/equipment")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content(validCreateJson("R-1")))
                    .andExpect(status().isInternalServerError());
            assertFalse(ekipmanRepository.existsBySeriNoIgnoreCase("R-1"));

            EkipmanBilgileri metadata = cihazKaydet(laptop, "Dell", "XPS", "R-2", EkipmanDurum.DEPODA);
            mockMvc.perform(patch("/api/admin/equipment/{id}", metadata.getEkipmanId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"marka\":\"Lenovo\"}"))
                    .andExpect(status().isInternalServerError());
            assertEquals("Dell", ekipmanRepository.findById(metadata.getEkipmanId()).orElseThrow().getMarka());

            EkipmanBilgileri status = cihazKaydet(laptop, "Dell", null, "R-3", EkipmanDurum.DEPODA);
            statusPatch(status, "{\"durum\":\"KULLANIM_DISI\",\"neden\":\"Arızalı\"}", 500);
            EkipmanBilgileri degismeyen = ekipmanRepository.findById(status.getEkipmanId()).orElseThrow();
            assertEquals(EkipmanDurum.DEPODA, degismeyen.getEkipmanDurum());
            assertNull(degismeyen.getKullanimDisiNedeni());
        } finally {
            hataEnjekteAuditService.hataDurumunuAyarla(false);
        }
        assertEquals(0L, auditLogRepository.count());
    }

    private void statusPatch(EkipmanBilgileri cihaz, String body, int expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/admin/equipment/{id}/status", cihaz.getEkipmanId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    private int createStatus(Personel aktor, String seriNo) throws Exception {
        return createStatus(aktor, laptop.getEkipmanTipiId(), seriNo);
    }

    private int createStatus(
            Personel aktor,
            int ekipmanTipiId,
            String seriNo
    ) throws Exception {
        return mockMvc.perform(post("/api/admin/equipment")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateJson(ekipmanTipiId, seriNo)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String validCreateJson(String seriNo) {
        return validCreateJson(laptop.getEkipmanTipiId(), seriNo);
    }

    private String validCreateJson(int tipId, String seriNo) {
        return """
                {"ekipmanTipiId":%d,"marka":"Dell","model":"XPS","seriNo":"%s"}
                """.formatted(tipId, seriNo);
    }

    private Rol rol(RolAdi rolAdi) {
        return switch (rolAdi) {
            case PERSONEL -> personelRolu;
            case YONETICI -> yoneticiRolu;
            case HR -> hrRolu;
            case ADMIN -> adminRolu;
        };
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman d = new Departman();
        d.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(d);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, boolean aktif) {
        Personel p = new Personel();
        p.setKullaniciAdi(kullaniciAdi);
        p.setParolaHash("test-hash");
        p.setAdi("Test");
        p.setSoyadi(kullaniciAdi);
        p.setDogumTarihi(LocalDate.of(1990, 1, 1));
        p.setePosta(kullaniciAdi + "@test.local");
        p.setDepartman(departman);
        p.setRol(rol);
        p.setCalisiyorMu(aktif);
        p.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        p.setYillikIzinHakki(14);
        p.setProfilFoto("def_pfp.png");
        p.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(p);
    }

    private EkipmanTipi tipKaydet(String ad, boolean aktif) {
        EkipmanTipi tip = new EkipmanTipi();
        tip.setTipAdi(ad);
        tip.setAktifMi(aktif);
        return ekipmanTipiRepository.saveAndFlush(tip);
    }

    private EkipmanBilgileri cihazKaydet(
            EkipmanTipi tip,
            String marka,
            String model,
            String seri,
            EkipmanDurum durum
    ) {
        EkipmanBilgileri cihaz = new EkipmanBilgileri();
        cihaz.setEkipmanTipiId(tip);
        cihaz.setMarka(marka);
        cihaz.setModel(model);
        cihaz.setSeriNo(seri);
        cihaz.setEkipmanDurum(durum);
        return ekipmanRepository.saveAndFlush(cihaz);
    }

    private ZimmetlemeBilgileri zimmetKaydet(
            EkipmanBilgileri cihaz,
            Personel personel,
            LocalDateTime iade
    ) {
        ZimmetlemeBilgileri z = new ZimmetlemeBilgileri();
        z.setEkipmanId(cihaz);
        z.setPersonelId(personel);
        z.setTeslimTarihi(LocalDateTime.of(2026, 8, 28, 9, 0));
        z.setIadeTarihi(iade);
        return zimmetRepository.saveAndFlush(z);
    }

    private String bearer(Personel aktor, RolAdi rol) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rol);
    }

    private void auditDogrula(AuditEylem eylem, String detay) {
        assertEquals(1L, auditLogRepository.count());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT eylem_tipi, personel_id, hedef_personel_id, kullanici_adi, detay
                FROM audit_log
                """);
        assertEquals(eylem.name(), audit.get("eylem_tipi"));
        assertEquals(admin.getPersonelId(), audit.get("personel_id"));
        assertEquals(admin.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(admin.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals(detay, audit.get("detay"));
        assertFalse(detay.contains("SERI"));
        assertFalse(detay.contains(laptop.getTipAdi()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditHatasiTestConfig {
        @Bean
        @Primary
        HataEnjekteAuditService hataEnjekteAuditService(AuditLogRepository repository) {
            return new HataEnjekteAuditService(repository);
        }
    }

    static class HataEnjekteAuditService extends AuditService {
        private boolean hata;

        HataEnjekteAuditService(AuditLogRepository repository) {
            super(repository);
        }

        void hataDurumunuAyarla(boolean hata) {
            this.hata = hata;
        }

        @Override
        public void logKaydetOrtakTransaction(
                AuditEylem eylem,
                Personel aktor,
                String kullaniciAdi,
                String detay,
                Personel hedef
        ) {
            if (hata) {
                throw new IllegalStateException("Audit hata enjeksiyonu.");
            }
            super.logKaydetOrtakTransaction(eylem, aktor, kullaniciAdi, detay, hedef);
        }
    }
}
