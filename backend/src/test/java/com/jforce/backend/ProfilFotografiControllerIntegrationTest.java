package com.jforce.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jforce.backend.model.dto.request.ProfilUpdateRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.ProfilFotografiService;
import com.jforce.backend.service.ProfilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AutoConfigureMockMvc
class ProfilFotografiControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Path BEKLENEN_TEST_DIZINLERI = Path.of(
            "intranet-portal-test",
            "profil-fotograflari"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PersonelRepository personelRepository;

    @Autowired
    private DepartmanRepository departmanRepository;

    @Autowired
    private RolRepository rolRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProfilFotografiRollbackTestService rollbackTestService;

    @Autowired
    private ProfilFotografiService profilFotografiService;

    @Autowired
    private ProfilFotografiStaleWriteTestService staleWriteTestService;

    private Personel personel;

    @BeforeEach
    void setUp() throws IOException {
        testDepolamasiniTemizle();
        auditLogRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        Rol rol = new Rol();
        rol.setRolId(2);
        rol.setRolAdi(RolAdi.PERSONEL);
        rol = rolRepository.save(rol);

        Departman departman = new Departman();
        departman.setDepartmanAdi("Bilgi Teknolojileri");
        departman = departmanRepository.save(departman);

        personel = yeniPersonel("test.personel", "test.personel@test.local", rol, departman);
        personel = personelRepository.saveAndFlush(personel);
    }

    @AfterEach
    void tearDown() throws IOException {
        testDepolamasiniTemizle();
    }

    @Test
    void gecerliJpegYukleme200VeUuidJpgAnahtariDoner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile dosya = jpegDosyasi("portre.jpg");

        mockMvc.perform(multipart("/api/profil/fotograf")
                        .file(dosya)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilFoto").value(
                        org.hamcrest.Matchers.matchesPattern(
                                "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$"
                        )
                ));
    }

    @Test
    void yuklenenAyniProfilFotografiAnahtariTumUygulanabilirResponseLaraTasinir()
            throws Exception {
        personel.setYillikIzinHakki(20);
        personel = personelRepository.saveAndFlush(personel);

        Rol hrRolu = new Rol();
        hrRolu.setRolId(4);
        hrRolu.setRolAdi(RolAdi.HR);
        hrRolu = rolRepository.saveAndFlush(hrRolu);

        Personel hrAktor = yeniPersonel(
                "zehra.hr",
                "zehra.hr@test.local",
                hrRolu,
                personel.getDepartman()
        );
        hrAktor.setAdi("Zehra");
        hrAktor.setSoyadi("HR");
        hrAktor.setYillikIzinHakki(20);
        hrAktor = personelRepository.saveAndFlush(hrAktor);

        String personelToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String hrToken = jwtService.generateToken(
                hrAktor.getPersonelId(),
                RolAdi.HR
        );
        String yuklenenAnahtar = profilFotografiYukle(
                personelToken,
                jpegDosyasi("response-propagation.jpg")
        );

        mockMvc.perform(get("/api/profil")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + personelToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilFoto").value(yuklenenAnahtar));

        mockMvc.perform(get("/api/ekip-dizini")
                        .param("arama", "  Test Personel  ")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + personelToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].profilFoto").value(yuklenenAnahtar))
                .andExpect(jsonPath("$.icerik[0].yoneticinMi").value(false));

        mockMvc.perform(get("/api/dashboard/ozet")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + personelToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aktifEkipUyesiSayisi").value(2))
                .andExpect(jsonPath("$.ekipDiziniOzeti.length()").value(2))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].profilFoto").value(yuklenenAnahtar))
                .andExpect(jsonPath("$.ekipDiziniOzeti[0].yoneticinMi").value(false))
                .andExpect(jsonPath("$.ekipDiziniOzeti[1].profilFoto").value("def_pfp.png"));

        mockMvc.perform(get("/api/hr/personel")
                        .param("arama", "  test.personel  ")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + hrToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.icerik[0].profilFoto").value(yuklenenAnahtar));

        mockMvc.perform(get(
                                "/api/hr/personel/{personelId}",
                                personel.getPersonelId()
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + hrToken
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilFoto").value(yuklenenAnahtar));

        assertEquals(1L, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifTumRollerKendiProfilFotografiniYukleyebilir(RolAdi rolAdi) throws Exception {
        personelinRolunuDegistir(rolAdi);
        String token = jwtService.generateToken(personel.getPersonelId(), rolAdi);

        mockMvc.perform(profilFotografiYuklemeIstegi(token, jpegDosyasi("rol-portresi.jpg")))
                .andExpect(status().isOk());

        Personel guncellenenPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();
        assertTrue(guncellenenPersonel.getProfilFoto().endsWith(".jpg"));
    }

    @Test
    void yuklemeYalnizPrincipalSatiriniDegistirirVeStorageAnahtariylaDbEslesir()
            throws Exception {
        Personel digerPersonel = yeniPersonel(
                "diger.personel",
                "diger.personel@test.local",
                personel.getRol(),
                personel.getDepartman()
        );
        digerPersonel = personelRepository.saveAndFlush(digerPersonel);
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        String responseBody = mockMvc.perform(
                        profilFotografiYuklemeIstegi(token, jpegDosyasi("kendi-portrem.jpg"))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(responseBody);
        String responseAnahtari = response.path("profilFoto").asText();

        Personel guncellenenPrincipal = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();
        Personel degismeyenDigerPersonel = personelRepository
                .findById(digerPersonel.getPersonelId())
                .orElseThrow();

        assertNotEquals("def_pfp.png", responseAnahtari);
        assertEquals(responseAnahtari, guncellenenPrincipal.getProfilFoto());
        assertEquals("def_pfp.png", degismeyenDigerPersonel.getProfilFoto());
        assertTrue(Files.isRegularFile(testDepolamaKoku().resolve(responseAnahtari)));
    }

    @Test
    void gercekYuklemeTekVeGuvenliAuditKaydiOlusturur() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        String orijinalDosyaAdi = "gizli-orijinal-isim.jpg";

        String responseBody = mockMvc.perform(
                        profilFotografiYuklemeIstegi(token, jpegDosyasi(orijinalDosyaAdi))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String yeniAnahtar = objectMapper.readTree(responseBody).path("profilFoto").asText();

        List<AuditLog> auditKayitlari = auditLogRepository.findAll();
        assertEquals(1, auditKayitlari.size());

        AuditLog audit = auditKayitlari.getFirst();
        assertEquals(AuditEylem.PROFIL_FOTOGRAFI_GUNCELLENDI, audit.getEylemTipi());
        assertEquals(personel.getPersonelId(), audit.getPersonel().getPersonelId());
        assertEquals(personel.getPersonelId(), audit.getHedefPersonel().getPersonelId());
        assertEquals("test.personel", audit.getKullaniciAdi());
        assertEquals("Profil fotoğrafı güncellendi.", audit.getDetay());
        assertFalse(audit.getDetay().contains(orijinalDosyaAdi));
        assertFalse(audit.getDetay().contains(yeniAnahtar));
        assertFalse(audit.getDetay().contains(testDepolamaKoku().toString()));
        assertFalse(audit.getDetay().contains(MediaType.IMAGE_JPEG_VALUE));
        assertFalse(audit.getDetay().contains("def_pfp.png"));
    }

    @Test
    void ikinciYuklemeYeniAnahtariDbdeTutarVeCommitSonrasiEskiDosyayiSiler()
            throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        String eskiAnahtar = profilFotografiYukle(
                token,
                jpegDosyasi("eski-portre.jpg")
        );
        Set<String> ilkYuklemeSonrasiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long ilkYuklemeSonrasiAuditSayisi = auditLogRepository.count();

        String yeniAnahtar = profilFotografiYukle(
                token,
                pngDosyasi("yeni-portre.png", 4, 3)
        );

        assertNotEquals(eskiAnahtar, yeniAnahtar);
        assertEquals(yeniAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(Set.of(eskiAnahtar), ilkYuklemeSonrasiDosyalar);
        assertEquals(Set.of(yeniAnahtar), testDepolamasindakiDosyaAnahtarlari());
        assertEquals(ilkYuklemeSonrasiAuditSayisi + 1, auditLogRepository.count());
    }

    @Test
    void gecersizIkinciYuklemeEskiDbAnahtariniDosyayiVeAuditiKorur()
            throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        String eskiAnahtar = profilFotografiYukle(
                token,
                jpegDosyasi("korunacak-portre.jpg")
        );
        Set<String> ikinciYuklemeOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long ikinciYuklemeOncesiAuditSayisi = auditLogRepository.count();
        MockMultipartFile gercekJpeg = jpegDosyasi("uyusmayan-portre.jpg");
        MockMultipartFile gecersizDosya = new MockMultipartFile(
                "dosya",
                "uyusmayan-portre.png",
                MediaType.IMAGE_PNG_VALUE,
                gercekJpeg.getBytes()
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, gecersizDosya))
                .andExpect(status().isUnsupportedMediaType());

        assertEquals(eskiAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(
                ikinciYuklemeOncesiDosyalar,
                testDepolamasindakiDosyaAnahtarlari()
        );
        assertEquals(ikinciYuklemeOncesiAuditSayisi, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHataVerirseEskiDbVeDosyaKorunurYeniDosyaVeAuditRollbackOlur()
            throws Exception {
        String eskiAnahtar = profilFotografiService.profilFotografimiGuncelle(
                personel.getPersonelId(),
                jpegDosyasi("rollback-eski-portre.jpg")
        ).profilFoto();
        Set<String> rollbackOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long rollbackOncesiAuditSayisi = auditLogRepository.count();

        assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.yukleVeHataFirlat(
                        personel.getPersonelId(),
                        pngDosyasi("rollback-yeni-portre.png", 4, 3)
                )
        );

        assertEquals(eskiAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(rollbackOncesiDosyalar, testDepolamasindakiDosyaAnahtarlari());
        assertTrue(Files.isRegularFile(testDepolamaKoku().resolve(eskiAnahtar)));
        assertEquals(rollbackOncesiAuditSayisi, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void stalePersonelTransactionYeniFotografCommitindenSonraReddedilir()
            throws Exception {
        String eskiAnahtar = profilFotografiService.profilFotografimiGuncelle(
                personel.getPersonelId(),
                jpegDosyasi("stale-eski-portre.jpg")
        ).profilFoto();
        long staleIslemOncesiAuditSayisi = auditLogRepository.count();

        RuntimeException staleIslemHatasi = null;
        try {
            staleWriteTestService.staleProfilGuncellemesiniTamamla(
                    personel.getPersonelId(),
                    pngDosyasi("stale-yeni-portre.png", 4, 3)
            );
        } catch (RuntimeException ex) {
            staleIslemHatasi = ex;
        }

        Personel guncelPersonel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();
        Set<String> guncelDosyalar = testDepolamasindakiDosyaAnahtarlari();
        RuntimeException finalStaleIslemHatasi = staleIslemHatasi;
        assertAll(
                () -> assertTrue(
                        finalStaleIslemHatasi
                                instanceof OptimisticLockingFailureException,
                        "Stale Personel transaction optimistic conflict ile reddedilmelidir."
                ),
                () -> assertNotEquals(
                        eskiAnahtar,
                        guncelPersonel.getProfilFoto()
                ),
                () -> assertEquals(
                        Set.of(guncelPersonel.getProfilFoto()),
                        guncelDosyalar
                ),
                () -> assertFalse(
                        Files.exists(testDepolamaKoku().resolve(eskiAnahtar))
                ),
                () -> assertNull(guncelPersonel.getTelNo()),
                () -> assertEquals(
                        staleIslemOncesiAuditSayisi + 1,
                        auditLogRepository.count()
                )
        );
    }

    @Test
    void customProfilFotografiniSilme204DbSentinelTekGuvenliAuditVeDosyaTemizligiUretir()
            throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        String orijinalDosyaAdi = "silinecek-gizli-isim.jpg";
        String eskiAnahtar = profilFotografiYukle(
                token,
                jpegDosyasi(orijinalDosyaAdi)
        );
        long silmeOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiSilmeIstegi(token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals("def_pfp.png", personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertFalse(Files.exists(testDepolamaKoku().resolve(eskiAnahtar)));
        assertEquals(Set.of(), testDepolamasindakiDosyaAnahtarlari());
        assertEquals(silmeOncesiAuditSayisi + 1, auditLogRepository.count());

        List<AuditLog> silmeAuditleri = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getEylemTipi()
                        == AuditEylem.PROFIL_FOTOGRAFI_SILINDI)
                .toList();
        assertEquals(1, silmeAuditleri.size());

        AuditLog silmeAuditi = silmeAuditleri.getFirst();
        assertEquals(personel.getPersonelId(), silmeAuditi.getPersonel().getPersonelId());
        assertEquals(
                personel.getPersonelId(),
                silmeAuditi.getHedefPersonel().getPersonelId()
        );
        assertEquals("test.personel", silmeAuditi.getKullaniciAdi());
        assertEquals("Profil fotoğrafı silindi.", silmeAuditi.getDetay());
        assertFalse(silmeAuditi.getDetay().contains(orijinalDosyaAdi));
        assertFalse(silmeAuditi.getDetay().contains(eskiAnahtar));
        assertFalse(silmeAuditi.getDetay().contains(testDepolamaKoku().toString()));
        assertFalse(silmeAuditi.getDetay().contains("def_pfp.png"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "def_pfp.png",
            "legacy-profil.jpg"
    })
    void sentinelVeyaMalformedProfilFotografiniSilme204NoOpVeIlgisizDosyayiKorur(
            String mevcutAnahtar
    )
            throws Exception {
        personel.setProfilFoto(mevcutAnahtar);
        personel = personelRepository.saveAndFlush(personel);
        Personel digerPersonel = ikinciAktifPersoneliKaydet();
        String digerToken = jwtService.generateToken(
                digerPersonel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String ilgisizAnahtar = profilFotografiYukle(
                digerToken,
                jpegDosyasi("ilgisiz-portre.jpg")
        );
        Set<String> silmeOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long silmeOncesiAuditSayisi = auditLogRepository.count();
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(profilFotografiSilmeIstegi(token))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertEquals(mevcutAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(ilgisizAnahtar, personelRepository
                .findById(digerPersonel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(silmeOncesiDosyalar, testDepolamasindakiDosyaAnahtarlari());
        assertEquals(Set.of(ilgisizAnahtar), silmeOncesiDosyalar);
        assertEquals(silmeOncesiAuditSayisi, auditLogRepository.count());
    }

    @Test
    @WithMockUser(roles = "PERSONEL")
    void disTransactionHataVerirseSilmeDbDosyaVeAuditIleRollbackOlur()
            throws Exception {
        String eskiAnahtar = profilFotografiService.profilFotografimiGuncelle(
                personel.getPersonelId(),
                jpegDosyasi("silme-rollback-portre.jpg")
        ).profilFoto();
        Set<String> rollbackOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long rollbackOncesiAuditSayisi = auditLogRepository.count();

        IllegalStateException hata = assertThrows(
                IllegalStateException.class,
                () -> rollbackTestService.silVeHataFirlat(personel.getPersonelId())
        );

        assertEquals(
                "Rollback davranışını doğrulamak için test hatası.",
                hata.getMessage()
        );
        assertEquals(eskiAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(rollbackOncesiDosyalar, testDepolamasindakiDosyaAnahtarlari());
        assertTrue(Files.isRegularFile(testDepolamaKoku().resolve(eskiAnahtar)));
        assertEquals(rollbackOncesiAuditSayisi, auditLogRepository.count());
    }

    @ParameterizedTest
    @EnumSource(RolAdi.class)
    void aktifTumRollerKendiProfilFotografiniSilebilir(RolAdi rolAdi)
            throws Exception {
        personelinRolunuDegistir(rolAdi);
        String token = jwtService.generateToken(personel.getPersonelId(), rolAdi);
        profilFotografiYukle(token, jpegDosyasi("role-gore-silme.jpg"));

        mockMvc.perform(profilFotografiSilmeIstegi(token))
                .andExpect(status().isNoContent());

        assertEquals("def_pfp.png", personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(Set.of(), testDepolamasindakiDosyaAnahtarlari());
        assertEquals(1, auditLogRepository.findAll().stream()
                .filter(audit -> audit.getEylemTipi()
                        == AuditEylem.PROFIL_FOTOGRAFI_SILINDI)
                .count());
    }

    @Test
    void tokenOlmadanProfilFotografiSilme401VeYanEtkisizdir() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        String eskiAnahtar = profilFotografiYukle(
                token,
                jpegDosyasi("tokensiz-silme.jpg")
        );
        Set<String> silmeOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long silmeOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(delete("/api/profil/fotograf"))
                .andExpect(status().isUnauthorized());

        basarisizSilmeYanEtkiBirakmadi(
                eskiAnahtar,
                silmeOncesiDosyalar,
                silmeOncesiAuditSayisi
        );
    }

    @Test
    void pasifPrincipalProfilFotografiniSilemez() throws Exception {
        String aktifToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String eskiAnahtar = profilFotografiYukle(
                aktifToken,
                jpegDosyasi("pasif-silme.jpg")
        );
        personel = personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow();
        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);
        assertEquals(eskiAnahtar, personel.getProfilFoto());
        String pasifToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        Set<String> silmeOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long silmeOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiSilmeIstegi(pasifToken))
                .andExpect(status().isForbidden());

        basarisizSilmeYanEtkiBirakmadi(
                eskiAnahtar,
                silmeOncesiDosyalar,
                silmeOncesiAuditSayisi
        );
    }

    @Test
    void principalSatiriYoksaProfilFotografiSilme404VeYanEtkisizdir()
            throws Exception {
        String aktifToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String eskiAnahtar = profilFotografiYukle(
                aktifToken,
                jpegDosyasi("kayip-principal-silme.jpg")
        );
        String kayipPrincipalToken = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );
        Set<String> silmeOncesiDosyalar = testDepolamasindakiDosyaAnahtarlari();
        long silmeOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiSilmeIstegi(kayipPrincipalToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        basarisizSilmeYanEtkiBirakmadi(
                eskiAnahtar,
                silmeOncesiDosyalar,
                silmeOncesiAuditSayisi
        );
    }

    @Test
    void dosyaPartiEksikseKontrollu400Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(multipart("/api/profil/fotograf")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz istek"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void bosDosyaKontrollu400Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "bos.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[0]
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz profil fotoğrafı."))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void dogruPngImzaliBozukGorselKontrollu400Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        byte[] bozukPng = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47,
                0x0d, 0x0a, 0x1a, 0x0a,
                0x01, 0x02, 0x03
        };
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "bozuk.png",
                MediaType.IMAGE_PNG_VALUE,
                bozukPng
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz profil fotoğrafı."))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void desteklenmeyenUzantiKontrollu415Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile gercekPng = pngDosyasi("portre.png", 4, 3);
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "portre.gif",
                MediaType.IMAGE_PNG_VALUE,
                gercekPng.getBytes()
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(
                        "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir."
                ))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void desteklenmeyenMimeKontrollu415Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile gercekJpeg = jpegDosyasi("portre.jpg");
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "portre.jpg",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                gercekJpeg.getBytes()
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(
                        "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir."
                ))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void uzantiVeMimeUyusmazsaKontrollu415Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile gercekPng = pngDosyasi("portre.png", 4, 3);
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "portre.jpg",
                MediaType.IMAGE_PNG_VALUE,
                gercekPng.getBytes()
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(
                        "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir."
                ))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void uzantiVeMimeIleGercekImzaUyusmazsaKontrollu415Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile gercekJpeg = jpegDosyasi("portre.jpg");
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "portre.png",
                MediaType.IMAGE_PNG_VALUE,
                gercekJpeg.getBytes()
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value(
                        "Yalnız JPEG veya PNG profil fotoğrafı yüklenebilir."
                ))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void ikiMegabayttanBirBaytBuyukDosyaKontrollu413Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile dosya = new MockMultipartFile(
                "dosya",
                "buyuk.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[(2 * 1024 * 1024) + 1]
        );

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value(
                        "Profil fotoğrafı en fazla 2 MB olabilir."
                ))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void ikiBinKirkDokuzPikselGenislikKontrollu400Doner() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);
        MockMultipartFile dosya = pngDosyasi("genis.png", 2049, 1);

        mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Geçersiz profil fotoğrafı."))
                .andExpect(jsonPath("$.path").value("/api/profil/fotograf"));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void tokenOlmadanProfilFotografiYukleme401Doner() throws Exception {
        mockMvc.perform(multipart("/api/profil/fotograf")
                        .file(jpegDosyasi("tokensiz.jpg"))
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isUnauthorized());

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void pasifPrincipalProfilFotografiYukleyemez() throws Exception {
        personel.setCalisiyorMu(false);
        personel = personelRepository.saveAndFlush(personel);
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(profilFotografiYuklemeIstegi(token, jpegDosyasi("pasif.jpg")))
                .andExpect(status().isForbidden());

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void principalSatiriYoksaProfilFotografiYukleme404Doner() throws Exception {
        String token = jwtService.generateToken("veritabaninda-olmayan-id", RolAdi.PERSONEL);

        mockMvc.perform(profilFotografiYuklemeIstegi(token, jpegDosyasi("kayip.jpg")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        basarisizYuklemeYanEtkiBirakmadi();
    }

    @Test
    void aktifFarkliKullaniciJpegProfilFotografiniBinaryVeGuvenliHeaderlarlaGetirebilir()
            throws Exception {
        String sahipToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String anahtar = profilFotografiYukle(
                sahipToken,
                jpegDosyasi("get-portre.jpg")
        );
        byte[] saklananByte = Files.readAllBytes(testDepolamaKoku().resolve(anahtar));
        Personel okuyucu = ikinciAktifPersoneliKaydet();
        String okuyucuToken = jwtService.generateToken(
                okuyucu.getPersonelId(),
                RolAdi.PERSONEL
        );
        long getOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiGetirmeIstegi(okuyucuToken, anahtar))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(saklananByte))
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("private")
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("max-age=3600")
                ))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        assertEquals(getOncesiAuditSayisi, auditLogRepository.count());
        assertEquals(anahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals("def_pfp.png", personelRepository
                .findById(okuyucu.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(1, testDepolamasindakiDosyaSayisi());
    }

    @Test
    void aktifFarkliKullaniciPngProfilFotografiniBinaryVeGuvenliHeaderlarlaGetirebilir()
            throws Exception {
        String sahipToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String anahtar = profilFotografiYukle(
                sahipToken,
                pngDosyasi("get-portre.png", 4, 3)
        );
        byte[] saklananByte = Files.readAllBytes(testDepolamaKoku().resolve(anahtar));
        Personel okuyucu = ikinciAktifPersoneliKaydet();
        String okuyucuToken = jwtService.generateToken(
                okuyucu.getPersonelId(),
                RolAdi.PERSONEL
        );
        long getOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiGetirmeIstegi(okuyucuToken, anahtar))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(saklananByte))
                .andExpect(header().string(
                        "X-Content-Type-Options",
                        "nosniff"
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("private")
                ))
                .andExpect(header().string(
                        HttpHeaders.CACHE_CONTROL,
                        containsString("max-age=3600")
                ))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        assertEquals(getOncesiAuditSayisi, auditLogRepository.count());
        assertEquals(anahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals("def_pfp.png", personelRepository
                .findById(okuyucu.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(1, testDepolamasindakiDosyaSayisi());
    }

    @Test
    void tokenOlmadanProfilFotografiGetirme401Doner() throws Exception {
        mockMvc.perform(get(
                        "/api/profil-fotograflari/{dosyaAnahtari}",
                        "00000000-0000-4000-8000-000000000000.jpg"
                ))
                .andExpect(status().isUnauthorized());

        basarisizGetirmeYanEtkiBirakmadi();
    }

    @Test
    void pasifPrincipalProfilFotografiGetiremez() throws Exception {
        String sahipToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String anahtar = profilFotografiYukle(
                sahipToken,
                jpegDosyasi("pasif-get.jpg")
        );
        Personel okuyucu = ikinciAktifPersoneliKaydet();
        okuyucu.setCalisiyorMu(false);
        okuyucu = personelRepository.saveAndFlush(okuyucu);
        String okuyucuToken = jwtService.generateToken(
                okuyucu.getPersonelId(),
                RolAdi.PERSONEL
        );
        long getOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiGetirmeIstegi(okuyucuToken, anahtar))
                .andExpect(status().isForbidden());

        assertEquals(getOncesiAuditSayisi, auditLogRepository.count());
        assertEquals(anahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals("def_pfp.png", personelRepository
                .findById(okuyucu.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(1, testDepolamasindakiDosyaSayisi());
    }

    @Test
    void principalSatiriYoksaProfilFotografiGetirme404Doner() throws Exception {
        String sahipToken = jwtService.generateToken(
                personel.getPersonelId(),
                RolAdi.PERSONEL
        );
        String anahtar = profilFotografiYukle(
                sahipToken,
                jpegDosyasi("kayip-principal-get.jpg")
        );
        String kayipPrincipalToken = jwtService.generateToken(
                "veritabaninda-olmayan-id",
                RolAdi.PERSONEL
        );
        long getOncesiAuditSayisi = auditLogRepository.count();

        mockMvc.perform(profilFotografiGetirmeIstegi(kayipPrincipalToken, anahtar))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));

        assertEquals(getOncesiAuditSayisi, auditLogRepository.count());
        assertEquals(anahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(1, testDepolamasindakiDosyaSayisi());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "gecersiz.jpg",
            "def_pfp.png",
            "00000000-0000-4000-8000-000000000000.jpg"
    })
    void gecersizVeyaEksikProfilFotografiAnahtari404Doner(String anahtar)
            throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(profilFotografiGetirmeIstegi(token, anahtar))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Profil fotoğrafı bulunamadı."));

        basarisizGetirmeYanEtkiBirakmadi();
    }

    @Test
    void encodedTraversalIstegiFirewallTarafindan400Reddedilir() throws Exception {
        String token = jwtService.generateToken(personel.getPersonelId(), RolAdi.PERSONEL);

        mockMvc.perform(profilFotografiGetirmeIstegi(token, "..%2Fhedef.jpg"))
                .andExpect(status().isBadRequest());

        basarisizGetirmeYanEtkiBirakmadi();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RollbackTestConfiguration {

        @Bean
        ProfilFotografiRollbackTestService profilFotografiRollbackTestService(
                ProfilFotografiService profilFotografiService
        ) {
            return new ProfilFotografiRollbackTestService(profilFotografiService);
        }

        @Bean
        ProfilFotografiRequiresNewUploadTestService requiresNewUploadTestService(
                ProfilFotografiService profilFotografiService
        ) {
            return new ProfilFotografiRequiresNewUploadTestService(
                    profilFotografiService
            );
        }

        @Bean
        ProfilFotografiStaleWriteTestService profilFotografiStaleWriteTestService(
                PersonelRepository personelRepository,
                ProfilFotografiRequiresNewUploadTestService requiresNewUploadTestService,
                ProfilService profilService
        ) {
            return new ProfilFotografiStaleWriteTestService(
                    personelRepository,
                    requiresNewUploadTestService,
                    profilService
            );
        }
    }

    static class ProfilFotografiRequiresNewUploadTestService {

        private final ProfilFotografiService profilFotografiService;

        ProfilFotografiRequiresNewUploadTestService(
                ProfilFotografiService profilFotografiService
        ) {
            this.profilFotografiService = profilFotografiService;
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void yukle(String personelId, MockMultipartFile dosya) {
            profilFotografiService.profilFotografimiGuncelle(personelId, dosya);
        }
    }

    static class ProfilFotografiStaleWriteTestService {

        private final PersonelRepository personelRepository;
        private final ProfilFotografiRequiresNewUploadTestService requiresNewUploadTestService;
        private final ProfilService profilService;

        ProfilFotografiStaleWriteTestService(
                PersonelRepository personelRepository,
                ProfilFotografiRequiresNewUploadTestService requiresNewUploadTestService,
                ProfilService profilService
        ) {
            this.personelRepository = personelRepository;
            this.requiresNewUploadTestService = requiresNewUploadTestService;
            this.profilService = profilService;
        }

        @Transactional
        public void staleProfilGuncellemesiniTamamla(
                String personelId,
                MockMultipartFile yeniFotograf
        ) {
            Personel stalePersonel = personelRepository
                    .findById(personelId)
                    .orElseThrow();
            stalePersonel.getProfilFoto();

            requiresNewUploadTestService.yukle(personelId, yeniFotograf);

            ProfilUpdateRequest request = new ProfilUpdateRequest();
            request.setTelNo("05551112233");
            profilService.profilimiGuncelle(personelId, request);
            personelRepository.flush();
        }
    }

    static class ProfilFotografiRollbackTestService {

        private final ProfilFotografiService profilFotografiService;

        ProfilFotografiRollbackTestService(ProfilFotografiService profilFotografiService) {
            this.profilFotografiService = profilFotografiService;
        }

        @Transactional
        public void yukleVeHataFirlat(String personelId, MockMultipartFile dosya) {
            profilFotografiService.profilFotografimiGuncelle(personelId, dosya);
            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }

        @Transactional
        public void silVeHataFirlat(String personelId) {
            try {
                ProfilFotografiService.class
                        .getMethod("profilFotografimiSil", String.class)
                        .invoke(profilFotografiService, personelId);
            } catch (InvocationTargetException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(
                        "Profil fotoğrafı silme işlemi başarısız oldu.",
                        cause
                );
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(
                        "Profil fotoğrafı silme metodu bulunamadı.",
                        ex
                );
            }

            throw new IllegalStateException(
                    "Rollback davranışını doğrulamak için test hatası."
            );
        }
    }

    private Personel yeniPersonel(
            String kullaniciAdi,
            String ePosta,
            Rol rol,
            Departman departman
    ) {
        Personel yeniPersonel = new Personel();
        yeniPersonel.setKullaniciAdi(kullaniciAdi);
        yeniPersonel.setParolaHash("test-hash");
        yeniPersonel.setAdi("Test");
        yeniPersonel.setSoyadi("Personel");
        yeniPersonel.setDogumTarihi(LocalDate.of(1995, 5, 10));
        yeniPersonel.setePosta(ePosta);
        yeniPersonel.setDepartman(departman);
        yeniPersonel.setRol(rol);
        yeniPersonel.setCalisiyorMu(true);
        yeniPersonel.setIsGirisTarihi(LocalDate.of(2022, 1, 10));
        return yeniPersonel;
    }

    private MockMultipartFile jpegDosyasi(String dosyaAdi) throws IOException {
        BufferedImage image = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.BLUE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpeg", output);
            return new MockMultipartFile(
                    "dosya",
                    dosyaAdi,
                    MediaType.IMAGE_JPEG_VALUE,
                    output.toByteArray()
            );
        } finally {
            image.flush();
        }
    }

    private MockMultipartFile pngDosyasi(
            String dosyaAdi,
            int genislik,
            int yukseklik
    ) throws IOException {
        BufferedImage image = new BufferedImage(
                genislik,
                yukseklik,
                BufferedImage.TYPE_INT_ARGB
        );
        image.setRGB(0, 0, Color.BLUE.getRGB());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return new MockMultipartFile(
                    "dosya",
                    dosyaAdi,
                    MediaType.IMAGE_PNG_VALUE,
                    output.toByteArray()
            );
        } finally {
            image.flush();
        }
    }

    private RequestBuilder profilFotografiYuklemeIstegi(
            String token,
            MockMultipartFile dosya
    ) {
        return multipart("/api/profil/fotograf")
                .file(dosya)
                .with(request -> {
                    request.setMethod("PUT");
                    return request;
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.MULTIPART_FORM_DATA);
    }

    private RequestBuilder profilFotografiGetirmeIstegi(
            String token,
            String anahtar
    ) {
        return get("/api/profil-fotograflari/{dosyaAnahtari}", anahtar)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private RequestBuilder profilFotografiSilmeIstegi(String token) {
        return delete("/api/profil/fotograf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String profilFotografiYukle(
            String token,
            MockMultipartFile dosya
    ) throws Exception {
        String responseBody = mockMvc.perform(profilFotografiYuklemeIstegi(token, dosya))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(responseBody).path("profilFoto").asText();
    }

    private Personel ikinciAktifPersoneliKaydet() {
        return personelRepository.saveAndFlush(yeniPersonel(
                "okuyucu.personel",
                "okuyucu.personel@test.local",
                personel.getRol(),
                personel.getDepartman()
        ));
    }

    private void personelinRolunuDegistir(RolAdi rolAdi) {
        int rolId = switch (rolAdi) {
            case ADMIN -> 1;
            case PERSONEL -> 2;
            case YONETICI -> 3;
            case HR -> 4;
        };
        Rol rol = rolRepository.findById(rolId).orElseGet(() -> {
            Rol yeniRol = new Rol();
            yeniRol.setRolId(rolId);
            yeniRol.setRolAdi(rolAdi);
            return rolRepository.save(yeniRol);
        });
        personel.setRol(rol);
        personel = personelRepository.saveAndFlush(personel);
    }

    private long testDepolamasindakiDosyaSayisi() throws IOException {
        Path testKoku = testDepolamaKoku();
        if (!Files.exists(testKoku)) {
            return 0;
        }
        try (var yollar = Files.list(testKoku)) {
            return yollar.filter(Files::isRegularFile).count();
        }
    }

    private Set<String> testDepolamasindakiDosyaAnahtarlari() throws IOException {
        Path testKoku = testDepolamaKoku();
        if (!Files.exists(testKoku)) {
            return Set.of();
        }
        try (var yollar = Files.list(testKoku)) {
            return yollar
                    .filter(Files::isRegularFile)
                    .map(yol -> yol.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private void basarisizYuklemeYanEtkiBirakmadi() throws IOException {
        assertEquals("def_pfp.png", personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(0, auditLogRepository.count());
        assertEquals(0, testDepolamasindakiDosyaSayisi());
    }

    private void basarisizGetirmeYanEtkiBirakmadi() throws IOException {
        assertEquals("def_pfp.png", personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(0, auditLogRepository.count());
        assertEquals(0, testDepolamasindakiDosyaSayisi());
    }

    private void basarisizSilmeYanEtkiBirakmadi(
            String eskiAnahtar,
            Set<String> silmeOncesiDosyalar,
            long silmeOncesiAuditSayisi
    ) throws IOException {
        assertEquals(eskiAnahtar, personelRepository
                .findById(personel.getPersonelId())
                .orElseThrow()
                .getProfilFoto());
        assertEquals(silmeOncesiDosyalar, testDepolamasindakiDosyaAnahtarlari());
        assertTrue(Files.isRegularFile(testDepolamaKoku().resolve(eskiAnahtar)));
        assertEquals(silmeOncesiAuditSayisi, auditLogRepository.count());
    }

    private Path testDepolamaKoku() {
        Path geciciKok = Path.of(System.getProperty("java.io.tmpdir"))
                .toAbsolutePath()
                .normalize();
        Path testKoku = geciciKok.resolve(BEKLENEN_TEST_DIZINLERI)
                .toAbsolutePath()
                .normalize();

        if (!testKoku.startsWith(geciciKok)
                || testKoku.equals(geciciKok)
                || !testKoku.endsWith(BEKLENEN_TEST_DIZINLERI)) {
            throw new IllegalStateException("Güvensiz test depolama yolu: " + testKoku);
        }
        return testKoku;
    }

    private void testDepolamasiniTemizle() throws IOException {
        Path testKoku = testDepolamaKoku();
        if (!Files.exists(testKoku)) {
            return;
        }

        try (var yollar = Files.walk(testKoku)) {
            for (Path yol : yollar.sorted(Comparator.reverseOrder()).toList()) {
                if (!yol.equals(testKoku)) {
                    Files.deleteIfExists(yol);
                }
            }
        }
    }
}
