package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAuditLogControllerIntegrationTest extends AbstractIntegrationTest {

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

    private Personel admin;
    private Personel aktor;
    private Personel hedef;
    private Rol adminRolu;
    private Rol personelRolu;
    private Rol hrRolu;
    private Rol yoneticiRolu;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        izinBilgileriRepository.deleteAll();
        personelYetenekRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();

        adminRolu = rolKaydet(1, RolAdi.ADMIN);
        personelRolu = rolKaydet(2, RolAdi.PERSONEL);
        hrRolu = rolKaydet(3, RolAdi.HR);
        yoneticiRolu = rolKaydet(4, RolAdi.YONETICI);
        Departman departman = departmanKaydet("Bilgi Teknolojileri");

        admin = personelKaydet("audit.admin", "Audit", "Admin", adminRolu, departman, true);
        aktor = personelKaydet("audit.aktor", "Ayşe", "Yılmaz", personelRolu, departman, true);
        hedef = personelKaydet("audit.hedef", "Mehmet", "Kaya", personelRolu, departman, true);
    }

    @Test
    void aktifAdminAuditKayitlariniSayfaliEnYeniOnceVeGuvenliAlanlarlaGorur() throws Exception {
        auditKaydet(
                AuditEylem.LOGIN_BASARILI,
                aktor,
                null,
                "Login başarılı.",
                Instant.parse("2026-08-25T08:00:00Z")
        );
        AuditLog ikinci = auditKaydet(
                AuditEylem.PROFIL_GUNCELLENDI,
                aktor,
                aktor,
                "Güncellenen alanlar: telNo",
                Instant.parse("2026-08-25T09:00:00Z")
        );
        AuditLog enYeni = auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                admin,
                hedef,
                "İzin talebi onaylandı.",
                Instant.parse("2026-08-25T10:00:00Z")
        );
        long auditSayisi = auditLogRepository.count();

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(2))
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(2))
                .andExpect(jsonPath("$.toplamKayit").value(3))
                .andExpect(jsonPath("$.toplamSayfa").value(2))
                .andExpect(jsonPath("$.icerik[0].auditId").value(enYeni.getLogId()))
                .andExpect(jsonPath("$.icerik[0].eylemTipi").value("IZIN_TALEBI_ONAYLANDI"))
                .andExpect(jsonPath("$.icerik[0].olusmaZamani").value("2026-08-25T10:00:00Z"))
                .andExpect(jsonPath("$.icerik[0].aktor.personelId").value(admin.getPersonelId()))
                .andExpect(jsonPath("$.icerik[0].aktor.adSoyad").value("Audit Admin"))
                .andExpect(jsonPath("$.icerik[0].aktor.kullaniciAdi").value("audit.admin"))
                .andExpect(jsonPath("$.icerik[0].hedef.personelId").value(hedef.getPersonelId()))
                .andExpect(jsonPath("$.icerik[0].hedef.adSoyad").value("Mehmet Kaya"))
                .andExpect(jsonPath("$.icerik[0].detay").value("İzin talebi onaylandı."))
                .andExpect(jsonPath("$.icerik[0].parola").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].token").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].ePosta").doesNotExist())
                .andExpect(jsonPath("$.icerik[0].telNo").doesNotExist())
                .andExpect(jsonPath("$.icerik[1].auditId").value(ikinci.getLogId()));

        assertEquals(auditSayisi, auditLogRepository.count());
    }

    @Test
    void tarihEylemAktorVeHedefFiltreleriAndSemantigiyleCalisir() throws Exception {
        AuditLog eslesen = auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                admin,
                hedef,
                "Eşleşen kayıt.",
                Instant.parse("2026-08-25T12:00:00Z")
        );
        auditKaydet(
                AuditEylem.IZIN_TALEBI_REDDEDILDI,
                admin,
                hedef,
                "Yanlış eylem.",
                Instant.parse("2026-08-25T12:00:00Z")
        );
        auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                aktor,
                hedef,
                "Yanlış aktör.",
                Instant.parse("2026-08-25T12:00:00Z")
        );
        auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                admin,
                aktor,
                "Yanlış hedef.",
                Instant.parse("2026-08-25T12:00:00Z")
        );
        auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                admin,
                hedef,
                "Başlangıçtan önce.",
                Instant.parse("2026-08-24T20:59:59Z")
        );
        auditKaydet(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                admin,
                hedef,
                "Bitişten sonra.",
                Instant.parse("2026-08-25T21:00:00Z")
        );

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("baslangicTarihi", "2026-08-25")
                        .param("bitisTarihi", "2026-08-25")
                        .param("eylemTipi", "IZIN_TALEBI_ONAYLANDI")
                        .param("aktorPersonelId", admin.getPersonelId())
                        .param("hedefPersonelId", hedef.getPersonelId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(1))
                .andExpect(jsonPath("$.toplamKayit").value(1))
                .andExpect(jsonPath("$.icerik[0].auditId").value(eslesen.getLogId()));
    }

    @Test
    void tersTarihAraligi400Doner() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("baslangicTarihi", "2026-08-26")
                        .param("bitisTarihi", "2026-08-25")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Başlangıç tarihi bitiş tarihinden sonra olamaz."));
    }

    @Test
    void uuidOlmayanAktorVeyaHedefFiltresi400Doner() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("aktorPersonelId", "gecersiz-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Aktör personel ID geçerli UUID olmalıdır."));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("hedefPersonelId", "gecersiz-id")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Hedef personel ID geçerli UUID olmalıdır."));
    }

    @Test
    void endpointYalnizJwtVeDbRoluAdminOlanAktifKullaniciyaAciktir() throws Exception {
        Personel hr = personelKaydet(
                "audit.hr", "Audit", "HR", hrRolu, admin.getDepartman(), true
        );
        Personel yonetici = personelKaydet(
                "audit.yonetici", "Audit", "Yönetici", yoneticiRolu,
                admin.getDepartman(), true
        );

        for (PersonelRolCifti cift : List.of(
                new PersonelRolCifti(aktor, RolAdi.PERSONEL),
                new PersonelRolCifti(hr, RolAdi.HR),
                new PersonelRolCifti(yonetici, RolAdi.YONETICI)
        )) {
            mockMvc.perform(get("/api/admin/audit-logs")
                            .header(HttpHeaders.AUTHORIZATION,
                                    "Bearer " + token(cift.personel(), cift.rolAdi())))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());

        Personel pasifAdmin = personelKaydet(
                "audit.pasif.admin", "Pasif", "Admin", adminRolu,
                admin.getDepartman(), false
        );
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("baslangicTarihi", "2026-08-26")
                        .param("bitisTarihi", "2026-08-25")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(pasifAdmin, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + token(aktor, RolAdi.ADMIN)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION,
                                "Bearer " + jwtService.generateToken(
                                        UUID.randomUUID().toString(), RolAdi.ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Personel bulunamadı."));
    }

    @Test
    void standartQueryDonusumVeSayfalamaHatalari400Doner() throws Exception {
        String adminToken = token(admin, RolAdi.ADMIN);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("baslangicTarihi", "26-08-2026")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz istek"));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("eylemTipi", "BILINMEYEN_EYLEM")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz istek"));

        for (String parametre : List.of("page=-1", "size=0", "size=101")) {
            String[] parcalar = parametre.split("=");
            mockMvc.perform(get("/api/admin/audit-logs")
                            .param(parcalar[0], parcalar[1])
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Geçersiz istek"));
        }
    }

    @Test
    void bilinmeyenPozitifUuidFiltreleriBosSayfaDoner() throws Exception {
        String bilinmeyenId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("aktorPersonelId", bilinmeyenId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(0))
                .andExpect(jsonPath("$.toplamKayit").value(0));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("hedefPersonelId", bilinmeyenId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik.length()").value(0))
                .andExpect(jsonPath("$.toplamKayit").value(0));
    }

    @Test
    void silinmisVeyaBulunamayanAktorluKayitKullaniciAdiSnapshotiniKorur() throws Exception {
        AuditLog kayit = auditKaydet(
                AuditEylem.LOGIN_BASARISIZ,
                null,
                null,
                "Başarısız giriş.",
                Instant.parse("2026-08-25T08:00:00Z")
        );

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icerik[0].auditId").value(kayit.getLogId()))
                .andExpect(jsonPath("$.icerik[0].aktor.personelId").value(nullValue()))
                .andExpect(jsonPath("$.icerik[0].aktor.adSoyad").value(nullValue()))
                .andExpect(jsonPath("$.icerik[0].aktor.kullaniciAdi").value("bilinmeyen"))
                .andExpect(jsonPath("$.icerik[0].hedef").value(nullValue()));
    }

    @Test
    void ayniZamandakiKayitlarAuditIdAzalanSiraylaDonerVeVarsayilanSayfalamaKullanilir()
            throws Exception {
        Instant ayniZaman = Instant.parse("2026-08-25T08:00:00Z");
        AuditLog ilk = auditKaydet(
                AuditEylem.LOGIN_BASARILI, aktor, null, "İlk.", ayniZaman
        );
        AuditLog ikinci = auditKaydet(
                AuditEylem.LOGOUT, aktor, null, "İkinci.", ayniZaman
        );
        String onceDonmesiGereken = ilk.getLogId().compareTo(ikinci.getLogId()) > 0
                ? ilk.getLogId()
                : ikinci.getLogId();

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(admin, RolAdi.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sayfa").value(0))
                .andExpect(jsonPath("$.sayfaBoyutu").value(20))
                .andExpect(jsonPath("$.icerik[0].auditId").value(onceDonmesiGereken));
    }

    private AuditLog auditKaydet(
            AuditEylem eylem,
            Personel kayitAktoru,
            Personel kayitHedefi,
            String detay,
            Instant olusmaZamani
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEylemTipi(eylem);
        auditLog.setPersonel(kayitAktoru);
        auditLog.setKullaniciAdi(kayitAktoru == null ? "bilinmeyen" : kayitAktoru.getKullaniciAdi());
        auditLog.setHedefPersonel(kayitHedefi);
        auditLog.setDetay(detay);
        auditLog.setOlusmaZamani(olusmaZamani);
        return auditLogRepository.saveAndFlush(auditLog);
    }

    private Rol rolKaydet(int rolId, RolAdi rolAdi) {
        Rol rol = new Rol();
        rol.setRolId(rolId);
        rol.setRolAdi(rolAdi);
        return rolRepository.save(rol);
    }

    private Departman departmanKaydet(String ad) {
        Departman departman = new Departman();
        departman.setDepartmanAdi(ad);
        return departmanRepository.save(departman);
    }

    private Personel personelKaydet(
            String kullaniciAdi,
            String ad,
            String soyad,
            Rol rol,
            Departman departman,
            boolean aktif
    ) {
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

    private record PersonelRolCifti(Personel personel, RolAdi rolAdi) {
    }
}
