package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Oda;
import com.jforce.backend.model.entity.Rezervasyon;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.entity.TatilTakvimiKilit;
import com.jforce.backend.model.enums.RezervasyonDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.repository.TatilTakvimiKilitRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import com.jforce.backend.model.entity.AuditLog;
import org.junit.jupiter.params.provider.MethodSource;
import com.jforce.backend.model.dto.request.TatilKaydetRequest;
import com.jforce.backend.service.TatilTakvimiService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;



@AutoConfigureMockMvc
@Import({
        TatilTakvimiControllerIntegrationTest.SabitSaatTestConfig.class,
        TatilTakvimiControllerIntegrationTest.RollbackTestConfiguration.class
})
class TatilTakvimiControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private TatilRepository tatilRepository;

    @Autowired
    private RezervasyonRepository rezervasyonRepository;

    @Autowired
    private OdaRepository odaRepository;

    @Autowired
    private TatilTakvimiKilitRepository tatilTakvimiKilitRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private IzinBilgileriRepository izinBilgileriRepository;

    @Autowired
    private PersonelYetenekRepository personelYetenekRepository;

    @Autowired
    private TatilRollbackTestService rollbackTestService;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    private Rol personelRolu;
    private Personel aktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        rezervasyonRepository.deleteAll();
        odaRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        tatilRepository.deleteAll();

        if (!tatilTakvimiKilitRepository.existsById(1)) {
            tatilTakvimiKilitRepository.saveAndFlush(
                    new TatilTakvimiKilit(1)
            );
        }

         personelRolu = rolKaydet(
                2,
                RolAdi.PERSONEL
        );

        Departman departman =
                departmanKaydet("Bilgi Teknolojileri");

        aktor = personelKaydet(
                "tatil.aktor",
                "Tatil",
                "Aktör",
                personelRolu,
                departman
        );
    }

    @Test
    void hrGelecekTatiliniSilerVeGuvenliAuditOlusturur()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Silinecek Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(0L, tatilRepository.count());

        List<AuditLog> auditKayitlari =
                auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog audit = auditKayitlari.getFirst();

        assertEquals(
                "TATIL_SILINDI",
                audit.getEylemTipi().name()
        );
        assertEquals(
                aktor.getPersonelId(),
                audit.getPersonel().getPersonelId()
        );
        assertEquals(
                aktor.getKullaniciAdi(),
                audit.getKullaniciAdi()
        );
        assertEquals(
                "Tatil silindi.",
                audit.getDetay()
        );
        assertNull(audit.getHedefPersonel());
    }

    @Test
    void gecmisTatilSilinemez()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Geçmiş Tatil",
                LocalDate.of(2026, 8, 21),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Geçmiş tatil kaydı silinemez."
                                )
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Geçmiş Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 8, 21),
                korunan.getTarih()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanTatilIdsiSilmede400Doner(
            int tatilId
    ) throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatilId
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Tatil ID pozitif olmalıdır."
                                )
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminGelecekTatiliniSilebilir()
            throws Exception {

        Rol adminRolu = rolKaydet(
                1,
                RolAdi.ADMIN
        );

        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Admin Tarafından Silinecek Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(0L, tatilRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"PERSONEL", "YONETICI"}
    )
    void personelVeYoneticiTatilSilemez(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case PERSONEL -> personelRolu;
            case YONETICI -> rolKaydet(
                    3,
                    RolAdi.YONETICI
            );
            default -> throw new IllegalArgumentException();
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                rolAdi
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(1L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void jwtHrAmaDbRoluPersonelOlanAktorTatilSilemez()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(1L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"HR", "ADMIN"}
    )
    void pasifHrVeAdminTatilSilemez(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case HR -> rolKaydet(4, RolAdi.HR);
            case ADMIN -> rolKaydet(1, RolAdi.ADMIN);
            default -> throw new IllegalArgumentException();
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        String token = token(
                aktor,
                rolAdi
        );

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(1L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaTatilSilme404Doner()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.HR
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );

        assertEquals(1L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanTatilSilinemez()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );

        assertEquals(1L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bulunmayanTatilSilmede404Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                999999
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Tatil bulunamadı.")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void metinTatilIdsiSilmede400Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        delete(
                                "/api/holidays/{tatilId}",
                                "abc"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataTatilSilmeVeAuditiBirlikteRollbackYapar() {
        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Rollback Silme Tatili",
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 14),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.silVeHataFirlat(
                        aktor.getPersonelId(),
                        tatil.getTatilId()
                )
        );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Rollback Silme Tatili",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                LocalDate.of(2026, 11, 14),
                korunan.getBitisTarihi()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ayniNormalizeTatilBilgileriNoOpOlur()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Şirket Tatili",
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 14),
                TatilKategori.SIRKET,
                TatilGunSuresi.YARIM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "  Şirket Tatili  ",
                                  "tarih": "2026-11-10",
                                  "bitisTarihi": "2026-11-14",
                                  "kategori": "SIRKET",
                                  "gunSuresi": "YARIM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Şirket Tatili",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                LocalDate.of(2026, 11, 14),
                korunan.getBitisTarihi()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.YARIM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanTatilIdsiGuncellemede400Doner(
            int tatilId
    ) throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatilId
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Tatil ID pozitif olmalıdır."
                                )
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tatilBaskaTatilinTarihineTasinamaz()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil guncellenecek = tatilKaydet(
                "Güncellenecek Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.YARIM_GUN
        );

        Tatil mevcut = tatilKaydet(
                "Mevcut Tatil",
                LocalDate.of(2026, 12, 10),
                TatilKategori.RESMI_SABIT,
                TatilGunSuresi.TAM_GUN
        );

        Long guncellenecekOncekiVersion =
                guncellenecek.getVersion();

        Long mevcutOncekiVersion =
                mevcut.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                guncellenecek.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Çakışan Güncelleme",
                                  "tarih": "2026-12-10",
                                  "kategori": "RESMI_DEGISKEN",
                                  "gunSuresi": "TAM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
                                )
                );

        assertEquals(2L, tatilRepository.count());

        Tatil korunanGuncellenecek = tatilRepository
                .findById(guncellenecek.getTatilId())
                .orElseThrow();

        assertEquals(
                "Güncellenecek Tatil",
                korunanGuncellenecek.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunanGuncellenecek.getTarih()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunanGuncellenecek.getKategori()
        );
        assertEquals(
                TatilGunSuresi.YARIM_GUN,
                korunanGuncellenecek.getGunSuresi()
        );
        assertEquals(
                guncellenecekOncekiVersion,
                korunanGuncellenecek.getVersion()
        );

        Tatil korunanMevcut = tatilRepository
                .findById(mevcut.getTatilId())
                .orElseThrow();

        assertEquals(
                "Mevcut Tatil",
                korunanMevcut.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 12, 10),
                korunanMevcut.getTarih()
        );
        assertEquals(
                mevcutOncekiVersion,
                korunanMevcut.getVersion()
        );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminGelecekTatiliniGuncelleyebilir()
            throws Exception {

        Rol adminRolu = rolKaydet(
                1,
                RolAdi.ADMIN
        );

        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Eski Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Admin Güncellemesi",
                                  "tarih": "2026-11-11",
                                  "kategori": "RESMI_DEGISKEN",
                                  "gunSuresi": "YARIM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Tatil guncellenen = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Admin Güncellemesi",
                guncellenen.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 11),
                guncellenen.getTarih()
        );
        assertEquals(1L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"PERSONEL", "YONETICI"}
    )
    void personelVeYoneticiTatilGuncelleyemez(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case PERSONEL -> personelRolu;
            case YONETICI -> rolKaydet(
                    3,
                    RolAdi.YONETICI
            );
            default -> throw new IllegalArgumentException();
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                rolAdi
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Korunan Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void jwtHrAmaDbRoluPersonelOlanAktorTatilGuncelleyemez()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Korunan Tatil",
                korunan.getAd()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"HR", "ADMIN"}
    )
    void pasifHrVeAdminTatilGuncelleyemez(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case HR -> rolKaydet(4, RolAdi.HR);
            case ADMIN -> rolKaydet(1, RolAdi.ADMIN);
            default -> throw new IllegalArgumentException();
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        String token = token(
                aktor,
                rolAdi
        );

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals("Korunan Tatil", korunan.getAd());
        assertEquals(oncekiVersion, korunan.getVersion());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiAktorBulunamazsaTatilGuncelleme404Doner()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.HR
        );

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );

        assertEquals("Korunan Tatil", tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow()
                .getAd());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanTatilGuncellenemez()
            throws Exception {

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );

        assertEquals("Korunan Tatil", tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow()
                .getAd());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bulunmayanTatilGuncellemede404Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                999999
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Tatil bulunamadı.")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void metinTatilIdsiGuncellemede400Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                "abc"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(gecerliTatilJson())
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @MethodSource("gecersizTatilRequestleri")
    void gecersizTatilGuncellemeRequesti400Doner(
            String requestBody
    ) throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Korunan Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void requestBodyOlmadanTatilGuncellenemez()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Korunan Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Korunan Tatil",
                korunan.getAd()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void gelecekTatilGecmisTariheTasinamaz()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Gelecek Tatil",
                LocalDate.of(2026, 11, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Geçmişe Taşınan Tatil",
                                  "tarih": "2026-08-21",
                                  "kategori": "RESMI_DEGISKEN",
                                  "gunSuresi": "YARIM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Tatil tarihi geçmişte olamaz."
                                )
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Gelecek Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void gecmisTatilAyniBilgilerleGonderilseBile409Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Geçmiş Tatil",
                LocalDate.of(2026, 8, 21),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "Geçmiş Tatil",
                                  "tarih": "2026-08-21",
                                  "kategori": "SIRKET",
                                  "gunSuresi": "TAM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Geçmiş tatil kaydı güncellenemez."
                                )
                );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Geçmiş Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 8, 21),
                korunan.getTarih()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrGelecekTatiliniGuncellerVeGuvenliAuditOlusturur()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Eski Tatil Adı",
                LocalDate.of(2026, 10, 10),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        patch(
                                "/api/holidays/{tatilId}",
                                tatil.getTatilId()
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                  "ad": "  Güncellenmiş Tatil  ",
                                  "tarih": "2026-11-10",
                                  "kategori": "RESMI_DEGISKEN",
                                  "gunSuresi": "YARIM_GUN"
                                }
                                """)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Tatil guncellenen = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Güncellenmiş Tatil",
                guncellenen.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                guncellenen.getTarih()
        );
        assertEquals(
                TatilKategori.RESMI_DEGISKEN,
                guncellenen.getKategori()
        );
        assertEquals(
                TatilGunSuresi.YARIM_GUN,
                guncellenen.getGunSuresi()
        );

        List<AuditLog> auditKayitlari =
                auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog audit = auditKayitlari.getFirst();

        assertEquals(
                "TATIL_GUNCELLENDI",
                audit.getEylemTipi().name()
        );
        assertEquals(
                aktor.getPersonelId(),
                audit.getPersonel().getPersonelId()
        );
        assertEquals(
                aktor.getKullaniciAdi(),
                audit.getKullaniciAdi()
        );
        assertEquals(
                "Tatil güncellendi.",
                audit.getDetay()
        );
        assertNull(audit.getHedefPersonel());
    }

    @ParameterizedTest
    @MethodSource("gecersizTatilRequestleri")
    void gecersizTatilRequesti400Doner(
            String requestBody
    ) throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataTatilOlusturmaVeAuditiBirlikteRollbackYapar() {
        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        LocalDate tarih = LocalDate.of(2026, 11, 10);

        TatilKaydetRequest request = new TatilKaydetRequest(
                "Rollback Test Tatili",
                tarih,
                LocalDate.of(2026, 11, 14),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.olusturVeHataFirlat(
                        aktor.getPersonelId(),
                        request
                )
        );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void requestBodyGonderilmezse400Doner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tersTarihAraligi400Doner()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-08-31"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-08-01"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Bitiş tarihi başlangıç tarihinden önce olamaz."
                                )
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void dahilUcYuzAltmısYediGunlukAralik400Doner()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2027-01-02"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Tarih aralığı en fazla 366 gün olabilir."
                                )
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void basariliTatilOlusturmaGuvenliAuditOlusturur()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isCreated());

        List<AuditLog> auditKayitlari =
                auditLogRepository.findAll();

        assertEquals(1, auditKayitlari.size());

        AuditLog audit = auditKayitlari.getFirst();

        assertEquals(
                "TATIL_EKLENDI",
                audit.getEylemTipi().name()
        );
        assertEquals(
                aktor.getPersonelId(),
                audit.getPersonel().getPersonelId()
        );
        assertEquals(
                aktor.getKullaniciAdi(),
                audit.getKullaniciAdi()
        );
        assertEquals(
                "Tatil oluşturuldu.",
                audit.getDetay()
        );
        assertNull(audit.getHedefPersonel());
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void tumAktifRollerTatilTakviminiGoruntuleyebilir(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case ADMIN ->
                    rolKaydet(1, RolAdi.ADMIN);

            case PERSONEL ->
                    personelRolu;

            case YONETICI ->
                    rolKaydet(3, RolAdi.YONETICI);

            case HR ->
                    rolKaydet(4, RolAdi.HR);
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-12-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        rolAdi
                                )
                        ))
                .andExpect(status().isOk());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tatilBulunmayanAralikBosListeDoner()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-01-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanTatilTakvimiGoruntulenemez()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-12-31"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifPrincipalTatilTakviminiGoruntuleyemez()
            throws Exception {

        String token = token(
                aktor,
                RolAdi.PERSONEL
        );

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-12-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner()
            throws Exception {

        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-12-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void oncekiAyBaslayanKesisenTatilGrubuListedeDoner()
            throws Exception {

        tatilKaydet(
                "Ay Sınırını Kesen Tatil",
                LocalDate.of(2026, 7, 29),
                LocalDate.of(2026, 8, 2),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        tatilKaydet(
                "Önce Biten Tatil",
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 28),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(get("/api/holidays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-05")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].tarih")
                                .value("2026-07-29")
                )
                .andExpect(
                        jsonPath("$[0].bitisTarihi")
                                .value("2026-08-02")
                );
    }

    @Test
    void eskiTekGunRequestiAyniBaslangicVeBitisleCalisir()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ad": "Tek Gün Tatili",
                                  "tarih": "2026-10-29",
                                  "kategori": "RESMI_SABIT",
                                  "gunSuresi": "TAM_GUN"
                                }
                                """)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.tarih")
                                .value("2026-10-29")
                )
                .andExpect(
                        jsonPath("$.bitisTarihi")
                                .value("2026-10-29")
                );
    }

    @Test
    void hrCokGunlukTatilGrubuOlusturur()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Kurban Bayramı",
                                "2026-08-24",
                                "2026-08-28"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tarih").value("2026-08-24"))
                .andExpect(jsonPath("$.bitisTarihi").value("2026-08-28"));

        assertEquals(1L, tatilRepository.count());

        AuditLog audit = auditLogRepository.findAll().getFirst();
        assertEquals("TATIL_EKLENDI", audit.getEylemTipi().name());
        assertEquals(
                aktor.getPersonelId(),
                audit.getPersonel().getPersonelId()
        );
        assertNull(audit.getHedefPersonel());
        assertEquals("Tatil oluşturuldu.", audit.getDetay());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-08-28|2026-08-24",
            "2026-01-01|2027-01-02"
    })
    void tersVeyaCokUzunTatilGrubu400Doner(String aralik)
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        String[] tarihler = aralik.split("\\|");

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Geçersiz Grup",
                                tarihler[0],
                                tarihler[1]
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isBadRequest());

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-08-24|2026-08-28",
            "2026-08-23|2026-08-25",
            "2026-08-27|2026-08-30",
            "2026-08-25|2026-08-27",
            "2026-08-23|2026-08-30",
            "2026-08-26|2026-08-26"
    })
    void cakisanTatilGrubu409VeAtomikOlur(String aralik)
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil mevcut = tatilKaydet(
                "Mevcut Grup",
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 28),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        String[] tarihler = aralik.split("\\|");

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Çakışan Grup",
                                tarihler[0],
                                tarihler[1]
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.message").value(
                                "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
                        )
                );

        assertEquals(1L, tatilRepository.count());
        Tatil korunan = tatilRepository.findById(mevcut.getTatilId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 8, 24), korunan.getTarih());
        assertEquals(LocalDate.of(2026, 8, 28), korunan.getBitisTarihi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bitisikTatilGrubuCakismaSayilmaz()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        tatilKaydet(
                "Mevcut Grup",
                LocalDate.of(2026, 8, 24),
                LocalDate.of(2026, 8, 28),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Bitişik Grup",
                                "2026-08-29",
                                "2026-08-30"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isCreated());

        assertEquals(2L, tatilRepository.count());
    }

    @Test
    void aktifRezervasyonlaCakisanTamGunTatilGrubu409VeAtomikOlur()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Oda oda = odaKaydet("Büyük Salon", 12);
        Rezervasyon rezervasyon = rezervasyonKaydet(
                aktor,
                oda,
                LocalDate.of(2026, 8, 26),
                10,
                11,
                RezervasyonDurum.AKTIF
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Rezervasyonla Çakışan Tatil",
                                "2026-08-24",
                                "2026-08-28"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Seçilen tarih aralığında tatil saatleriyle "
                                + "çakışan aktif rezervasyonlar bulunuyor. "
                                + "Tatili kaydetmek için önce bu "
                                + "rezervasyonları iptal edin."
                ));

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
        assertEquals(
                RezervasyonDurum.AKTIF,
                rezervasyonRepository.findById(
                        rezervasyon.getRezervasyonId()
                ).orElseThrow().getDurum()
        );
    }

    @Test
    void iptalEdilmisRezervasyonTatilOlusturmayiEngellemez()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Oda oda = odaKaydet("Büyük Salon", 12);
        rezervasyonKaydet(
                aktor,
                oda,
                LocalDate.of(2026, 8, 26),
                10,
                11,
                RezervasyonDurum.IPTAL_EDILDI
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "İptal Sonrası Tatil",
                                "2026-08-24",
                                "2026-08-28"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isCreated());

        assertEquals(1L, tatilRepository.count());
        assertEquals(1L, auditLogRepository.count());
    }

    @Test
    void yarimGunSiniriIcindekiAktifRezervasyonTatilOlusturmayiEngellemez()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Oda oda = odaKaydet("Büyük Salon", 12);
        rezervasyonKaydet(
                aktor,
                oda,
                LocalDate.of(2026, 8, 26),
                8,
                13,
                RezervasyonDurum.AKTIF
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yarimGunTatilGrubuJson(
                                "Yarım Gün Tatili",
                                "2026-08-24",
                                "2026-08-28"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isCreated());

        assertEquals(1L, tatilRepository.count());
    }

    @Test
    void yarimGunSiniriniAsanAktifRezervasyonTatilOlusturmayiEngeller()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Oda oda = odaKaydet("Büyük Salon", 12);
        rezervasyonKaydet(
                aktor,
                oda,
                LocalDate.of(2026, 8, 26),
                12,
                14,
                RezervasyonDurum.AKTIF
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(yarimGunTatilGrubuJson(
                                "Yarım Gün Tatili",
                                "2026-08-24",
                                "2026-08-28"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isConflict());

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void aktifRezervasyonlaCakisanTatilGuncellemesi409VeAtomikOlur()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil mevcut = tatilKaydet(
                "Korunacak Tatil",
                LocalDate.of(2026, 9, 1),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );
        Oda oda = odaKaydet("Büyük Salon", 12);
        rezervasyonKaydet(
                aktor,
                oda,
                LocalDate.of(2026, 9, 10),
                10,
                11,
                RezervasyonDurum.AKTIF
        );

        mockMvc.perform(patch(
                        "/api/holidays/{tatilId}",
                        mevcut.getTatilId()
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Çakışan Yeni Aralık",
                                "2026-09-09",
                                "2026-09-11"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isConflict());

        Tatil korunan = tatilRepository.findById(
                mevcut.getTatilId()
        ).orElseThrow();
        assertEquals("Korunacak Tatil", korunan.getAd());
        assertEquals(LocalDate.of(2026, 9, 1), korunan.getTarih());
        assertEquals(LocalDate.of(2026, 9, 1), korunan.getBitisTarihi());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void cokGunlukGrupGuncellenirVeSilinir()
            throws Exception {

        Rol hrRolu = rolKaydet(4, RolAdi.HR);
        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Eski Grup",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 3),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(patch("/api/holidays/{tatilId}", tatil.getTatilId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tatilGrubuJson(
                                "Yeni Grup",
                                "2026-09-07",
                                "2026-09-11"
                        ))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isNoContent());

        Tatil guncellenen = tatilRepository.findById(tatil.getTatilId()).orElseThrow();
        assertEquals(LocalDate.of(2026, 9, 7), guncellenen.getTarih());
        assertEquals(LocalDate.of(2026, 9, 11), guncellenen.getBitisTarihi());
        assertEquals(1L, auditLogRepository.count());
        assertEquals(
                "TATIL_GUNCELLENDI",
                auditLogRepository.findAll().getFirst().getEylemTipi().name()
        );

        mockMvc.perform(delete("/api/holidays/{tatilId}", tatil.getTatilId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.HR)
                        ))
                .andExpect(status().isNoContent());

        assertEquals(0L, tatilRepository.count());
        assertEquals(2L, auditLogRepository.count());
        assertTrue(
                auditLogRepository.findAll().stream()
                        .anyMatch(audit ->
                                "TATIL_SILINDI".equals(
                                        audit.getEylemTipi().name()
                                )
                                        && "Tatil silindi.".equals(
                                                audit.getDetay()
                                        )
                        )
        );
    }

    @Test
    void hrGelecekTatiliniOlustururVeTrimEdilmisDtoDoner()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "ad": "  Cumhuriyet Bayramı  ",
                              "tarih": "2026-10-29",
                              "kategori": "RESMI_SABIT",
                              "gunSuresi": "TAM_GUN"
                            }
                            """)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.tatilId")
                                .isNumber()
                )
                .andExpect(
                        jsonPath("$.ad")
                                .value("Cumhuriyet Bayramı")
                )
                .andExpect(
                        jsonPath("$.tarih")
                                .value("2026-10-29")
                )
                .andExpect(
                        jsonPath("$.kategori")
                                .value("RESMI_SABIT")
                )
                .andExpect(
                        jsonPath("$.gunSuresi")
                                .value("TAM_GUN")
                )
                .andExpect(
                        jsonPath("$.version")
                                .doesNotExist()
                );

        assertEquals(1L, tatilRepository.count());

        Tatil kaydedilen = tatilRepository
                .findAll()
                .getFirst();

        assertEquals(
                "Cumhuriyet Bayramı",
                kaydedilen.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 10, 29),
                kaydedilen.getTarih()
        );
        assertEquals(
                TatilKategori.RESMI_SABIT,
                kaydedilen.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                kaydedilen.getGunSuresi()
        );
    }

    @Test
    void tarihParametresiEksikse400Doner()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-01-01"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tarihParametresiBozuksa400Doner()
            throws Exception {

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-13-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-12-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminGelecekTatiliniOlusturabilir()
            throws Exception {

        Rol adminRolu = rolKaydet(
                1,
                RolAdi.ADMIN
        );

        aktor.setRol(adminRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.ad")
                                .value("Cumhuriyet Bayramı")
                );
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {
                    "PERSONEL",
                    "YONETICI"
            }
    )
    void personelVeYoneticiTatilOlusturamaz(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case PERSONEL ->
                    personelRolu;

            case YONETICI ->
                    rolKaydet(3, RolAdi.YONETICI);

            default ->
                    throw new IllegalArgumentException(
                            "Beklenmeyen rol: " + rolAdi
                    );
        };

        aktor.setRol(rol);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        rolAdi
                                )
                        ))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void jwtHrAmaDbRoluPersonelOlanAktorTatilOlusturamaz()
            throws Exception {

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void pasifHrTatilOlusturamaz()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        String token = token(
                aktor,
                RolAdi.HR
        );

        aktor.setCalisiyorMu(false);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiHrBulunamazsaTatilOlusturma404Doner()
            throws Exception {

        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.HR
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokenOlmadanTatilOlusturulamaz()
            throws Exception {

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gecerliTatilJson()))
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void gecmisTarihIcinTatilOlusturulamaz()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "ad": "Geçmiş Tatil",
                              "tarih": "2026-08-21",
                              "kategori": "SIRKET",
                              "gunSuresi": "TAM_GUN"
                            }
                            """)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Geçmiş tarih için tatil oluşturulamaz."
                                )
                );

        assertEquals(0L, tatilRepository.count());
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void ayniTarihteIkinciTatil409DonerVeIlkKaydiKorur()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        tatilKaydet(
                "Mevcut Tatil",
                LocalDate.of(2026, 10, 29),
                TatilKategori.RESMI_SABIT,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(post("/api/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "ad": "Çakışan Tatil",
                              "tarih": "2026-10-29",
                              "kategori": "SIRKET",
                              "gunSuresi": "YARIM_GUN"
                            }
                            """)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.status")
                                .value(409)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
                                )
                );

        assertEquals(1L, tatilRepository.count());

        Tatil korunan = tatilRepository
                .findAll()
                .getFirst();

        assertEquals(
                "Mevcut Tatil",
                korunan.getAd()
        );
        assertEquals(
                TatilKategori.RESMI_SABIT,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataTatilGuncellemeVeAuditiBirlikteRollbackYapar() {
        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        aktor.setRol(hrRolu);
        aktor = personelRepository.saveAndFlush(aktor);

        Tatil tatil = tatilKaydet(
                "Rollback Öncesi Tatil",
                LocalDate.of(2026, 11, 10),
                LocalDate.of(2026, 11, 14),
                TatilKategori.SIRKET,
                TatilGunSuresi.TAM_GUN
        );

        Long oncekiVersion = tatil.getVersion();

        TatilKaydetRequest request = new TatilKaydetRequest(
                "Rollback Sonrası Tatil",
                LocalDate.of(2026, 12, 10),
                LocalDate.of(2026, 12, 15),
                TatilKategori.RESMI_DEGISKEN,
                TatilGunSuresi.YARIM_GUN
        );

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.guncelleVeHataFirlat(
                        aktor.getPersonelId(),
                        tatil.getTatilId(),
                        request
                )
        );

        Tatil korunan = tatilRepository
                .findById(tatil.getTatilId())
                .orElseThrow();

        assertEquals(
                "Rollback Öncesi Tatil",
                korunan.getAd()
        );
        assertEquals(
                LocalDate.of(2026, 11, 10),
                korunan.getTarih()
        );
        assertEquals(
                LocalDate.of(2026, 11, 14),
                korunan.getBitisTarihi()
        );
        assertEquals(
                TatilKategori.SIRKET,
                korunan.getKategori()
        );
        assertEquals(
                TatilGunSuresi.TAM_GUN,
                korunan.getGunSuresi()
        );
        assertEquals(
                oncekiVersion,
                korunan.getVersion()
        );
        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void aktifPersonelTarihAraligindakiTatilleriSiraliVeGuvenliGorur()
            throws Exception {

        Tatil yarimGun = tatilKaydet(
                "Şirket Etkinliği",
                LocalDate.of(2026, 8, 31),
                TatilKategori.SIRKET,
                TatilGunSuresi.YARIM_GUN
        );

        Tatil zaferBayrami = tatilKaydet(
                "Zafer Bayramı",
                LocalDate.of(2026, 8, 30),
                TatilKategori.RESMI_SABIT,
                TatilGunSuresi.TAM_GUN
        );

        tatilKaydet(
                "Aralık Dışındaki Tatil",
                LocalDate.of(2026, 9, 1),
                TatilKategori.RESMI_DEGISKEN,
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(get("/api/holidays")
                        .param(
                                "baslangicTarihi",
                                "2026-08-01"
                        )
                        .param(
                                "bitisTarihi",
                                "2026-08-31"
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[0].tatilId")
                                .value(zaferBayrami.getTatilId())
                )
                .andExpect(
                        jsonPath("$[0].ad")
                                .value("Zafer Bayramı")
                )
                .andExpect(
                        jsonPath("$[0].tarih")
                                .value("2026-08-30")
                )
                .andExpect(
                        jsonPath("$[0].kategori")
                                .value("RESMI_SABIT")
                )
                .andExpect(
                        jsonPath("$[0].gunSuresi")
                                .value("TAM_GUN")
                )
                .andExpect(
                        jsonPath("$[1].tatilId")
                                .value(yarimGun.getTatilId())
                )
                .andExpect(
                        jsonPath("$[1].ad")
                                .value("Şirket Etkinliği")
                )
                .andExpect(
                        jsonPath("$[1].tarih")
                                .value("2026-08-31")
                )
                .andExpect(
                        jsonPath("$[1].kategori")
                                .value("SIRKET")
                )
                .andExpect(
                        jsonPath("$[1].gunSuresi")
                                .value("YARIM_GUN")
                )
                .andExpect(
                        jsonPath("$[0].version")
                                .doesNotExist()
                );

        assertEquals(0L, auditLogRepository.count());
    }

    private Tatil tatilKaydet(
            String ad,
            LocalDate tarih,
            TatilKategori kategori,
            TatilGunSuresi gunSuresi
    ) {
        return tatilKaydet(
                ad,
                tarih,
                tarih,
                kategori,
                gunSuresi
        );
    }

    private Tatil tatilKaydet(
            String ad,
            LocalDate tarih,
            LocalDate bitisTarihi,
            TatilKategori kategori,
            TatilGunSuresi gunSuresi
    ) {
        Tatil tatil = new Tatil();
        tatil.setAd(ad);
        tatil.setTarih(tarih);
        tatil.setBitisTarihi(bitisTarihi);
        tatil.setKategori(kategori);
        tatil.setGunSuresi(gunSuresi);

        return tatilRepository.saveAndFlush(tatil);
    }

    private Oda odaKaydet(String ad, int kapasite) {
        Oda oda = new Oda();
        oda.setOdaAd(ad);
        oda.setOdaKapasite(kapasite);
        return odaRepository.saveAndFlush(oda);
    }

    private Rezervasyon rezervasyonKaydet(
            Personel olusturan,
            Oda oda,
            LocalDate tarih,
            int baslangicSaat,
            int bitisSaat,
            RezervasyonDurum durum
    ) {
        Rezervasyon rezervasyon = new Rezervasyon();
        rezervasyon.setOlusturan(olusturan);
        rezervasyon.setOda(oda);
        rezervasyon.setBaslik("Tatil çakışma testi");
        rezervasyon.setTarih(tarih);
        rezervasyon.setBaslangicSaat(baslangicSaat);
        rezervasyon.setBitisSaat(bitisSaat);
        rezervasyon.setKatilimciSayisi(2);
        rezervasyon.setDurum(durum);
        rezervasyon.setOlusturmaZamani(
                LocalDateTime.of(2026, 8, 22, 10, 0)
        );
        return rezervasyonRepository.saveAndFlush(rezervasyon);
    }

    private Rol rolKaydet(
            int rolId,
            RolAdi rolAdi
    ) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);

        return rolRepository.saveAndFlush(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);

        return departmanRepository.saveAndFlush(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman departman
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(
                LocalDate.of(1995, 1, 1)
        );
        personel.setePosta(
                kullaniciAdi + "@test.local"
        );
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");

        return personelRepository.saveAndFlush(personel);
    }

    private String token(
            Personel personel,
            RolAdi rolAdi
    ) {
        return jwtService.generateToken(
                personel.getPersonelId(),
                rolAdi
        );
    }

    private String gecerliTatilJson() {
        return """
            {
              "ad": "Cumhuriyet Bayramı",
              "tarih": "2026-10-29",
              "kategori": "RESMI_SABIT",
              "gunSuresi": "TAM_GUN"
            }
            """;
    }

    private String tatilGrubuJson(
            String ad,
            String baslangicTarihi,
            String bitisTarihi
    ) {
        return """
                {
                  "ad": "%s",
                  "tarih": "%s",
                  "bitisTarihi": "%s",
                  "kategori": "SIRKET",
                  "gunSuresi": "TAM_GUN"
                }
                """.formatted(ad, baslangicTarihi, bitisTarihi);
    }

    private String yarimGunTatilGrubuJson(
            String ad,
            String baslangicTarihi,
            String bitisTarihi
    ) {
        return """
                {
                  "ad": "%s",
                  "tarih": "%s",
                  "bitisTarihi": "%s",
                  "kategori": "SIRKET",
                  "gunSuresi": "YARIM_GUN"
                }
                """.formatted(ad, baslangicTarihi, bitisTarihi);
    }

    @TestConfiguration
    static class SabitSaatTestConfig {

        @Bean
        @Primary
        Clock testClock() {
            return Clock.fixed(
                    Instant.parse("2026-08-22T09:00:00Z"),
                    ZoneId.of("Europe/Istanbul")
            );
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        TatilRollbackTestService tatilRollbackTestService(
                TatilTakvimiService service
        ) {
            return new TatilRollbackTestService(service);
        }
    }

    static class TatilRollbackTestService {

        @Transactional
        public void guncelleVeHataFirlat(
                String aktorId,
                Integer tatilId,
                TatilKaydetRequest request
        ) {
            service.tatilGuncelle(
                    aktorId,
                    tatilId,
                    request
            );

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        private final TatilTakvimiService service;

        TatilRollbackTestService(
                TatilTakvimiService service
        ) {
            this.service = service;
        }

        @Transactional
        public void silVeHataFirlat(
                String aktorId,
                Integer tatilId
        ) {
            service.tatilSil(
                    aktorId,
                    tatilId
            );

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        @Transactional
        public void olusturVeHataFirlat(
                String aktorId,
                TatilKaydetRequest request
        ) {
            service.tatilOlustur(
                    aktorId,
                    request
            );

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }
    }

    static Stream<String> gecersizTatilRequestleri() {
        return Stream.of(
                """
                {
                  "ad": "   ",
                  "tarih": "2026-10-29",
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": "TAM_GUN"
                }
                """,

                """
                {
                  "ad": "%s",
                  "tarih": "2026-10-29",
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": "TAM_GUN"
                }
                """.formatted("A".repeat(101)),

                """
                {
                  "ad": null,
                  "tarih": "2026-10-29",
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": "TAM_GUN"
                }
                """,

                """
                {
                  "ad": "Test Tatili",
                  "tarih": null,
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": "TAM_GUN"
                }
                """,

                """
                {
                  "ad": "Test Tatili",
                  "tarih": "2026-10-29",
                  "kategori": null,
                  "gunSuresi": "TAM_GUN"
                }
                """,

                """
                {
                  "ad": "Test Tatili",
                  "tarih": "2026-10-29",
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": null
                }
                """,

                """
                {
                  "ad": "Test Tatili",
                  "tarih": "2026-10-29",
                  "kategori": "GECERSIZ_KATEGORI",
                  "gunSuresi": "TAM_GUN"
                }
                """,

                """
                {
                  "ad": "Test Tatili",
                  "tarih": "2026-99-99",
                  "kategori": "RESMI_SABIT",
                  "gunSuresi": "TAM_GUN"
                }
                """
        );
    }

}
