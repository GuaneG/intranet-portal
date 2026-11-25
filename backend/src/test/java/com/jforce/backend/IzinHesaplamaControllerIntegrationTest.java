package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IzinHesaplamaControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private TatilRepository tatilRepository;

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

    private Personel personel;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        tatilRepository.deleteAll();

        Rol personelRolu = rolKaydet(
                2,
                RolAdi.PERSONEL
        );

        Departman departman =
                departmanKaydet("Bilgi Teknolojileri");

        personel = personelKaydet(
                "izin.onizleme.personel",
                personelRolu,
                departman
        );
    }

    @Test
    void aktifVeUygunYoneticiIsGunuOnizler()
            throws Exception {

        Rol yoneticiRolu = rolKaydet(
                3,
                RolAdi.YONETICI
        );

        Personel ustYonetici = personelKaydet(
                "izin.onizleme.ust.yonetici",
                yoneticiRolu,
                personel.getDepartman()
        );

        personel.setRol(yoneticiRolu);
        personel.setYonetici(ustYonetici);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.YONETICI
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.isGunuSayisi")
                                .value(5.0)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void kokYoneticiIsGunuOnizleyemez()
            throws Exception {

        Rol yoneticiRolu = rolKaydet(
                3,
                RolAdi.YONETICI
        );

        personel.setRol(yoneticiRolu);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.YONETICI
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "false, YONETICI",
            "true, PERSONEL"
    })
    void pasifVeyaYanlisRolluUstYoneticiyeBagliYoneticiIsGunuOnizleyemez(
            boolean ustYoneticiAktif,
            RolAdi ustYoneticiRolu
    ) throws Exception {

        Rol yoneticiRolu = rolKaydet(
                3,
                RolAdi.YONETICI
        );

        Rol ustYoneticiRolKaydi = switch (ustYoneticiRolu) {
            case YONETICI -> yoneticiRolu;
            case PERSONEL -> personel.getRol();
            default -> throw new IllegalArgumentException();
        };

        Personel ustYonetici = personelKaydet(
                "izin.onizleme.gecersiz.ust.yonetici." + ustYoneticiRolu,
                ustYoneticiRolKaydi,
                personel.getDepartman()
        );
        ustYonetici.setCalisiyorMu(ustYoneticiAktif);
        ustYonetici = personelRepository.saveAndFlush(ustYonetici);

        personel.setRol(yoneticiRolu);
        personel.setYonetici(ustYonetici);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.YONETICI
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void adminIsGunuOnizleyemez()
            throws Exception {

        Rol adminRolu = rolKaydet(
                1,
                RolAdi.ADMIN
        );

        personel.setRol(adminRolu);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"YONETICI", "ADMIN"}
    )
    void jwtPersonelAmaDbRoluYasakAktorOnizlemeYapamaz(
            RolAdi dbRolu
    ) throws Exception {

        Rol rol = switch (dbRolu) {
            case YONETICI -> rolKaydet(
                    3,
                    RolAdi.YONETICI
            );
            case ADMIN -> rolKaydet(
                    1,
                    RolAdi.ADMIN
            );
            default -> throw new IllegalArgumentException();
        };

        personel.setRol(rol);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @CsvSource({
            "2026-08-30, 2026-08-24",
            "2026-01-01, 2027-01-02"
    })
    void gecersizOnizlemeTarihAraligi400Doner(
            String baslangicTarihi,
            String bitisTarihi
    ) throws Exception {

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        baslangicTarihi
                                )
                                .param(
                                        "bitisTarihi",
                                        bitisTarihi
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
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
    void baslangicTarihiEksikseOnizleme400Doner()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
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
    void bitisTarihiEksikseOnizleme400Doner()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
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
    @CsvSource({
            "bozuk-tarih, 2026-08-30",
            "2026-08-24, bozuk-tarih"
    })
    void bozukTarihParametresiOnizlemede400Doner(
            String baslangicTarihi,
            String bitisTarihi
    ) throws Exception {

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        baslangicTarihi
                                )
                                .param(
                                        "bitisTarihi",
                                        bitisTarihi
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
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
    @EnumSource(
            value = RolAdi.class,
            names = {"PERSONEL", "HR"}
    )
    void pasifPersonelVeHrOnizlemeYapamaz(
            RolAdi rolAdi
    ) throws Exception {

        Rol rol = switch (rolAdi) {
            case PERSONEL -> personel.getRol();
            case HR -> rolKaydet(
                    4,
                    RolAdi.HR
            );
            default -> throw new IllegalArgumentException();
        };

        personel.setRol(rol);
        personel = personelRepository.saveAndFlush(personel);

        String token = token(
                personel,
                rolAdi
        );

        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
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

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tokendakiPersonelBulunamazsaOnizleme404Doner()
            throws Exception {

        String token = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
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
    void tokenOlmadanIsGunuOnizlenemez()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrOndalikliIsGunuOnizleyebilir()
            throws Exception {

        Rol hrRolu = rolKaydet(
                4,
                RolAdi.HR
        );

        personel.setRol(hrRolu);
        personel = personelRepository.saveAndFlush(personel);

        tatilKaydet(
                "Yarım Gün Şirket Tatili",
                LocalDate.of(2026, 8, 26),
                TatilGunSuresi.YARIM_GUN
        );

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.isGunuSayisi")
                                .value(4.5)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void tamGunTatilIcinSifirIsGunuDoner()
            throws Exception {

        tatilKaydet(
                "Tam Gün Tatil",
                LocalDate.of(2026, 8, 24),
                TatilGunSuresi.TAM_GUN
        );

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-24"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.isGunuSayisi")
                                .value(0.0)
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void personelTatilVeHaftaSonunuDuserekOndalikliIsGunuOnizler()
            throws Exception {

        tatilKaydet(
                "Yarım Gün Şirket Tatili",
                LocalDate.of(2026, 8, 26),
                TatilGunSuresi.YARIM_GUN
        );

        mockMvc.perform(
                        get(
                                "/api/leave-requests/workday-preview"
                        )
                                .param(
                                        "baslangicTarihi",
                                        "2026-08-24"
                                )
                                .param(
                                        "bitisTarihi",
                                        "2026-08-30"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                personel,
                                                RolAdi.PERSONEL
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.isGunuSayisi")
                                .value(4.5)
                )
                .andExpect(
                        jsonPath("$.length()")
                                .value(1)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    private Tatil tatilKaydet(
            String ad,
            LocalDate tarih,
            TatilGunSuresi gunSuresi
    ) {
        Tatil tatil = new Tatil();
        tatil.setAd(ad);
        tatil.setTarih(tarih);
        tatil.setBitisTarihi(tarih);
        tatil.setKategori(TatilKategori.SIRKET);
        tatil.setGunSuresi(gunSuresi);

        return tatilRepository.saveAndFlush(tatil);
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

    private Departman departmanKaydet(
            String departmanAdi
    ) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(departmanAdi);

        return departmanRepository.saveAndFlush(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            Rol rol,
            Departman departman
    ) {
        Personel kaydedilecek = new Personel();
        kaydedilecek.setKullaniciAdi(kullaniciAdi);
        kaydedilecek.setParolaHash("test-hash");
        kaydedilecek.setAdi("İzin");
        kaydedilecek.setSoyadi("Personeli");
        kaydedilecek.setDogumTarihi(
                LocalDate.of(1995, 1, 1)
        );
        kaydedilecek.setePosta(
                kullaniciAdi + "@test.local"
        );
        kaydedilecek.setDepartman(departman);
        kaydedilecek.setRol(rol);
        kaydedilecek.setCalisiyorMu(true);
        kaydedilecek.setIsGirisTarihi(
                LocalDate.of(2022, 1, 1)
        );
        kaydedilecek.setYillikIzinHakki(20);
        kaydedilecek.setProfilFoto("def_pfp.png");

        return personelRepository.saveAndFlush(
                kaydedilecek
        );
    }

    private String token(
            Personel aktor,
            RolAdi rolAdi
    ) {
        return jwtService.generateToken(
                aktor.getPersonelId(),
                rolAdi
        );
    }
}
