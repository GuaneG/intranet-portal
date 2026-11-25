package com.jforce.backend;

import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.repository.YetenekRepository;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PersonelYonetimControllerIntegrationTest
        extends AbstractIntegrationTest {

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
    private YetenekRepository yetenekRepository;

    private Rol adminRolu;
    private Rol personelRolu;
    private Rol yoneticiRolu;
    private Rol hrRolu;

    private Departman bilgiTeknolojileri;
    private Personel hrAktor;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        yetenekRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        adminRolu = rolKaydet(1, RolAdi.ADMIN);
        personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        yoneticiRolu = rolKaydet(3, RolAdi.YONETICI);
        hrRolu = rolKaydet(4, RolAdi.HR);

        bilgiTeknolojileri = departmanKaydet("Bilgi Teknolojileri");

        hrAktor = personelKaydet("hr.aktor", "Zehra", "HR", hrRolu, bilgiTeknolojileri, true);
    }

    @Test
    void tokenOlmadanPersonelYonetimListesineErisilemez() throws Exception {
        mockMvc.perform(get("/api/hr/personel"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @ParameterizedTest
    @EnumSource(value = RolAdi.class, names = {"PERSONEL", "YONETICI"})
    void personelVeYoneticiYonetimListesineErisemez(RolAdi rolAdi) throws Exception {
        Rol rol = rolAdi == RolAdi.PERSONEL ? personelRolu : yoneticiRolu;

        Personel aktor = personelKaydet(
                "yetkisiz." + rolAdi.name().toLowerCase(),
                "Yetkisiz",
                rolAdi.name(),
                rol,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        aktor,
                                        rolAdi
                                )
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void pasifHrYonetimListesineErisemez()
            throws Exception {
        String token = token(hrAktor, RolAdi.HR);

        hrAktor.setCalisiyorMu(false);
        personelRepository.saveAndFlush(hrAktor);

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void tokendakiPersonelBulunamazsa404Doner()
            throws Exception {
        String token = jwtService.generateToken(
                "olmayan-personel-id",
                RolAdi.HR
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101"
    })
    void gecersizPagination400Doner(
            int page,
            int size
    ) throws Exception {
        mockMvc.perform(get("/api/hr/personel")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "page",
            "size",
            "departmanId"
    })
    void sayisalParametreMetinse400Doner(
            String parametre
    ) throws Exception {
        mockMvc.perform(get("/api/hr/personel")
                        .param(parametre, "abc")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"rol", "aktif"})
    void enumVeyaBooleanParametreGecersizse400Doner(
            String parametre
    ) throws Exception {
        mockMvc.perform(get("/api/hr/personel")
                        .param(parametre, "abc")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void pozitifOlmayanDepartmanIdsi400Doner(
            int departmanId
    ) throws Exception {
        mockMvc.perform(get("/api/hr/personel")
                        .param(
                                "departmanId",
                                String.valueOf(departmanId)
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void aktifFiltresiYoksaAktifVePasifHedeflerDoner()
            throws Exception {
        personelKaydet(
                "aktif.personel",
                "Aktif",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "pasif.personel",
                "Pasif",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                false
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(2));
    }

    @Test
    void hrIzinliPersonelinYonetimDetayiniGoruntuler()
            throws Exception {
        Personel yonetici = personelKaydet(
                "detay.yonetici",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        Personel hedef = personelKaydet(
                "detay.personel",
                "Ayşe",
                "Yılmaz",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        hedef.setYonetici(yonetici);
        hedef.setDogumTarihi(
                LocalDate.of(1995, 4, 12)
        );
        hedef.setePosta(
                "ayse.yilmaz@example.com"
        );
        hedef.setIsGirisTarihi(
                LocalDate.of(2022, 1, 10)
        );
        hedef.setTelNo("05550000000");
        hedef.setMezunOkul("Örnek Üniversitesi");
        hedef.setMezunBolum(
                "Bilgisayar Mühendisliği"
        );
        hedef.setMezuniyetYil(2018);

        hedef = personelRepository.saveAndFlush(hedef);

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.personelId")
                                .value(hedef.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.kullaniciAdi")
                                .value("detay.personel")
                )
                .andExpect(
                        jsonPath("$.ad")
                                .value("Ayşe")
                )
                .andExpect(
                        jsonPath("$.soyad")
                                .value("Yılmaz")
                )
                .andExpect(
                        jsonPath("$.dogumTarihi")
                                .value("1995-04-12")
                )
                .andExpect(
                        jsonPath("$.ePosta")
                                .value("ayse.yilmaz@example.com")
                )
                .andExpect(
                        jsonPath("$.departmanId")
                                .value(
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                )
                )
                .andExpect(
                        jsonPath("$.yoneticiId")
                                .value(yonetici.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.isGirisTarihi")
                                .value("2022-01-10")
                )
                .andExpect(
                        jsonPath("$.telNo")
                                .value("05550000000")
                )
                .andExpect(
                        jsonPath("$.mezunOkul")
                                .value("Örnek Üniversitesi")
                )
                .andExpect(
                        jsonPath("$.mezunBolum")
                                .value("Bilgisayar Mühendisliği")
                )
                .andExpect(
                        jsonPath("$.mezuniyetYili")
                                .value(2018)
                )
                .andExpect(
                        jsonPath("$.rol")
                                .value("PERSONEL")
                )
                .andExpect(
                        jsonPath("$.aktif")
                                .value(true)
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void bulunmayanPersonelDetayi404Doner()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                "olmayan-personel-id"
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
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );
    }

    @Test
    void hrKendiYonetimDetayiniGoruntuleyemez()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
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
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void hrYoneticiDetayiniGoruntuleyemez()
            throws Exception {
        Personel yonetici = personelKaydet(
                "detay.yasak.yonetici",
                "Yasak",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminKendiYonetimDetayiniGoruntuleyemez()
            throws Exception {
        Personel admin = personelKaydet(
                "detay.admin.aktor",
                "Admin",
                "Aktör",
                adminRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                admin.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                admin,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminBaskaAdminDetayiniGoruntuleyemez()
            throws Exception {
        Personel aktor = personelKaydet(
                "detay.admin.aktor",
                "Admin",
                "Aktör",
                adminRolu,
                bilgiTeknolojileri,
                true
        );
        Personel hedef = personelKaydet(
                "detay.admin.hedef",
                "Admin",
                "Hedef",
                adminRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                aktor,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void adminYoneticiDetayiniGoruntuleyebilir()
            throws Exception {
        Personel admin = personelKaydet(
                "detay.admin.aktor",
                "Admin",
                "Aktör",
                adminRolu,
                bilgiTeknolojileri,
                true
        );
        Personel yonetici = personelKaydet(
                "detay.admin.yonetici",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                yonetici.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                admin,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.personelId")
                                .value(yonetici.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.rol")
                                .value("YONETICI")
                );
    }

    @Test
    void yonetimDetayiMevcutYetenekleriAlfabetikDoner() throws Exception {
        Personel hedef = personelKaydet(
                "detay.yetenekli",
                "Yetenekli",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        Yetenek javaYetenegi = new Yetenek();
        javaYetenegi.setYetenekAdi("Java");
        javaYetenegi = yetenekRepository.saveAndFlush(javaYetenegi);

        Yetenek docker = new Yetenek();
        docker.setYetenekAdi("Docker");
        docker = yetenekRepository.saveAndFlush(docker);

        PersonelYetenek javaBaglantisi = new PersonelYetenek();
        javaBaglantisi.setId(new PersonelYetenekId(hedef.getPersonelId(), javaYetenegi.getYetenekId()));
        javaBaglantisi.setPersonel(hedef);
        javaBaglantisi.setYetenek(javaYetenegi);

        PersonelYetenek dockerBaglantisi = new PersonelYetenek();
        dockerBaglantisi.setId(new PersonelYetenekId(hedef.getPersonelId(), docker.getYetenekId()));
        dockerBaglantisi.setPersonel(hedef);
        dockerBaglantisi.setYetenek(docker);
        personelYetenekRepository.saveAllAndFlush(java.util.List.of(javaBaglantisi, dockerBaglantisi));

        mockMvc.perform(get("/api/hr/personel/{personelId}", hedef.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(hrAktor, RolAdi.HR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yetenekler.length()").value(2))
                .andExpect(jsonPath("$.yetenekler[0].id").value(docker.getYetenekId()))
                .andExpect(jsonPath("$.yetenekler[0].ad").value("Docker"))
                .andExpect(jsonPath("$.yetenekler[1].id").value(javaYetenegi.getYetenekId()))
                .andExpect(jsonPath("$.yetenekler[1].ad").value("Java"));
    }

    @Test
    void yonetimDetayiYetenegiOlmayanHedefteBosListeDoner() throws Exception {
        Personel hedef = personelKaydet(
                "detay.yeteneksiz",
                "Yeteneksiz",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel/{personelId}", hedef.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(hrAktor, RolAdi.HR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yetenekler").isArray())
                .andExpect(jsonPath("$.yetenekler").isEmpty());
    }

    @Test
    void detayResponseHassasAlanlariIcermez()
            throws Exception {
        Personel hedef = personelKaydet(
                "detay.guvenli",
                "Güvenli",
                "Detay",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.parolaHash")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.parolaDegistirilmeliMi")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.sicilNumarasi")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.yillikIzinHakki")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.profilFoto")
                                .value("def_pfp.png")
                )
                .andExpect(
                        jsonPath("$.isCikisTarihi")
                                .doesNotExist()
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void lookupYalnizAktifDepartmanYoneticileriniSiraliDoner()
            throws Exception {
        Departman insanKaynaklari =
                departmanKaydet("İnsan Kaynakları");

        Personel ayse = personelKaydet(
                "lookup.ayse",
                "Ayşe",
                "Yönetici",
                yoneticiRolu,
                insanKaynaklari,
                true
        );
        Personel mert = personelKaydet(
                "lookup.mert",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                insanKaynaklari,
                true
        );

        personelKaydet(
                "lookup.pasif",
                "Pasif",
                "Yönetici",
                yoneticiRolu,
                insanKaynaklari,
                false
        );
        personelKaydet(
                "lookup.personel",
                "Aktif",
                "Personel",
                personelRolu,
                insanKaynaklari,
                true
        );
        personelKaydet(
                "lookup.baska.departman",
                "Başka",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        insanKaynaklari
                                                .getDepartmanId()
                                                .toString()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(
                        jsonPath("$[0].personelId")
                                .value(ayse.getPersonelId())
                )
                .andExpect(
                        jsonPath("$[0].ad")
                                .value("Ayşe")
                )
                .andExpect(
                        jsonPath("$[0].soyad")
                                .value("Yönetici")
                )
                .andExpect(
                        jsonPath("$[0].kullaniciAdi")
                                .value("lookup.ayse")
                )
                .andExpect(
                        jsonPath("$[1].personelId")
                                .value(mert.getPersonelId())
                );
    }

    @Test
    void lookupHaricPersonelIdsiniSonuctanCikarir()
            throws Exception {
        Personel haricTutulan = personelKaydet(
                "lookup.haric",
                "Ayşe",
                "Hariç",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );
        Personel kalan = personelKaydet(
                "lookup.kalan",
                "Mert",
                "Kalan",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .param(
                                        "haricPersonelId",
                                        haricTutulan.getPersonelId()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].personelId")
                                .value(kalan.getPersonelId())
                );
    }

    @Test
    void lookupEslesmeYoksaBosListeDoner()
            throws Exception {
        Departman bosDepartman =
                departmanKaydet("Yöneticisiz Departman");

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bosDepartman
                                                .getDepartmanId()
                                                .toString()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void lookupDepartmaniBulunamazsa404Doner()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param("departmanId", "999999")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Departman bulunamadı.")
                );
    }

    @Test
    void yoneticisiOlmayanPersonelinYoneticiIdsiNullDoner()
            throws Exception {
        Personel hedef = personelKaydet(
                "detay.yoneticisiz",
                "Deniz",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/{personelId}",
                                hedef.getPersonelId()
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.personelId")
                                .value(hedef.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.yoneticiId")
                                .value(nullValue())
                );
    }

    @Test
    void bosHaricPersonelIdFiltreUygulamaz()
            throws Exception {
        personelKaydet(
                "lookup.bos.bir",
                "Ayşe",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "lookup.bos.iki",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .param("haricPersonelId", "   ")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void olmayanHaricPersonelIdVarlikKontroluOlusturmaz()
            throws Exception {
        Personel yonetici = personelKaydet(
                "lookup.olmayanhariç",
                "Ayşe",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .param(
                                        "haricPersonelId",
                                        "olmayan-personel-id"
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].personelId")
                                .value(yonetici.getPersonelId())
                );
    }

    @Test
    void lookupDepartmanIdEksikse400Doner()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void lookupDepartmanIdPozitifDegilse400Doner(
            int departmanId
    ) throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        String.valueOf(departmanId)
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void lookupDepartmanIdMetinse400Doner()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param("departmanId", "abc")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                hrAktor,
                                                RolAdi.HR
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.message")
                                .value("Geçersiz istek")
                );
    }

    @Test
    void adminLookupEndpointineErisebilir()
            throws Exception {
        Personel admin = personelKaydet(
                "lookup.admin",
                "Admin",
                "Aktör",
                adminRolu,
                bilgiTeknolojileri,
                true
        );
        Personel yonetici = personelKaydet(
                "lookup.admin.yonetici",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token(
                                                admin,
                                                RolAdi.ADMIN
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].personelId")
                                .value(yonetici.getPersonelId())
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(
            value = RolAdi.class,
            names = {"PERSONEL", "YONETICI"}
    )
    void personelVeYoneticiLookupEndpointineErisemez(
            RolAdi rolAdi
    ) throws Exception {
        Rol rol = rolAdi == RolAdi.PERSONEL
                ? personelRolu
                : yoneticiRolu;

        Personel aktor = personelKaydet(
                "lookup.yetkisiz."
                        + rolAdi.name().toLowerCase(),
                "Yetkisiz",
                rolAdi.name(),
                rol,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
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
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void pasifHrLookupEndpointineErisemez()
            throws Exception {
        String token = token(hrAktor, RolAdi.HR);

        hrAktor.setCalisiyorMu(false);
        personelRepository.saveAndFlush(hrAktor);

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void lookupPrincipalBulunamazsa404Doner()
            throws Exception {
        String token = jwtService.generateToken(
                "olmayan-personel-id",
                RolAdi.HR
        );

        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer " + token
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.message")
                                .value("Personel bulunamadı.")
                );
    }

    @Test
    void tokenOlmadanLookupEndpointineErisilemez()
            throws Exception {
        mockMvc.perform(
                        get(
                                "/api/hr/personel/"
                                        + "atanabilir-yoneticiler"
                        )
                                .param(
                                        "departmanId",
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                                .toString()
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void listeResponseHassasAlanlariIcermez()
            throws Exception {
        personelKaydet(
                "guvenli.response",
                "Güvenli",
                "Response",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].parolaHash").doesNotExist())
                .andExpect(
                        jsonPath("$.icerik[0].parolaDegistirilmeliMi").doesNotExist()
                )
                .andExpect(jsonPath("$.icerik[0].sicilNumarasi").doesNotExist())
                .andExpect(
                        jsonPath("$.icerik[0].yillikIzinHakki")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.icerik[0].profilFoto")
                                .value("def_pfp.png")
                )
                .andExpect(
                        jsonPath("$.icerik[0].isCikisTarihi")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.icerik[0].dogumTarihi")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.icerik[0].ePosta")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.icerik[0].yoneticiId")
                                .doesNotExist()
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @Test
    void hrListesindeYalnizPersonelVeBaskaHrGorunur()
            throws Exception {
        Personel personel = personelKaydet(
                "ayse.personel",
                "Ayşe",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Personel baskaHr = personelKaydet(
                "beril.hr",
                "Beril",
                "HR",
                hrRolu,
                bilgiTeknolojileri,
                true
        );

        personelKaydet(
                "mert.yonetici",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "admin.hedef",
                "Selin",
                "Admin",
                adminRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(20))
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(
                        jsonPath("$.icerik[0].personelId")
                                .value(personel.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.icerik[0].ad")
                                .value("Ayşe")
                )
                .andExpect(
                        jsonPath("$.icerik[0].soyad")
                                .value("Personel")
                )
                .andExpect(
                        jsonPath("$.icerik[0].kullaniciAdi")
                                .value("ayse.personel")
                )
                .andExpect(
                        jsonPath("$.icerik[0].rol")
                                .value("PERSONEL")
                )
                .andExpect(
                        jsonPath("$.icerik[0].departmanId")
                                .value(
                                        bilgiTeknolojileri
                                                .getDepartmanId()
                                )
                )
                .andExpect(
                        jsonPath("$.icerik[0].departmanAdi")
                                .value("Bilgi Teknolojileri")
                )
                .andExpect(
                        jsonPath("$.icerik[0].aktif")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.icerik[1].personelId")
                                .value(baskaHr.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.icerik[1].rol")
                                .value("HR")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "  AYŞE  ",
            "yılmaz",
            "AYŞE yılMAZ",
            "ÖZEL.KULLANICI"
    })
    void aramaAdSoyadTamAdVeKullaniciAdindaCalisir(
            String arama
    ) throws Exception {
        Personel eslesen = personelKaydet(
                "ozel.kullanici",
                "Ayşe",
                "Yılmaz",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        personelKaydet(
                "deniz.kaya",
                "Deniz",
                "Kaya",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("arama", arama)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].personelId")
                                .value(eslesen.getPersonelId())
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void bosAramaFiltreUygulanmamisGibiDavranir(
            String arama
    ) throws Exception {
        personelKaydet(
                "ayse.personel",
                "Ayşe",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("arama", arama)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1));
    }

    @Test
    void aramaDepartmanRolVeAktifFiltreleriAndUygular()
            throws Exception {
        Departman insanKaynaklari =
                departmanKaydet("İnsan Kaynakları");

        Personel eslesen = personelKaydet(
                "ayse.uygun",
                "Ayşe",
                "Uygun",
                personelRolu,
                insanKaynaklari,
                false
        );

        personelKaydet(
                "ayse.yanlis.departman",
                "Ayşe",
                "Yanlış Departman",
                personelRolu,
                bilgiTeknolojileri,
                false
        );
        personelKaydet(
                "ayse.yanlis.rol",
                "Ayşe",
                "Yanlış Rol",
                hrRolu,
                insanKaynaklari,
                false
        );
        personelKaydet(
                "ayse.yanlis.aktiflik",
                "Ayşe",
                "Yanlış Aktiflik",
                personelRolu,
                insanKaynaklari,
                true
        );
        personelKaydet(
                "deniz.yanlis.arama",
                "Deniz",
                "Yanlış Arama",
                personelRolu,
                insanKaynaklari,
                false
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("arama", "  AYŞE  ")
                        .param(
                                "departmanId",
                                insanKaynaklari
                                        .getDepartmanId()
                                        .toString()
                        )
                        .param("rol", "PERSONEL")
                        .param("aktif", "false")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.toplamSayfa").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].personelId")
                                .value(eslesen.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.icerik[0].aktif")
                                .value(false)
                );
    }

    @Test
    void aramaCountSorgusuVePaginationIleTutarlidir()
            throws Exception {
        personelKaydet(
                "ayse.a",
                "Ayşe",
                "A",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "ayse.b",
                "Ayşe",
                "B",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        personelKaydet(
                "deniz.c",
                "Deniz",
                "C",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("arama", "ayşe")
                        .param("page", "1")
                        .param("size", "1")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(1))
                .andExpect(jsonPath("$.sayfaBoyutu").value(1))
                .andExpect(jsonPath("$.toplamKayit").value(2))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(
                        jsonPath("$.icerik[0].soyad")
                                .value("B")
                );
    }

    @Test
    void adminRolFiltresiGecerliAmaBosSayfaDoner()
            throws Exception {
        personelKaydet(
                "ayse.personel",
                "Ayşe",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("rol", "ADMIN")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik").isEmpty())
                .andExpect(jsonPath("$.toplamKayit").value(0))
                .andExpect(jsonPath("$.toplamSayfa").value(0));
    }

    @Test
    void bulunmayanDepartmanFiltresiBosSayfaDoner()
            throws Exception {
        personelKaydet(
                "ayse.personel",
                "Ayşe",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .param("departmanId", "999999")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        hrAktor,
                                        RolAdi.HR
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik").isEmpty())
                .andExpect(jsonPath("$.toplamKayit").value(0))
                .andExpect(jsonPath("$.toplamSayfa").value(0));
    }

    @Test
    void adminListesindePersonelHrVeYoneticiGorunur()
            throws Exception {
        Personel adminAktor = personelKaydet(
                "admin.aktor",
                "Admin",
                "Aktör",
                adminRolu,
                bilgiTeknolojileri,
                true
        );
        Personel personel = personelKaydet(
                "ayse.personel",
                "Ayşe",
                "Personel",
                personelRolu,
                bilgiTeknolojileri,
                true
        );
        Personel yonetici = personelKaydet(
                "mert.yonetici",
                "Mert",
                "Yönetici",
                yoneticiRolu,
                bilgiTeknolojileri,
                true
        );

        personelKaydet(
                "baska.admin",
                "Selin",
                "Admin",
                adminRolu,
                bilgiTeknolojileri,
                true
        );

        mockMvc.perform(get("/api/hr/personel")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(
                                        adminAktor,
                                        RolAdi.ADMIN
                                )
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.icerik.length()").value(3))
                .andExpect(jsonPath("$.icerik[0].personelId").value(personel.getPersonelId()))
                .andExpect(jsonPath("$.icerik[0].rol").value("PERSONEL"))
                .andExpect(jsonPath("$.icerik[1].personelId").value(yonetici.getPersonelId()))
                .andExpect(
                        jsonPath("$.icerik[1].rol")
                                .value("YONETICI")
                )
                .andExpect(
                        jsonPath("$.icerik[2].personelId")
                                .value(hrAktor.getPersonelId())
                )
                .andExpect(
                        jsonPath("$.icerik[2].rol")
                                .value("HR")
                );

        assertEquals(0L, auditLogRepository.count());
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);

        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String departmanAdi) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(departmanAdi);

        return departmanRepository.save(departman);
    }

    private Personel personelKaydet(String kullaniciAdi, String ad, String soyad, Rol rol, Departman departman, boolean aktif) {
        Personel personel = new Personel();
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setParolaHash("test-hash");
        personel.setAdi(ad);
        personel.setSoyadi(soyad);
        personel.setDogumTarihi(LocalDate.of(1995, 1, 1));
        personel.setePosta(kullaniciAdi + "@test.local");
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setIsGirisTarihi(LocalDate.of(2022, 1, 1));
        personel.setYillikIzinHakki(14);
        personel.setProfilFoto("def_pfp.png");
        personel.setParolaDegistirilmeliMi(false);

        return personelRepository.saveAndFlush(personel);
    }

    private String token(Personel personel, RolAdi rolAdi) {
        return jwtService.generateToken(personel.getPersonelId(), rolAdi);
    }
}
