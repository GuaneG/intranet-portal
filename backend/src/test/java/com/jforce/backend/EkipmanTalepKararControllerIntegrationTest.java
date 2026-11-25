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
import com.jforce.backend.service.EkipmanTalepKararService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(EkipmanTalepKararControllerIntegrationTest.RollbackTestConfig.class)
class EkipmanTalepKararControllerIntegrationTest extends AbstractIntegrationTest {

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
    @Autowired private EkipmanKararRollbackTestService rollbackTestService;

    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol adminRolu;
    private Rol hrRolu;
    private Departman departman;
    private Personel yonetici;
    private Personel dogrudanCalisan;
    private EkipmanTipi laptop;
    private DurumTuru bekliyor;

    @BeforeEach
    void setUp() {
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

        personelRolu = rolKaydet(9601, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(9602, RolAdi.YONETICI);
        adminRolu = rolKaydet(9603, RolAdi.ADMIN);
        hrRolu = rolKaydet(9604, RolAdi.HR);
        departman = departmanKaydet("Karar Test Departmanı");
        yonetici = personelKaydet("karar.yonetici", yoneticiRolu, true, null);
        dogrudanCalisan = personelKaydet("karar.calisan", personelRolu, true, yonetici);
        laptop = tipKaydet("Laptop");
        bekliyor = durumKaydet(TalepDurum.ONAY_BEKLIYOR);
    }

    @Test
    void yoneticiTakimTalepKuyrugunaUlasir() throws Exception {
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(talep.getTalepId()));
    }

    @Test
    void yoneticiVeAdminYetkiliTalepIcinUygunCihazlariGorur() throws Exception {
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "RED-DEVICE-1", EkipmanDurum.DEPODA);
        Personel admin = personelKaydet("karar.available.admin", adminRolu, true, null);

