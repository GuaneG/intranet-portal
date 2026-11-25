package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
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
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.EkipmanTipiService;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(EkipmanTipiControllerIntegrationTest.AuditHatasiTestConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EkipmanTipiControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;
    @Autowired
    private EkipmanTipiRepository ekipmanTipiRepository;
    @Autowired
    private EkipmanTalepRepository ekipmanTalepRepository;
    @Autowired
    private EkipmanBilgileriRepository ekipmanBilgileriRepository;
    @Autowired
    private DurumTuruRepository durumTuruRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private HataEnjekteAuditService hataEnjekteAuditService;
    @Autowired
    private EkipmanTipiService ekipmanTipiService;

    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;
    private Rol adminRolu;
    private Departman departman;
    private Personel admin;
    private EkipmanTipi klavye;
    private EkipmanTipi monitor;
    private EkipmanTipi pasifTip;

    @BeforeAll
    void turkceCaseInsensitiveEkipmanTipiSemasiniKur() {
        jdbcTemplate.execute("""
                ALTER TABLE ekipman_tipi
                MODIFY COLUMN tip_adi VARCHAR(50)
                CHARACTER SET utf8mb4
                COLLATE utf8mb4_tr_0900_ai_ci
                NOT NULL
                """);
    }

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

        personelRolu = rolKaydet(9301, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(9302, RolAdi.YONETICI);
        hrRolu = rolKaydet(9303, RolAdi.HR);
        adminRolu = rolKaydet(9304, RolAdi.ADMIN);
        departman = departmanKaydet("Ekipman Test Departmanı");
        admin = personelKaydet("ekipman.admin", adminRolu, true);
        klavye = tipKaydet("Klavye", true);
        monitor = tipKaydet("Monitor", true);
        pasifTip = tipKaydet("Pasif Tip", false);
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifRollerYalnizAktifTipleriGuvenliVeAlfabetikGorur(RolAdi rol)
            throws Exception {
        Personel aktor = rol == RolAdi.ADMIN
                ? admin
                : personelKaydet("katalog." + rol.name().toLowerCase(), rol(rol), true);

        mockMvc.perform(get("/api/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(klavye.getEkipmanTipiId()))
                .andExpect(jsonPath("$[0].ad").value("Klavye"))
                .andExpect(jsonPath("$[1].id").value(monitor.getEkipmanTipiId()))
                .andExpect(jsonPath("$[1].ad").value("Monitor"))
                .andExpect(jsonPath("$[0].aktifMi").doesNotExist())
                .andExpect(jsonPath("$[0].tipAdi").doesNotExist())
                .andExpect(jsonPath("$[0].stok").doesNotExist());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifMissingVeUnauthenticatedKatalogAktoruKontrolluReddedilir()
            throws Exception {
        Personel pasif = personelKaydet("katalog.pasif", personelRolu, false);

        mockMvc.perform(get("/api/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasif, RolAdi.PERSONEL)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken("olmayan-aktor", RolAdi.PERSONEL)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/equipment-types"))
                .andExpect(status().isUnauthorized());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI", "HR"})
    void adminTipEndpointlerineAdminDisiRollerErisemez(RolAdi rol)
            throws Exception {
        Personel aktor = personelKaydet("admin.yasak." + rol.name().toLowerCase(), rol(rol), true);

        mockMvc.perform(get("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"Mouse\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":false}"))
                .andExpect(status().isForbidden());

        assertTrue(ekipmanTipiRepository.findById(monitor.getEkipmanTipiId()).orElseThrow().isAktifMi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminEndpointiDbRoluAktifligiVePrincipalVarliginiDogrular()
            throws Exception {
        Personel jwtAdminDbPersonel = personelKaydet("jwt.admin.db.personel", personelRolu, true);
        Personel pasifAdmin = personelKaydet("pasif.admin", adminRolu, false);

        mockMvc.perform(get("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(jwtAdminDbPersonel, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasifAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken("olmayan-admin", RolAdi.ADMIN)))
                .andExpect(status().isNotFound());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminTipleriAndFiltreKararliSayfalamaIleGorur() throws Exception {
        EkipmanTipi ikinciMonitor = tipKaydet("Monitor Standı", false);

        mockMvc.perform(get("/api/admin/equipment-types")
                        .param("page", "0")
                        .param("size", "1")
                        .param("aktif", "false")
                        .param("arama", "monitor")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(ikinciMonitor.getEkipmanTipiId()))
                .andExpect(jsonPath("$.icerik[0].ad").value("Monitor Standı"))
                .andExpect(jsonPath("$.icerik[0].aktifMi").value(false))
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(1))
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.toplamSayfa").value(1));

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/admin/equipment-types?page=-1&size=20",
            "/api/admin/equipment-types?page=0&size=0",
            "/api/admin/equipment-types?page=0&size=101"
    })
    void adminTipSayfalamaSinirlari400Doner(String yol) throws Exception {
        mockMvc.perform(get(yol)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminTipOlustururTrimlerAktifBaslatirVeGuvenliAuditYazar()
            throws Exception {
        mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"  Mouse  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.ad").value("Mouse"))
                .andExpect(jsonPath("$.aktifMi").value(true));

        EkipmanTipi kayit = ekipmanTipiRepository.findAll().stream()
                .filter(t -> "Mouse".equals(t.getTipAdi()))
                .findFirst()
                .orElseThrow();
        assertTrue(kayit.isAktifMi());
        auditDogrula(AuditEylem.EKIPMAN_TIPI_EKLENDI, "Ekipman tipi eklendi.");
    }

    @Test
    void caseInsensitiveDuplicateOlusturma409VeYanEtkisizdir() throws Exception {
        long oncekiTipSayisi = ekipmanTipiRepository.count();

        mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"  MONITOR  \"}"))
                .andExpect(status().isConflict());

        assertEquals(oncekiTipSayisi, ekipmanTipiRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void baskaKaydaDuplicateOlacakPatch409VeYanEtkisizdir() throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", klavye.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"  MONITOR  \"}"))
                .andExpect(status().isConflict());

        assertEquals("Klavye", ekipmanTipiRepository.findById(klavye.getEkipmanTipiId())
                .orElseThrow().getTipAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ad\":\"   \"}"})
    void gecersizCreateBody400Doner(String body) throws Exception {
        mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elliKarakterdenUzunCreateAdi400Doner() throws Exception {
        mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminTipAdiniVeAktifliginiTekAuditIleGunceller() throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"  Ekran  \",\"aktifMi\":false}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        EkipmanTipi guncel = ekipmanTipiRepository.findById(monitor.getEkipmanTipiId()).orElseThrow();
        assertEquals("Ekran", guncel.getTipAdi());
        assertFalse(guncel.isAktifMi());
        auditDogrula(AuditEylem.EKIPMAN_TIPI_GUNCELLENDI, "Ekipman tipi güncellendi.");
    }

    @Test
    void pasifTipTekrarAktiflestirilebilir() throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", pasifTip.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":true}"))
                .andExpect(status().isNoContent());

        assertTrue(ekipmanTipiRepository.findById(pasifTip.getEkipmanTipiId()).orElseThrow().isAktifMi());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void normalizeAyniPatchNoOpVeAuditYanEtkisizdir() throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"  Monitor  \",\"aktifMi\":true}"))
                .andExpect(status().isNoContent());

        assertEquals("Monitor", ekipmanTipiRepository.findById(monitor.getEkipmanTipiId())
                .orElseThrow().getTipAdi());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"ad\":null}", "{\"ad\":\"   \"}",
            "{\"ad\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}"})
    void gecersizPatchBody400Doner(String body) throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanPatchId400Doner(int id) throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulunmayanPozitifPatchId404Doner() throws Exception {
        mockMvc.perform(patch("/api/admin/equipment-types/{id}", 999999)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void bekleyenTalebiBulunanTipPasifeAlinamaz() throws Exception {
        Personel owner = personelKaydet("pending.owner", personelRolu, true);
        DurumTuru bekliyor = durumKaydet(TalepDurum.ONAY_BEKLIYOR);
        talepKaydet(owner, monitor, bekliyor);

        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":false}"))
                .andExpect(status().isConflict());

        assertTrue(ekipmanTipiRepository.findById(monitor.getEkipmanTipiId()).orElseThrow().isAktifMi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void terminalTalepVeFizikselEnvanterPasiflestirmeyiEngellemez() throws Exception {
        Personel owner = personelKaydet("terminal.owner", personelRolu, true);
        DurumTuru onaylandi = durumKaydet(TalepDurum.ONAYLANDI);
        talepKaydet(owner, monitor, onaylandi);
        cihazKaydet(monitor, "ENV-001");

        mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktifMi\":false}"))
                .andExpect(status().isNoContent());

        assertFalse(ekipmanTipiRepository.findById(monitor.getEkipmanTipiId()).orElseThrow().isAktifMi());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void auditHatasiCreateIsleminiTamamenRollbackEder() throws Exception {
        hataEnjekteAuditService.hataDurumunuAyarla(true);
        try {
            mockMvc.perform(post("/api/admin/equipment-types")
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ad\":\"Rollback Tipi\"}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            hataEnjekteAuditService.hataDurumunuAyarla(false);
        }

        assertFalse(ekipmanTipiRepository.existsByTipAdiIgnoreCase("Rollback Tipi"));
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void auditHatasiPatchIsleminiTamamenRollbackEder() throws Exception {
        hataEnjekteAuditService.hataDurumunuAyarla(true);
        try {
            mockMvc.perform(patch("/api/admin/equipment-types/{id}", monitor.getEkipmanTipiId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ad\":\"Rollback Ekranı\",\"aktifMi\":false}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            hataEnjekteAuditService.hataDurumunuAyarla(false);
        }

        EkipmanTipi degismeyen = ekipmanTipiRepository.findById(monitor.getEkipmanTipiId())
                .orElseThrow();
        assertEquals("Monitor", degismeyen.getTipAdi());
        assertTrue(degismeyen.isAktifMi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void aktifTipGetirIdVarlikVeAktiflikKurallariniKilitliSorgudaKorur() {
        assertEquals(
                monitor.getEkipmanTipiId(),
                ekipmanTipiService.aktifTipGetir(monitor.getEkipmanTipiId())
                        .getEkipmanTipiId()
        );
        assertThrows(
                com.jforce.backend.exception.GecersizIstekException.class,
                () -> ekipmanTipiService.aktifTipGetir(0)
        );
        assertThrows(
                com.jforce.backend.exception.KaynakBulunamadiException.class,
                () -> ekipmanTipiService.aktifTipGetir(999999)
        );
        assertThrows(
                com.jforce.backend.exception.KaynakCakismasiException.class,
                () -> ekipmanTipiService.aktifTipGetir(pasifTip.getEkipmanTipiId())
        );
    }

    @Test
    void paralelCaseInsensitiveCreateSonuclari201Ve409OlarakKontrolludur()
            throws Exception {
        Personel ikinciAdmin = personelKaydet("ikinci.admin", adminRolu, true);
        CountDownLatch basla = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> ilk = executor.submit(() -> {
                basla.await();
                return createStatus(admin, "Kulaklık");
            });
            Future<Integer> ikinci = executor.submit(() -> {
                basla.await();
                return createStatus(ikinciAdmin, "KULAKLIK");
            });
            basla.countDown();

            List<Integer> durumlar = List.of(ilk.get(), ikinci.get()).stream().sorted().toList();
            assertEquals(List.of(201, 409), durumlar);
        }

        assertEquals(1L, ekipmanTipiRepository.findAll().stream()
                .filter(t -> "Kulaklık".equalsIgnoreCase(t.getTipAdi()))
                .count());
        assertEquals(1L, auditLogRepository.count());
    }

    private int createStatus(Personel aktor, String ad) throws Exception {
        return mockMvc.perform(post("/api/admin/equipment-types")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ad\":\"" + ad + "\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
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
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(yeniDepartman);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, boolean aktif) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi("Test");
        personel.setSoyadi(kullaniciAdi);
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2020, 1, 1));
        personel.setYillikIzinHakki(14);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);
        return personelRepository.saveAndFlush(personel);
    }

    private EkipmanTipi tipKaydet(String ad, boolean aktif) {
        EkipmanTipi tip = new EkipmanTipi();
        tip.setTipAdi(ad);
        tip.setAktifMi(aktif);
        return ekipmanTipiRepository.saveAndFlush(tip);
    }

    private DurumTuru durumKaydet(TalepDurum durum) {
        DurumTuru entity = new DurumTuru();
        entity.setDurumKodu(durum);
        return durumTuruRepository.saveAndFlush(entity);
    }

    private EkipmanTalep talepKaydet(Personel owner, EkipmanTipi tip, DurumTuru durum) {
        EkipmanTalep talep = new EkipmanTalep();
        talep.setPersonel(owner);
        talep.setEkipmanTipi(tip);
        talep.setTalepDurum(durum);
        talep.setTalepTarih(LocalDateTime.of(2026, 8, 28, 9, 0));
        return ekipmanTalepRepository.saveAndFlush(talep);
    }

    private EkipmanBilgileri cihazKaydet(EkipmanTipi tip, String seriNo) {
        EkipmanBilgileri cihaz = new EkipmanBilgileri();
        cihaz.setEkipmanTipiId(tip);
        cihaz.setMarka("Test Marka");
        cihaz.setModel("Test Model");
        cihaz.setSeriNo(seriNo);
        cihaz.setEkipmanDurum(EkipmanDurum.DEPODA);
        return ekipmanBilgileriRepository.saveAndFlush(cihaz);
    }

    private String bearer(Personel aktor, RolAdi rol) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rol);
    }

    private void auditDogrula(AuditEylem eylem, String detay) {
        assertEquals(1L, auditLogRepository.count());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
                SELECT eylem_tipi, personel_id, hedef_personel_id,
                       kullanici_adi, detay
                FROM audit_log
                """);
        assertEquals(eylem.name(), audit.get("eylem_tipi"));
        assertEquals(admin.getPersonelId(), audit.get("personel_id"));
        assertEquals(admin.getPersonelId(), audit.get("hedef_personel_id"));
        assertEquals(admin.getKullaniciAdi(), audit.get("kullanici_adi"));
        assertEquals(detay, audit.get("detay"));
        assertFalse(detay.contains(monitor.getTipAdi()));
        assertFalse(detay.contains(monitor.getEkipmanTipiId().toString()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class AuditHatasiTestConfig {

        @Bean
        @Primary
        HataEnjekteAuditService hataEnjekteAuditService(
                AuditLogRepository auditLogRepository
        ) {
            return new HataEnjekteAuditService(auditLogRepository);
        }
    }

    static class HataEnjekteAuditService extends AuditService {

        private boolean hata;

        HataEnjekteAuditService(AuditLogRepository auditLogRepository) {
            super(auditLogRepository);
        }

        void hataDurumunuAyarla(boolean hata) {
            this.hata = hata;
        }

        @Override
        public void logKaydetOrtakTransaction(
                AuditEylem auditEylem,
                Personel aktor,
                String kullaniciAdi,
                String detayMsg,
                Personel hedefPersonel
        ) {
            if (hata) {
                throw new IllegalStateException("Audit hata enjeksiyonu.");
            }
            super.logKaydetOrtakTransaction(
                    auditEylem,
                    aktor,
                    kullaniciAdi,
                    detayMsg,
                    hedefPersonel
            );
        }
    }
}
