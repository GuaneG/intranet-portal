package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import com.jforce.backend.service.PersonelService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PersonelAktiflestirmeControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PersonelAktiflestirmeRollbackTestService
            rollbackTestService;

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

    private final Map<RolAdi, Rol> roller =
            new EnumMap<>(RolAdi.class);

    private Departman departman;
    private Personel hrAktor;
    private Personel pasifPersonel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        roller.clear();

        roller.put(
                RolAdi.ADMIN,
                rolKaydet(1, RolAdi.ADMIN)
        );
        roller.put(
                RolAdi.PERSONEL,
                rolKaydet(2, RolAdi.PERSONEL)
        );
        roller.put(
                RolAdi.YONETICI,
                rolKaydet(3, RolAdi.YONETICI)
        );
        roller.put(
                RolAdi.HR,
                rolKaydet(4, RolAdi.HR)
        );

        departman =
                departmanKaydet("Bilgi Teknolojileri");

        hrAktor = personelKaydet(
                "aktiflestirme.hr.aktor",
                "Aktifleştirme",
                "HR",
                RolAdi.HR,
                true
        );

        pasifPersonel = personelKaydet(
                "aktiflestirilecek.personel",
                "Aktifleşecek",
                "Personel",
                RolAdi.PERSONEL,
                false
        );

        pasifPersonel.setIsCikisTarihi(
                LocalDate.now().minusDays(1)
        );

        pasifPersonel =
                personelRepository.saveAndFlush(
                        pasifPersonel
                );
    }

    @Test
    void yoneticiAktiflestirilinceEskiCalisanlariGeriBaglanmaz()
            throws Exception {
        Personel admin = personelKaydet(
                "aktiflestirme.admin",
                "Aktifleştirme",
                "Admin",
                RolAdi.ADMIN,
                true
        );

        Personel yonetici = personelKaydet(
                "aktiflestirme.yonetici",
                "Aktifleştirme",
                "Yönetici",
                RolAdi.YONETICI,
                true
        );

        Personel calisan = personelKaydet(
                "aktiflestirme.calisan",
                "Aktifleştirme",
                "Çalışan",
                RolAdi.PERSONEL,
                true
        );

        calisan.setYonetici(yonetici);
        calisan =
                personelRepository.saveAndFlush(calisan);

        String adminToken =
                token(admin, RolAdi.ADMIN);

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/pasiflestir",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + adminToken
                                )
                )
                .andExpect(status().isNoContent());

        Personel pasiflestirmeSonrasiCalisan =
                personelRepository
                        .findById(calisan.getPersonelId())
                        .orElseThrow();

        assertNull(
                pasiflestirmeSonrasiCalisan.getYonetici()
        );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + adminToken
                                )
                )
                .andExpect(status().isNoContent());

        Personel sonCalisan =
                personelRepository
                        .findById(calisan.getPersonelId())
                        .orElseThrow();

        Personel aktiflesenYonetici =
                personelRepository
                        .findById(yonetici.getPersonelId())
                        .orElseThrow();

        assertNull(sonCalisan.getYonetici());
        assertTrue(aktiflesenYonetici.getCalisiyorMu());
        assertNull(
                aktiflesenYonetici.getIsCikisTarihi()
        );
    }

    @Test
    void aktiflestirilenPersonelEkipDizinineGeriDoner()
            throws Exception {
        String hrToken = token(hrAktor, RolAdi.HR);

        mockMvc.perform(
                        get("/api/ekip-dizini")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + hrToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.icerik[*].ad")
                                .value(
                                        org.hamcrest.Matchers.not(
                                                hasItem("Aktifleşecek")
                                        )
                                )
                );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + hrToken
                                )
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/ekip-dizini")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + hrToken
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.icerik[*].ad")
                                .value(
                                        hasItem("Aktifleşecek")
                                )
                );
    }

    @Test
    void hrPasifPersoneliAktiflestirir()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Personel aktiflesen =
                personelRepository
                        .findById(
                                pasifPersonel.getPersonelId()
                        )
                        .orElseThrow();

        assertTrue(aktiflesen.getCalisiyorMu());
        assertNull(aktiflesen.getIsCikisTarihi());
    }

    @Test
    @WithMockUser(roles = "HR")
    void disHataAktiflestirmeVeAuditiRollbackYapar() {
        LocalDate eskiCikisTarihi =
                pasifPersonel.getIsCikisTarihi();

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService
                        .aktiflestirVeHataFirlat(
                                hrAktor.getPersonelId(),
                                pasifPersonel.getPersonelId()
                        )
        );

        Boolean veritabanindaAktif =
                jdbcTemplate.queryForObject(
                        """
                        SELECT calisiyor_mu
                        FROM personel
                        WHERE personel_id = ?
                        """,
                        Boolean.class,
                        pasifPersonel.getPersonelId()
                );

        java.sql.Date veritabanindakiCikisTarihi =
                jdbcTemplate.queryForObject(
                        """
                        SELECT is_cikis_tarihi
                        FROM personel
                        WHERE personel_id = ?
                        """,
                        java.sql.Date.class,
                        pasifPersonel.getPersonelId()
                );

        assertFalse(veritabanindaAktif);

        assertEquals(
                eskiCikisTarihi,
                veritabanindakiCikisTarihi.toLocalDate()
        );

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @Test
    void gercekAktiflestirmeGuvenliAuditOlusturur()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent());

        assertEquals(
                1L,
                auditLogRepository.count()
        );

        Map<String, Object> audit =
                jdbcTemplate.queryForMap(
                        """
                        SELECT
                            eylem_tipi,
                            personel_id,
                            hedef_personel_id,
                            kullanici_adi,
                            detay
                        FROM audit_log
                        """
                );

        assertEquals(
                "PERSONEL_AKTIFLESTIRILDI",
                audit.get("eylem_tipi")
        );
        assertEquals(
                hrAktor.getPersonelId(),
                audit.get("personel_id")
        );
        assertEquals(
                pasifPersonel.getPersonelId(),
                audit.get("hedef_personel_id")
        );
        assertEquals(
                "aktiflestirme.hr.aktor",
                audit.get("kullanici_adi")
        );
        assertEquals(
                "Personel aktifleştirildi.",
                audit.get("detay")
        );
    }

    @Test
    void zatenAktifHedefNoOpOlur()
            throws Exception {
        Personel zatenAktif = personelKaydet(
                "zaten.aktif.personel",
                "Zaten",
                "Aktif",
                RolAdi.PERSONEL,
                true
        );

        LocalDate tutarsizEskiCikisTarihi =
                LocalDate.now().minusDays(10);

        zatenAktif.setIsCikisTarihi(
                tutarsizEskiCikisTarihi
        );
        zatenAktif =
                personelRepository.saveAndFlush(
                        zatenAktif
                );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                zatenAktif.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        Personel degismeyen =
                personelRepository
                        .findById(zatenAktif.getPersonelId())
                        .orElseThrow();

        assertTrue(degismeyen.getCalisiyorMu());

        assertEquals(
                tutarsizEskiCikisTarihi,
                degismeyen.getIsCikisTarihi()
        );

        assertEquals(
                0L,
                auditLogRepository.count()
        );
    }

    @ParameterizedTest
    @CsvSource({
            "HR,PERSONEL,204",
            "HR,HR,204",
            "HR,YONETICI,403",
            "HR,ADMIN,403",
            "ADMIN,PERSONEL,204",
            "ADMIN,HR,204",
            "ADMIN,YONETICI,204",
            "ADMIN,ADMIN,403"
    })
    void hedefRolMatrisiUygulanir(
            RolAdi aktorRolu,
            RolAdi hedefRolu,
            int beklenenStatus
    ) throws Exception {
        Personel aktor = personelKaydet(
                "matris.aktor."
                        + aktorRolu.name().toLowerCase(),
                "Matris",
                "Aktör",
                aktorRolu,
                true
        );

        Personel hedef = personelKaydet(
                "matris.hedef."
                        + hedefRolu.name().toLowerCase(),
                "Matris",
                "Hedef",
                hedefRolu,
                false
        );

        hedef.setIsCikisTarihi(
                LocalDate.now().minusDays(1)
        );
        hedef = personelRepository.saveAndFlush(hedef);

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                aktorRolu
                                        )
                                )
                )
                .andExpect(
                        status().is(beklenenStatus)
                );

        Personel sonHedef =
                personelRepository
                        .findById(hedef.getPersonelId())
                        .orElseThrow();

        assertEquals(
                beklenenStatus == 204,
                sonHedef.getCalisiyorMu()
        );
    }

    @Test
    void hrKendisiniAktiflestiremez()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                hrAktor.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isForbidden());

        assertTrue(
                personelRepository
                        .findById(hrAktor.getPersonelId())
                        .orElseThrow()
                        .getCalisiyorMu()
        );
    }

    @Test
    void pasifHrPersonelAktiflestiremez()
            throws Exception {
        hrAktor.setCalisiyorMu(false);
        hrAktor =
                personelRepository.saveAndFlush(hrAktor);

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isForbidden());

        assertFalse(
                personelRepository
                        .findById(
                                pasifPersonel.getPersonelId()
                        )
                        .orElseThrow()
                        .getCalisiyorMu()
        );
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner()
            throws Exception {
        String gecersizToken =
                jwtService.generateToken(
                        "veritabaninda-olmayan-personel",
                        RolAdi.HR
                );

        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + gecersizToken
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message").value(
                                "Personel bulunamadı."
                        )
                );

        assertFalse(
                personelRepository
                        .findById(
                                pasifPersonel.getPersonelId()
                        )
                        .orElseThrow()
                        .getCalisiyorMu()
        );
    }

    @Test
    void hedefPersonelBulunamazsa404Doner()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                "veritabaninda-olmayan-hedef"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.message").value(
                                "Personel bulunamadı."
                        )
                );
    }

    @Test
    void tokenOlmadanPersonelAktiflestirilemez()
            throws Exception {
        mockMvc.perform(
                        patch(
                                "/api/hr/personel/{personelId}/aktiflestir",
                                pasifPersonel.getPersonelId()
                        )
                )
                .andExpect(status().isUnauthorized());

        assertFalse(
                personelRepository
                        .findById(
                                pasifPersonel.getPersonelId()
                        )
                        .orElseThrow()
                        .getCalisiyorMu()
        );
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        PersonelAktiflestirmeRollbackTestService
        personelAktiflestirmeRollbackTestService(
                PersonelService personelService
        ) {
            return new PersonelAktiflestirmeRollbackTestService(
                    personelService
            );
        }
    }

    static class PersonelAktiflestirmeRollbackTestService {

        private final PersonelService personelService;

        PersonelAktiflestirmeRollbackTestService(
                PersonelService personelService
        ) {
            this.personelService = personelService;
        }

        @Transactional
        public void aktiflestirVeHataFirlat(
                String aktorId,
                String hedefPersonelId
        ) {
            personelService.personelAktiflestir(
                    aktorId,
                    hedefPersonelId
            );

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak "
                            + "için test hatası."
            );
        }
    }

    private Rol rolKaydet(
            int id,
            RolAdi rolAdi
    ) {
        Rol rol = new Rol();
        rol.setRolId(id);
        rol.setRolAdi(rolAdi);

        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman yeniDepartman = new Departman();
        yeniDepartman.setDepartmanAdi(ad);

        return departmanRepository.saveAndFlush(
                yeniDepartman
        );
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            RolAdi rolAdi,
            boolean aktif
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
        personel.setRol(roller.get(rolAdi));
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);

        return personelRepository.saveAndFlush(
                personel
        );
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
}