        mockMvc.perform(get("/api/equipment-requests/{talepId}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(cihaz.getEkipmanId()));

        mockMvc.perform(get("/api/equipment-requests/{talepId}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(cihaz.getEkipmanId()));
    }

    @Test
    void yoneticiYalnizAktifDogrudanCalisanTalepleriniGorur() throws Exception {
        Personel digerYonetici = personelKaydet("karar.diger.yonetici", yoneticiRolu, true, null);
        Personel dolayli = personelKaydet("karar.dolayli", personelRolu, true, dogrudanCalisan);
        Personel baskaTakim = personelKaydet("karar.baska.takim", personelRolu, true, digerYonetici);
        Personel pasif = personelKaydet("karar.pasif", personelRolu, false, yonetici);
        EkipmanTalep dogrudanTalep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        talepKaydet(dolayli, laptop, bekliyor);
        talepKaydet(baskaTakim, laptop, bekliyor);
        talepKaydet(pasif, laptop, bekliyor);
        talepKaydet(yonetici, laptop, bekliyor);

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(dogrudanTalep.getTalepId()));
    }

    @Test
    void legacySelfManagerYoneticiKendiTalebiniTakimdaVeUygunCihazdaGoremez()
            throws Exception {
        yonetici.setYonetici(yonetici);
        personelRepository.saveAndFlush(yonetici);
        EkipmanTalep kendiTalebi = talepKaydet(yonetici, laptop, bekliyor);
        cihazKaydet(laptop, "SELF-MANAGER-1", EkipmanDurum.DEPODA);

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0))
                .andExpect(jsonPath("$.icerik.length()").value(0));

        mockMvc.perform(get("/api/equipment-requests/{talepId}/available-equipment",
                        kendiTalebi.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isNotFound());
    }

    @Test
    void yoneticiKuyrukFiltreleriniAndUygularVeKararliSiralar() throws Exception {
        EkipmanTipi monitor = tipKaydet("Monitor");
        DurumTuru reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep eski = talepKaydet(dogrudanCalisan, laptop, bekliyor,
                LocalDateTime.of(2026, 8, 29, 10, 0));
        EkipmanTalep yeni = talepKaydet(dogrudanCalisan, laptop, bekliyor,
                LocalDateTime.of(2026, 8, 30, 11, 0));
        talepKaydet(dogrudanCalisan, laptop, reddedildi,
                LocalDateTime.of(2026, 8, 30, 12, 0));
        talepKaydet(dogrudanCalisan, monitor, bekliyor,
                LocalDateTime.of(2026, 8, 30, 13, 0));

        mockMvc.perform(get("/api/equipment-requests/team")
                        .param("durum", "ONAY_BEKLIYOR")
                        .param("ekipmanTipiId", laptop.getEkipmanTipiId().toString())
                        .param("page", "0").param("size", "20")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik[0].id").value(yeni.getTalepId()))
                .andExpect(jsonPath("$.icerik[1].id").value(eski.getTalepId()));

        mockMvc.perform(get("/api/equipment-requests/team")
                        .param("ekipmanTipiId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @Test
    void adminSirketGeneliAktifPasifGecmisiVeKendiLegacyTalebiniGorur() throws Exception {
        Personel admin = personelKaydet("karar.admin", adminRolu, true, null);
        Personel pasif = personelKaydet("karar.admin.pasif", personelRolu, false, null);
        EkipmanTalep aktifTalep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanTalep pasifTalep = talepKaydet(pasif, laptop, bekliyor);
        EkipmanTalep adminTalebi = talepKaydet(admin, laptop, bekliyor);

        mockMvc.perform(get("/api/admin/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.icerik[?(@.id == '%s')]", aktifTalep.getTalepId()).exists())
                .andExpect(jsonPath("$.icerik[?(@.id == '%s')]", pasifTalep.getTalepId()).exists())
                .andExpect(jsonPath("$.icerik[?(@.id == '%s')]", adminTalebi.getTalepId()).exists());
    }

    @Test
    void adminFiltreleriAndUygularCanonicalOlmayanPersonelIdyiReddeder() throws Exception {
        Personel admin = personelKaydet("karar.admin.filtre", adminRolu, true, null);
        Personel hedef = personelKaydet("karar.admin.hedef", personelRolu, true, null);
        Personel diger = personelKaydet("karar.admin.diger", personelRolu, true, null);
        EkipmanTipi monitor = tipKaydet("Admin Monitor");
        DurumTuru reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep beklenen = talepKaydet(hedef, laptop, bekliyor);
        talepKaydet(hedef, laptop, reddedildi);
        talepKaydet(hedef, monitor, bekliyor);
        talepKaydet(diger, laptop, bekliyor);

        mockMvc.perform(get("/api/admin/equipment-requests")
                        .param("durum", "ONAY_BEKLIYOR")
                        .param("ekipmanTipiId", laptop.getEkipmanTipiId().toString())
                        .param("personelId", hedef.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(beklenen.getTalepId()));

        mockMvc.perform(get("/api/admin/equipment-requests")
                        .param("personelId", UUID.randomUUID().toString().toUpperCase())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/equipment-requests")
                        .param("personelId", UUID.randomUUID().toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));

        mockMvc.perform(get("/api/admin/equipment-requests")
                        .param("ekipmanTipiId", "999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @Test
    void kararKuyruguGuvenliDtoKararVerenVeZimmetOzetiniDoner() throws Exception {
        Personel kararVeren = personelKaydet("karar.onceki.admin", adminRolu, true, null);
        DurumTuru onaylandi = durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, onaylandi);
        talep.setAciklama("Güvenli açıklama");
        talep.setIslemYapanId(kararVeren);
        talep.setIslemTarihi(LocalDateTime.of(2026, 8, 30, 12, 0));
        talep.setKararNotu("Onay notu");
        ekipmanTalepRepository.saveAndFlush(talep);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "DTO-SERI-1", EkipmanDurum.PERSONELDE);
        zimmetKaydet(cihaz, dogrudanCalisan, talep,
                LocalDateTime.of(2026, 8, 30, 13, 0), null);

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].talepSahibi.ad").value("Test"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.soyad").value("karar.calisan"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.departman").value("Karar Test Departmanı"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.profilFoto").value("def_pfp.png"))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.aktifMi").value(true))
                .andExpect(jsonPath("$.icerik[0].talepSahibi.kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].talepSahibi.ePosta").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kararVeren.ad").value("Test"))
                .andExpect(jsonPath("$.icerik[0].kararVeren.soyad").value("karar.onceki.admin"))
                .andExpect(jsonPath("$.icerik[0].zimmet.id").isString())
                .andExpect(jsonPath("$.icerik[0].zimmet.ekipmanId").value(cihaz.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[0].personelId").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].parolaHash").doesNotExist());
    }

    @Test
    void uygunCihazlarYalnizTalepTipindekiDepodaCihazlariGuvenliDoner() throws Exception {
        EkipmanTipi monitor = tipKaydet("Uygun Monitor");
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri uygun = cihazKaydet(laptop, "UYGUN-1", EkipmanDurum.DEPODA);
        cihazKaydet(laptop, "PERSONELDE-1", EkipmanDurum.PERSONELDE);
        cihazKaydet(laptop, "KULLANIM-DISI-1", EkipmanDurum.KULLANIM_DISI);
        cihazKaydet(monitor, "YANLIS-TIP-1", EkipmanDurum.DEPODA);

        mockMvc.perform(get("/api/equipment-requests/{talepId}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].id").value(uygun.getEkipmanId()))
                .andExpect(jsonPath("$.icerik[0].marka").value("Test Marka"))
                .andExpect(jsonPath("$.icerik[0].model").value("Test Model"))
                .andExpect(jsonPath("$.icerik[0].seriNo").value("UYGUN-1"))
                .andExpect(jsonPath("$.icerik[0].durum").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].kullanimDisiNedeni").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].zimmet").doesNotExist());
    }

    @Test
    void uygunCihazKapsamiYoneticiVeAdminIcinGuvenli404IleKorunur() throws Exception {
        Personel admin = personelKaydet("karar.scope.admin", adminRolu, true, null);
        Personel digerYonetici = personelKaydet("karar.scope.diger.yonetici", yoneticiRolu, true, null);
        Personel baskaTakim = personelKaydet("karar.scope.baska", personelRolu, true, digerYonetici);
        Personel dolayli = personelKaydet("karar.scope.dolayli", personelRolu, true, dogrudanCalisan);
        Personel pasif = personelKaydet("karar.scope.pasif", personelRolu, false, yonetici);
        EkipmanTalep baskaTalep = talepKaydet(baskaTakim, laptop, bekliyor);
        EkipmanTalep dolayliTalep = talepKaydet(dolayli, laptop, bekliyor);
        EkipmanTalep pasifTalep = talepKaydet(pasif, laptop, bekliyor);
        EkipmanTalep adminTalebi = talepKaydet(admin, laptop, bekliyor);

        for (EkipmanTalep kapsamDisi : new EkipmanTalep[]{baskaTalep, dolayliTalep, pasifTalep}) {
            mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", kapsamDisi.getTalepId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", adminTalebi.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void kararEndpointleriAktiflikVarlikJwtDbRolUyumunuDogrular() throws Exception {
        Personel pasifYonetici = personelKaydet("karar.pasif.yonetici", yoneticiRolu, false, null);
        Personel dbAdmin = personelKaydet("karar.db.admin", adminRolu, true, null);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasifYonetici, RolAdi.YONETICI)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(dbAdmin, RolAdi.YONETICI)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "
                                + jwtService.generateToken(UUID.randomUUID().toString(), RolAdi.YONETICI)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/equipment-requests/team"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "ADMIN"})
    void yalnizYoneticiTakimKuyrugunaErisir(RolAdi rol) throws Exception {
        Personel aktor = personelKaydet("karar.team." + rol.name().toLowerCase(), rol(rol), true, null);
        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR", "YONETICI"})
    void yalnizAdminSirketKuyrugunaErisir(RolAdi rol) throws Exception {
        Personel aktor = personelKaydet("karar.admin.queue." + rol.name().toLowerCase(), rol(rol), true, null);
        mockMvc.perform(get("/api/admin/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "HR"})
    void personelVeHrUygunCihazEndpointineErisemez(RolAdi rol) throws Exception {
        Personel aktor = personelKaydet("karar.available." + rol.name().toLowerCase(), rol(rol), true, null);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(aktor, rol)))
                .andExpect(status().isForbidden());
    }

    @Test
    void gecersizFiltreUuidVeSayfalama400PozitifMissingFiltreBos200Doner() throws Exception {
        Personel admin = personelKaydet("karar.validation.admin", adminRolu, true, null);
        for (String yol : new String[]{
                "/api/equipment-requests/team?page=-1&size=20",
                "/api/equipment-requests/team?page=0&size=0",
                "/api/equipment-requests/team?page=0&size=101",
                "/api/equipment-requests/team?ekipmanTipiId=0",
                "/api/equipment-requests/team?ekipmanTipiId=-1",
                "/api/equipment-requests/team?ekipmanTipiId=metin",
                "/api/equipment-requests/team?durum=BILINMIYOR"
        }) {
            mockMvc.perform(get(yol).header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                    .andExpect(status().isBadRequest());
        }
        for (String yol : new String[]{
                "/api/admin/equipment-requests?page=-1",
                "/api/admin/equipment-requests?size=101",
                "/api/admin/equipment-requests?ekipmanTipiId=0",
                "/api/admin/equipment-requests?personelId=bozuk",
                "/api/admin/equipment-requests?durum=BILINMIYOR"
        }) {
            mockMvc.perform(get(yol).header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", "bozuk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isNotFound());
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        for (String sorgu : new String[]{"?page=-1", "?size=0", "?size=101"}) {
            mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment" + sorgu,
                            talep.getTalepId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void readEndpointleriAuditVeyaPersistenceYanEtkisiOlusturmaz() throws Exception {
        Personel admin = personelKaydet("karar.read.admin", adminRolu, true, null);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        cihazKaydet(laptop, "READ-ONLY-1", EkipmanDurum.DEPODA);
        long talepSayisi = ekipmanTalepRepository.count();
        long cihazSayisi = ekipmanBilgileriRepository.count();

        mockMvc.perform(get("/api/equipment-requests/team")
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/equipment-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/equipment-requests/{id}/available-equipment", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI)))
                .andExpect(status().isOk());

        assertEquals(0L, auditLogRepository.count());
        assertEquals(talepSayisi, ekipmanTalepRepository.count());
        assertEquals(cihazSayisi, ekipmanBilgileriRepository.count());
    }

    @Test
    void yoneticiSecilenCihazlaTalebiAtomikOnaylarVeGuvenliAuditOlusturur()
            throws Exception {
        DurumTuru onaylandi = durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        talep.setAciklama("Audit'e taşınmaması gereken açıklama");
        ekipmanTalepRepository.saveAndFlush(talep);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "APPROVE-SERI-1", EkipmanDurum.DEPODA);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isNoContent());

        EkipmanTalep guncelTalep = ekipmanTalepRepository.findById(talep.getTalepId()).orElseThrow();
        EkipmanBilgileri guncelCihaz = ekipmanBilgileriRepository.findById(cihaz.getEkipmanId()).orElseThrow();
        ZimmetlemeBilgileri zimmet = zimmetRepository.findByTalepId(talep.getTalepId()).orElseThrow();
        assertEquals(onaylandi.getDurumId(), guncelTalep.getTalepDurum().getDurumId());
        assertEquals(yonetici.getPersonelId(), guncelTalep.getIslemYapanId().getPersonelId());
        assertNull(guncelTalep.getKararNotu());
        assertEquals(EkipmanDurum.PERSONELDE, guncelCihaz.getEkipmanDurum());
        assertEquals(dogrudanCalisan.getPersonelId(), zimmet.getPersonelId().getPersonelId());
        assertEquals(cihaz.getEkipmanId(), zimmet.getEkipmanId().getEkipmanId());
        assertEquals(talep.getTalepId(), zimmet.getTalep().getTalepId());

        var audit = auditLogRepository.findAll();
        assertEquals(1, audit.size());
        assertEquals(AuditEylem.EKIPMAN_TALEBI_ONAYLANDI, audit.getFirst().getEylemTipi());
        assertEquals(yonetici.getPersonelId(), audit.getFirst().getPersonel().getPersonelId());
        assertEquals(dogrudanCalisan.getPersonelId(),
                audit.getFirst().getHedefPersonel().getPersonelId());
        assertEquals("Ekipman talebi onaylandı.", audit.getFirst().getDetay());
        assertFalse(audit.getFirst().getDetay().contains(cihaz.getSeriNo()));
        assertFalse(audit.getFirst().getDetay().contains(talep.getTalepId()));
    }

    @Test
    void adminTalebiTrimlenmisNedenleReddederVeNedeniAudittenGizler()
            throws Exception {
        Personel admin = personelKaydet("karar.reject.admin", adminRolu, true, null);
        DurumTuru reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);

        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"  Stok planına uygun değil.  \"}"))
                .andExpect(status().isNoContent());

        EkipmanTalep guncel = ekipmanTalepRepository.findById(talep.getTalepId()).orElseThrow();
        assertEquals(reddedildi.getDurumId(), guncel.getTalepDurum().getDurumId());
        assertEquals("Stok planına uygun değil.", guncel.getKararNotu());
        assertEquals(admin.getPersonelId(), guncel.getIslemYapanId().getPersonelId());
        assertTrue(guncel.getIslemTarihi() != null);
        assertEquals(0, zimmetRepository.count());

        var audit = auditLogRepository.findAll();
        assertEquals(1, audit.size());
        assertEquals(AuditEylem.EKIPMAN_TALEBI_REDDEDILDI, audit.getFirst().getEylemTipi());
        assertEquals("Ekipman talebi reddedildi.", audit.getFirst().getDetay());
        assertFalse(audit.getFirst().getDetay().contains("Stok planına"));
    }

    @Test
    void adminSirketGenelindekiTalebiSeciliCihazlaOnaylayabilir() throws Exception {
        Personel admin = personelKaydet("karar.approve.admin", adminRolu, true, null);
        durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "ADMIN-APPROVE-1", EkipmanDurum.DEPODA);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isNoContent());

        assertEquals(TalepDurum.ONAYLANDI, talepDurumu(talep.getTalepId()));
        assertEquals(EkipmanDurum.PERSONELDE, ekipmanBilgileriRepository
                .findById(cihaz.getEkipmanId()).orElseThrow().getEkipmanDurum());
        assertEquals(1L, zimmetRepository.count());
        var audit = auditLogRepository.findAll();
        assertEquals(1, audit.size());
        assertEquals(admin.getPersonelId(), audit.getFirst().getPersonel().getPersonelId());
        assertEquals(dogrudanCalisan.getPersonelId(),
                audit.getFirst().getHedefPersonel().getPersonelId());
    }

    @Test
    void onayYanlisTipVeyaUygunOlmayanCihazdaAtomik409Doner() throws Exception {
        durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTipi monitor = tipKaydet("Karar Monitor");
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri yanlisTip = cihazKaydet(monitor, "WRONG-TYPE-1", EkipmanDurum.DEPODA);
        EkipmanBilgileri personelde = cihazKaydet(laptop, "NOT-DEPOT-1", EkipmanDurum.PERSONELDE);

        for (EkipmanBilgileri cihaz : new EkipmanBilgileri[]{yanlisTip, personelde}) {
            mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                    .andExpect(status().isConflict());
        }

        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(talep.getTalepId()));
        assertEquals(0, zimmetRepository.count());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void acikZimmetliDepodaCihazVePasifOwnerGuvenliReddedilir() throws Exception {
        Personel admin = personelKaydet("karar.invariant.admin", adminRolu, true, null);
        Personel pasifOwner = personelKaydet("karar.invariant.pasif", personelRolu, false, null);
        EkipmanTalep aktifTalep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanTalep pasifTalep = talepKaydet(pasifOwner, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "LEGACY-OPEN-DEPOT-1", EkipmanDurum.DEPODA);
        zimmetKaydet(cihaz, dogrudanCalisan, null,
                LocalDateTime.of(2026, 8, 29, 9, 0), null);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", aktifTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", pasifTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"Uygun değil\"}"))
                .andExpect(status().isConflict());

        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(aktifTalep.getTalepId()));
        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(pasifTalep.getTalepId()));
        assertEquals(1L, zimmetRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void yetkiKontroluTerminalDurumdanOnceUygulanirVeOzHedeflerGizlenir()
            throws Exception {
        DurumTuru reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        Personel digerYonetici = personelKaydet("karar.mutation.diger", yoneticiRolu, true, null);
        Personel baskaTakim = personelKaydet("karar.mutation.baska", personelRolu, true, digerYonetici);
        EkipmanTalep baskaTerminal = talepKaydet(baskaTakim, laptop, reddedildi);
        EkipmanTalep kendiTerminal = talepKaydet(yonetici, laptop, reddedildi);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "SCOPE-ORDER-1", EkipmanDurum.DEPODA);

        for (EkipmanTalep kapsamDisi : new EkipmanTalep[]{baskaTerminal, kendiTerminal}) {
            mockMvc.perform(patch("/api/equipment-requests/{id}/approve", kapsamDisi.getTalepId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                    .andExpect(status().isNotFound());
        }

        Personel admin = personelKaydet("karar.mutation.self.admin", adminRolu, true, null);
        EkipmanTalep adminKendiTerminal = talepKaydet(admin, laptop, reddedildi);
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", adminKendiTerminal.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"Reddedildi\"}"))
                .andExpect(status().isNotFound());

        assertEquals(0, auditLogRepository.count());
        assertEquals(0, zimmetRepository.count());
    }

    @Test
    void kararMutationlariUuidBodyDurumOwnerVeRolKurallariniDogrular() throws Exception {
        Personel admin = personelKaydet("karar.validation.mutation.admin", adminRolu, true, null);
        DurumTuru reddedildi = durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep terminal = talepKaydet(dogrudanCalisan, laptop, reddedildi);
        EkipmanTalep bekleyenTalep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "VALIDATION-1", EkipmanDurum.DEPODA);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", terminal.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", "bozuk")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", bekleyenTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"bozuk\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", bekleyenTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", bekleyenTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", bekleyenTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());

        Personel personel = personelKaydet("karar.validation.personel", personelRolu, true, null);
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", bekleyenTalep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(personel, RolAdi.PERSONEL))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"Uygun değil\"}"))
                .andExpect(status().isForbidden());

        assertEquals(0, zimmetRepository.count());
        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void mutationlarNoncanonicalVeMissingIdleriVeRedNotuSinirlariniDogrular()
            throws Exception {
        Personel admin = personelKaydet("karar.edge.admin", adminRolu, true, null);
        durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "EDGE-DEVICE-1", EkipmanDurum.DEPODA);
        String noncanonical = "ABCDEFAB-1234-1234-1234-ABCDEFABCDEF";

        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", noncanonical)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"Uygun değil\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + noncanonical + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":null}"))
                .andExpect(status().isBadRequest());

        String tamBesYuzKarakter = "x".repeat(500);
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin, RolAdi.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"  " + tamBesYuzKarakter + "  \"}"))
                .andExpect(status().isNoContent());

        EkipmanTalep guncel = ekipmanTalepRepository.findById(talep.getTalepId()).orElseThrow();
        assertEquals(tamBesYuzKarakter, guncel.getKararNotu());
        assertEquals(TalepDurum.REDDEDILDI, talepDurumu(talep.getTalepId()));
        assertEquals(1L, auditLogRepository.count());
        assertEquals(0L, zimmetRepository.count());
    }

    @Test
    void mutationlarHrUnauthenticatedPasifMissingVeJwtDbRolUyumsuzAktoruReddeder()
            throws Exception {
        Personel hr = personelKaydet("karar.security.hr", hrRolu, true, null);
        Personel pasifYonetici = personelKaydet(
                "karar.security.pasif", yoneticiRolu, false, null
        );
        Personel dbAdmin = personelKaydet("karar.security.dbadmin", adminRolu, true, null);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "SECURITY-DEVICE-1", EkipmanDurum.DEPODA);
        String onayBody = "{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}";
        String redBody = "{\"kararNotu\":\"Uygun değil\"}";

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hr, RolAdi.HR))
                        .contentType(MediaType.APPLICATION_JSON).content(onayBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", talep.getTalepId())
                        .contentType(MediaType.APPLICATION_JSON).content(redBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pasifYonetici, RolAdi.YONETICI))
                        .contentType(MediaType.APPLICATION_JSON).content(onayBody))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(
                                UUID.randomUUID().toString(), RolAdi.YONETICI
                        ))
                        .contentType(MediaType.APPLICATION_JSON).content(redBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", talep.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(dbAdmin, RolAdi.YONETICI))
                        .contentType(MediaType.APPLICATION_JSON).content(onayBody))
                .andExpect(status().isForbidden());

        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(talep.getTalepId()));
        assertEquals(EkipmanDurum.DEPODA, ekipmanBilgileriRepository
                .findById(cihaz.getEkipmanId()).orElseThrow().getEkipmanDurum());
        assertEquals(0L, zimmetRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pendingLegacySelfManagerKendiTalebiniOnaylayamazVeyaReddedemez()
            throws Exception {
        yonetici.setYonetici(yonetici);
        personelRepository.saveAndFlush(yonetici);
        EkipmanTalep kendiTalebi = talepKaydet(yonetici, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "SELF-MUTATION-1", EkipmanDurum.DEPODA);

        mockMvc.perform(patch("/api/equipment-requests/{id}/approve", kendiTalebi.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ekipmanId\":\"" + cihaz.getEkipmanId() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/equipment-requests/{id}/reject", kendiTalebi.getTalepId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(yonetici, RolAdi.YONETICI))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kararNotu\":\"Uygun değil\"}"))
                .andExpect(status().isNotFound());

        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(kendiTalebi.getTalepId()));
        assertEquals(EkipmanDurum.DEPODA, ekipmanBilgileriRepository
                .findById(cihaz.getEkipmanId()).orElseThrow().getEkipmanDurum());
        assertEquals(0L, zimmetRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "YONETICI")
    void disTransactionHatasindaTalepCihazZimmetVeAuditBirlikteRollbackOlur() {
        durumKaydet(TalepDurum.ONAYLANDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);
        EkipmanBilgileri cihaz = cihazKaydet(laptop, "ROLLBACK-APPROVE-1", EkipmanDurum.DEPODA);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.onaylaVeHataFirlat(
                        yonetici.getPersonelId(), talep.getTalepId(), cihaz.getEkipmanId()
                )
        );

        assertEquals("rollback-probe", exception.getMessage());
        assertEquals(TalepDurum.ONAYLANDI, rollbackTestService.transactionIcindekiDurum());
        assertEquals(EkipmanDurum.PERSONELDE, rollbackTestService.transactionIcindekiCihazDurumu());
        assertEquals(1L, rollbackTestService.transactionIcindekiZimmetSayisi());
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(talep.getTalepId()));
        assertEquals(EkipmanDurum.DEPODA, ekipmanBilgileriRepository.findById(cihaz.getEkipmanId())
                .orElseThrow().getEkipmanDurum());
        assertEquals(0L, zimmetRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void redSonrasiDisTransactionHatasindaTalepVeAuditBirlikteRollbackOlur() {
        Personel admin = personelKaydet("karar.rollback.red.admin", adminRolu, true, null);
        durumKaydet(TalepDurum.REDDEDILDI);
        EkipmanTalep talep = talepKaydet(dogrudanCalisan, laptop, bekliyor);

        IllegalStateException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.reddetVeHataFirlat(
                        admin.getPersonelId(), talep.getTalepId(), "  Red nedeni  "
                )
        );

        assertEquals("rollback-probe", exception.getMessage());
        assertEquals(TalepDurum.REDDEDILDI, rollbackTestService.transactionIcindekiDurum());
        assertEquals("Red nedeni", rollbackTestService.transactionIcindekiKararNotu());
        assertEquals(1L, rollbackTestService.transactionIcindekiAuditSayisi());
        assertEquals(TalepDurum.ONAY_BEKLIYOR, talepDurumu(talep.getTalepId()));
        assertNull(talepKararNotu(talep.getTalepId()));
        assertEquals(0L, auditLogRepository.count());
    }

    private Rol rolKaydet(int id, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman kayit = new Departman();
        kayit.setDepartmanAdi(ad);
        return departmanRepository.saveAndFlush(kayit);
    }

    private Personel personelKaydet(String kullaniciAdi, Rol rol, boolean aktif, Personel yonetici) {
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
        kayit.setYonetici(yonetici);
        return personelRepository.saveAndFlush(kayit);
    }

    private EkipmanTipi tipKaydet(String ad) {
        EkipmanTipi kayit = new EkipmanTipi();
        kayit.setTipAdi(ad);
        kayit.setAktifMi(true);
        return ekipmanTipiRepository.saveAndFlush(kayit);
    }

    private DurumTuru durumKaydet(TalepDurum durum) {
        DurumTuru kayit = new DurumTuru();
        kayit.setDurumKodu(durum);
        return durumTuruRepository.saveAndFlush(kayit);
    }

    private EkipmanTalep talepKaydet(Personel owner, EkipmanTipi tip, DurumTuru durum) {
        return talepKaydet(owner, tip, durum, LocalDateTime.of(2026, 8, 30, 10, 0));
    }

    private EkipmanTalep talepKaydet(Personel owner, EkipmanTipi tip, DurumTuru durum,
                                     LocalDateTime tarih) {
        EkipmanTalep kayit = new EkipmanTalep();
        kayit.setPersonel(owner);
        kayit.setEkipmanTipi(tip);
        kayit.setTalepDurum(durum);
        kayit.setTalepTarih(tarih);
        return ekipmanTalepRepository.saveAndFlush(kayit);
    }

    private void zimmetKaydet(EkipmanBilgileri cihaz, Personel owner, EkipmanTalep talep,
                              LocalDateTime teslim, LocalDateTime iade) {
        com.jforce.backend.model.entity.ZimmetlemeBilgileri kayit =
                new com.jforce.backend.model.entity.ZimmetlemeBilgileri();
        kayit.setEkipmanId(cihaz);
        kayit.setPersonelId(owner);
        kayit.setTalep(talep);
        kayit.setTeslimTarihi(teslim);
        kayit.setIadeTarihi(iade);
        zimmetRepository.saveAndFlush(kayit);
    }

    private Rol rol(RolAdi rolAdi) {
        return switch (rolAdi) {
            case PERSONEL -> personelRolu;
            case YONETICI -> yoneticiRolu;
            case HR -> hrRolu;
            case ADMIN -> adminRolu;
        };
    }

    private EkipmanBilgileri cihazKaydet(EkipmanTipi tip, String seriNo, EkipmanDurum durum) {
        EkipmanBilgileri kayit = new EkipmanBilgileri();
        kayit.setEkipmanTipiId(tip);
        kayit.setMarka("Test Marka");
        kayit.setModel("Test Model");
        kayit.setSeriNo(seriNo);
        kayit.setEkipmanDurum(durum);
        return ekipmanBilgileriRepository.saveAndFlush(kayit);
    }

    private String bearer(Personel aktor, RolAdi rol) {
        return "Bearer " + jwtService.generateToken(aktor.getPersonelId(), rol);
    }

    private TalepDurum talepDurumu(String talepId) {
        String durum = jdbcTemplate.queryForObject(
                """
                SELECT d.durum_adi
                FROM ekipman_talep t
                JOIN durum_turu d ON d.durum_id = t.talep_durum_id
                WHERE t.talep_id = ?
                """,
                String.class,
                talepId
        );
        return TalepDurum.valueOf(durum);
    }

    private String talepKararNotu(String talepId) {
        return jdbcTemplate.queryForObject(
                "SELECT karar_notu FROM ekipman_talep WHERE talep_id = ?",
                String.class,
                talepId
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfig {

        @Bean
        EkipmanKararRollbackTestService ekipmanKararRollbackTestService(
                EkipmanTalepKararService service,
                EkipmanTalepRepository talepRepository,
                EkipmanBilgileriRepository ekipmanRepository,
                ZimmetlemeBilgileriRepository zimmetRepository,
                AuditLogRepository auditLogRepository
        ) {
            return new EkipmanKararRollbackTestService(
                    service,
                    talepRepository,
                    ekipmanRepository,
                    zimmetRepository,
                    auditLogRepository
            );
        }
    }

    static class EkipmanKararRollbackTestService {

        private final EkipmanTalepKararService service;
        private final EkipmanTalepRepository talepRepository;
        private final EkipmanBilgileriRepository ekipmanRepository;
        private final ZimmetlemeBilgileriRepository zimmetRepository;
        private final AuditLogRepository auditLogRepository;
        private TalepDurum transactionIcindekiDurum;
        private EkipmanDurum transactionIcindekiCihazDurumu;
        private long transactionIcindekiZimmetSayisi;
        private long transactionIcindekiAuditSayisi;
        private String transactionIcindekiKararNotu;

        EkipmanKararRollbackTestService(
                EkipmanTalepKararService service,
                EkipmanTalepRepository talepRepository,
                EkipmanBilgileriRepository ekipmanRepository,
                ZimmetlemeBilgileriRepository zimmetRepository,
                AuditLogRepository auditLogRepository
        ) {
            this.service = service;
            this.talepRepository = talepRepository;
            this.ekipmanRepository = ekipmanRepository;
            this.zimmetRepository = zimmetRepository;
            this.auditLogRepository = auditLogRepository;
        }

        @Transactional
        public void onaylaVeHataFirlat(
                String aktorId,
                String talepId,
                String ekipmanId
        ) {
            service.talebiOnayla(
                    aktorId, talepId, new com.jforce.backend.model.dto.request.EkipmanTalepOnayRequest(ekipmanId)
            );
            transactionIcindekiDurum = talepRepository.findById(talepId)
                    .orElseThrow().getTalepDurum().getDurumKodu();
            transactionIcindekiCihazDurumu = ekipmanRepository.findById(ekipmanId)
                    .orElseThrow().getEkipmanDurum();
            transactionIcindekiZimmetSayisi = zimmetRepository.count();
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException("rollback-probe");
        }

        @Transactional
        public void reddetVeHataFirlat(
                String aktorId,
                String talepId,
                String kararNotu
        ) {
            service.talebiReddet(
                    aktorId,
                    talepId,
                    new com.jforce.backend.model.dto.request.EkipmanTalepRedRequest(kararNotu)
            );
            EkipmanTalep talep = talepRepository.findById(talepId).orElseThrow();
            transactionIcindekiDurum = talep.getTalepDurum().getDurumKodu();
            transactionIcindekiKararNotu = talep.getKararNotu();
            transactionIcindekiAuditSayisi = auditLogRepository.count();
            throw new IllegalStateException("rollback-probe");
        }

        TalepDurum transactionIcindekiDurum() {
            return transactionIcindekiDurum;
        }

        EkipmanDurum transactionIcindekiCihazDurumu() {
            return transactionIcindekiCihazDurumu;
        }

        long transactionIcindekiZimmetSayisi() {
            return transactionIcindekiZimmetSayisi;
        }

        long transactionIcindekiAuditSayisi() {
            return transactionIcindekiAuditSayisi;
        }

        String transactionIcindekiKararNotu() {
            return transactionIcindekiKararNotu;
        }
    }
}
