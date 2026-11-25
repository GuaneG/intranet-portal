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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DogumGunuTakvimiControllerIntegrationTest extends AbstractIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Departman bilgiTeknolojileri;
    private Rol personelRolu;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM yetenek");

        bilgiTeknolojileri = departmanKaydet("Bilgi Teknolojileri");
        personelRolu = rolKaydet(RolAdi.PERSONEL);
    }

    @Test
    void aktifPersonelSirketGeneliAktifDogumGunleriniGuvenliVeSiraliGorur() throws Exception {
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");
        Personel aktor = personelKaydet(
                "aktor.personel",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 1, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );

        personelKaydet(
                "zeynep.ik",
                "Zeynep",
                "İnsan",
                LocalDate.of(1988, 8, 15),
                insanKaynaklari,
                personelRolu,
                true
        );
        personelKaydet(
                "ayse.bt",
                "Ayşe",
                "Yılmaz",
                LocalDate.of(1995, 8, 14),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        personelKaydet(
                "pasif.personel",
                "Pasif",
                "Kullanıcı",
                LocalDate.of(1992, 8, 14),
                bilgiTeknolojileri,
                personelRolu,
                false
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].adSoyad").value("Ayşe Yılmaz"))
                .andExpect(jsonPath("$[0].tarih").value("2026-08-14"))
                .andExpect(jsonPath("$[0].departman").value("Bilgi Teknolojileri"))
                .andExpect(jsonPath("$[1].adSoyad").value("Zeynep İnsan"))
                .andExpect(jsonPath("$[1].tarih").value("2026-08-15"))
                .andExpect(jsonPath("$[1].departman").value("İnsan Kaynakları"))
                .andExpect(jsonPath("$[0].dogumTarihi").doesNotExist())
                .andExpect(jsonPath("$[0].dogumYili").doesNotExist())
                .andExpect(jsonPath("$[0].personelId").doesNotExist())
                .andExpect(jsonPath("$[0].kullaniciAdi").doesNotExist())
                .andExpect(jsonPath("$[0].ePosta").doesNotExist())
                .andExpect(jsonPath("$[0].telNo").doesNotExist());

        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void pasifAktorDogumGunuTakviminiGoremez() throws Exception {
        Personel pasifAktor = personelKaydet(
                "pasif.aktor",
                "Pasif",
                "Aktör",
                LocalDate.of(1990, 8, 14),
                bilgiTeknolojileri,
                personelRolu,
                false
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        pasifAktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isForbidden());

        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void yilSiniriniGecenAralikIkiYildakiDogumGunleriniGetirir() throws Exception {
        Personel aktor = personelKaydet(
                "aktor.yil",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        personelKaydet(
                "aralik.personel",
                "Aralık",
                "Kullanıcı",
                LocalDate.of(1985, 12, 31),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        personelKaydet(
                "ocak.personel",
                "Ocak",
                "Kullanıcı",
                LocalDate.of(1991, 1, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-12-20")
                        .param("bitisTarihi", "2027-01-10")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].adSoyad").value("Aralık Kullanıcı"))
                .andExpect(jsonPath("$[0].tarih").value("2026-12-31"))
                .andExpect(jsonPath("$[1].adSoyad").value("Ocak Kullanıcı"))
                .andExpect(jsonPath("$[1].tarih").value("2027-01-01"));
    }

    @Test
    void subatYirmiDokuzDogumGunuYalnizArtikYildaGorunur() throws Exception {
        Personel aktor = personelKaydet(
                "aktor.subat",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        personelKaydet(
                "artik.yil",
                "Artık",
                "Yıl",
                LocalDate.of(2000, 2, 29),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        String token = jwtService.generateToken(
                aktor.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-02-01")
                        .param("bitisTarihi", "2026-02-28")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2028-02-01")
                        .param("bitisTarihi", "2028-02-29")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].adSoyad").value("Artık Yıl"))
                .andExpect(jsonPath("$[0].tarih").value("2028-02-29"));
    }

    @ParameterizedTest
    @CsvSource({
            "2026-08-31, 2026-08-01",
            "2026-01-01, 2027-01-02"
    })
    void gecersizTarihAraliklari400Doner(
            String baslangicTarihi,
            String bitisTarihi
    ) throws Exception {
        Personel aktor = personelKaydet(
                "aktor." + baslangicTarihi,
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", baslangicTarihi)
                        .param("bitisTarihi", bitisTarihi)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isBadRequest());

        assertEquals(0, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifTumRollerSirketGeneliDogumGunleriniGorebilir(RolAdi rolAdi)
            throws Exception {
        Rol aktorRolu = rolAdi == RolAdi.PERSONEL
                ? personelRolu
                : rolKaydet(rolAdi);
        Personel aktor = personelKaydet(
                "aktor." + rolAdi.name().toLowerCase(),
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                aktorRolu,
                true
        );
        Departman insanKaynaklari = departmanKaydet("İnsan Kaynakları");
        personelKaydet(
                "sirket.geneli." + rolAdi.name().toLowerCase(),
                "Şirket",
                "Geneli",
                LocalDate.of(1995, 8, 14),
                insanKaynaklari,
                personelRolu,
                true
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        rolAdi
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].adSoyad").value("Şirket Geneli"))
                .andExpect(jsonPath("$[0].departman").value("İnsan Kaynakları"));
    }

    @Test
    void kimlikDogrulamaVePrincipalKontrolleriKorunur() throws Exception {
        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        UUID.randomUUID().toString(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isNotFound());

        assertEquals(0, auditLogRepository.count());
    }

    @Test
    void eksikVeyaBozukTarihParametresi400Doner() throws Exception {
        Personel aktor = personelKaydet(
                "aktor.parametre",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        String token = jwtService.generateToken(
                aktor.getPersonelId(),
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "31-08-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eslesmeYoksaBosListeDoner() throws Exception {
        Personel aktor = personelKaydet(
                "aktor.bos",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-08-01")
                        .param("bitisTarihi", "2026-08-31")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void dahilUcYuzAltmısAltiGunlukAralikKabulEdilir() throws Exception {
        Personel aktor = personelKaydet(
                "aktor.sinir",
                "Aktif",
                "Aktör",
                LocalDate.of(1990, 6, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );
        personelKaydet(
                "ocak.sinir",
                "Ocak",
                "Sınır",
                LocalDate.of(1995, 1, 1),
                bilgiTeknolojileri,
                personelRolu,
                true
        );

        mockMvc.perform(get("/api/birthdays")
                        .param("baslangicTarihi", "2026-01-01")
                        .param("bitisTarihi", "2027-01-01")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        aktor.getPersonelId(),
                                        RolAdi.PERSONEL
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].tarih").value("2026-01-01"))
                .andExpect(jsonPath("$[1].adSoyad").value("Aktif Aktör"))
                .andExpect(jsonPath("$[1].tarih").value("2026-06-01"))
                .andExpect(jsonPath("$[2].tarih").value("2027-01-01"));
    }

    private Rol rolKaydet(RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId(rolAdi));
        rol.setRolAdi(rolAdi);
        return rolRepository.saveAndFlush(rol);
    }

    private int rolId(RolAdi rolAdi) {
        return switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };
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
            LocalDate dogumTarihi,
            Departman departman,
            Rol rol,
            boolean aktif
    ) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(dogumTarihi);
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(20);
        personel.setProfilFoto("def_pfp.png");
        return personelRepository.saveAndFlush(personel);
    }
}
