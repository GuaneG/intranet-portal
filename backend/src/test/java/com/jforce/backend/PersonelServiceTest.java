package com.jforce.backend;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.PersonelService;
import com.jforce.backend.service.RefreshTokenService;
import com.jforce.backend.service.RezervasyonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import com.jforce.backend.service.PersonelYonetimYetkiService;

import java.time.LocalDate;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertNull;


@ExtendWith(MockitoExtension.class)
class PersonelServiceTest {

    @Mock
    private PersonelRepository personelRepository;


    private PersonelYonetimYetkiService personelYonetimYetkiService;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @Mock
    private DepartmanRepository departmanRepository;

    @Mock
    private ParolaService parolaService;

    @Mock
    private RezervasyonService rezervasyonService;

    @Mock
    private EkipmanTalepRepository ekipmanTalepRepository;

    @Mock
    private DurumTuruRepository durumTuruRepository;

    private PersonelService personelService;

    @BeforeEach
    void setUp() {
        personelYonetimYetkiService = spy(
                new PersonelYonetimYetkiService(
                        personelRepository
                )
        );

        doAnswer(invocation -> {
            String aktorId = invocation.getArgument(0);

            return personelRepository
                    .findById(aktorId)
                    .orElseThrow();
        })
                .when(personelYonetimYetkiService)
                .aktifAktoruGetir(anyString());

        personelService = new PersonelService(
                personelRepository,
                rolRepository,
                refreshTokenService,
                auditService,
                departmanRepository,
                parolaService,
                personelYonetimYetkiService,
                rezervasyonService,
                ekipmanTalepRepository,
                durumTuruRepository,
                Clock.systemUTC()
        );
    }
    private Personel personelHazirla(String id, String kullaniciAdi, RolAdi rolAdi, boolean aktif) {
        Rol rol = new Rol();
        rol.setRolAdi(rolAdi);

        Personel personel = new Personel();
        personel.setPersonelId(id);
        personel.setKullaniciAdi(kullaniciAdi);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);

        return personel;
    }

    @Test
    void rolDegistirAdminRoluneGecisiReddeder() {
        Personel aktor = personelHazirla(
                "admin-id", "admin", RolAdi.ADMIN, true
        );
        Personel hedef = personelHazirla(
                "hedef-id", "hedef", RolAdi.PERSONEL, true
        );

        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(aktor));
        when(personelRepository.findByIdForUpdate("hedef-id"))
                .thenReturn(Optional.of(hedef));

        assertThrows(
                GecersizIstekException.class,
                () -> personelService.rolDegistir(
                        "admin-id", "hedef-id", RolAdi.ADMIN
                )
        );

        assertSame(RolAdi.PERSONEL, hedef.getRol().getRolAdi());
        verifyNoInteractions(
                rolRepository,
                refreshTokenService,
                auditService,
                ekipmanTalepRepository,
                durumTuruRepository
        );
    }

    @Test
    void rolDegistirHedefinRolunuDegistirirTokenlariniIptalEderVeAuditKaydeder() {
        //rolleri hazirla
        Rol adminRolu = new Rol();
        adminRolu.setRolAdi(RolAdi.ADMIN);
        Rol personelRolu = new Rol();
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        Rol hrRolu = new Rol();
        hrRolu.setRolAdi(RolAdi.HR);
        //admin olustur
        Personel aktor = new Personel();
        aktor.setPersonelId("admin-id");
        aktor.setKullaniciAdi("admin");
        aktor.setRol(adminRolu);
        //hedef personel
        Personel hedef = new Personel();
        hedef.setPersonelId("hedef-id");
        hedef.setKullaniciAdi("hedef.personel");
        hedef.setRol(personelRolu);
        //roldegistir metodunun dönceği şeyleri önceden hazırlar
        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(aktor));
        when(personelRepository.findByIdForUpdate("hedef-id"))
                .thenReturn(Optional.of(hedef));
        when(rolRepository.findByRolAdi(RolAdi.HR))
                .thenReturn(Optional.of(hrRolu));

        //act
        personelService.rolDegistir("admin-id", "hedef-id", RolAdi.HR);
        //assert
        assertSame(hrRolu, hedef.getRol());

        //assert sonrası çalışması gerekenler çalıştımı?
        verify(refreshTokenService).revokeAllForPersonel(hedef);
        verify(auditService).logKaydetOrtakTransaction(
                eq(AuditEylem.ROL_DEGISTIRILDI),
                same(aktor),
                eq("admin"),
                contains("PERSONEL -> HR"),
                same(hedef)
        );
        verifyNoInteractions(ekipmanTalepRepository, durumTuruRepository);
    }

    @Test
    void rolDegistirHedefEskiRolveYeniRolAyni() {
        //rolleri hazirla
        Rol adminRolu = new Rol();
        adminRolu.setRolAdi(RolAdi.ADMIN);
        Rol personelRolu = new Rol();
        personelRolu.setRolAdi(RolAdi.PERSONEL);

        //admin olustur
        Personel aktor = new Personel();
        aktor.setPersonelId("admin-id");
        aktor.setKullaniciAdi("admin");
        aktor.setRol(adminRolu);
        //hedef personel
        Personel hedef = new Personel();
        hedef.setPersonelId("hedef-id");
        hedef.setKullaniciAdi("hedef.personel");
        hedef.setRol(personelRolu);
        //roldegistir metodunun dönceği şeyleri önceden hazırlar
        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(aktor));
        when(personelRepository.findByIdForUpdate("hedef-id"))
                .thenReturn(Optional.of(hedef));

        personelService.rolDegistir("admin-id", "hedef-id", RolAdi.PERSONEL);

        assertSame(personelRolu, hedef.getRol());

        verifyNoInteractions(
                rolRepository, refreshTokenService, auditService,
                ekipmanTalepRepository, durumTuruRepository
        );

    }

    @Test
    void rolDegistirAdminKendisineAyniRolGonderseBileReddedilir() {
        Personel admin = personelHazirla("admin-id", "admin", RolAdi.ADMIN, true);

        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(admin));
        when(personelRepository.findByIdForUpdate("admin-id"))
                .thenReturn(Optional.of(admin));

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-id", "admin-id", RolAdi.ADMIN));

        verifyNoInteractions(
                rolRepository, refreshTokenService, auditService,
                ekipmanTalepRepository, durumTuruRepository
        );
    }

    @Test
    void rolDegistirBaskaAdminHedefeAyniRolGonderseBileReddedilir() {
        Personel aktor = personelHazirla("admin-aktor-id", "admin.aktor", RolAdi.ADMIN, true);
        Personel hedef = personelHazirla("admin-hedef-id", "admin.hedef", RolAdi.ADMIN, true);

        when(personelRepository.findById("admin-aktor-id"))
                .thenReturn(Optional.of(aktor));
        when(personelRepository.findByIdForUpdate("admin-hedef-id"))
                .thenReturn(Optional.of(hedef));

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-aktor-id", "admin-hedef-id", RolAdi.ADMIN));

        verifyNoInteractions(
                rolRepository, refreshTokenService, auditService,
                ekipmanTalepRepository, durumTuruRepository
        );
    }

    @Test
    void rolDegistirAdminKendiRolunuDegistiremez(){
        Rol adminRolu = new Rol();
        adminRolu.setRolAdi(RolAdi.ADMIN);

        Personel admin = new Personel();
        admin.setPersonelId("admin-id");
        admin.setKullaniciAdi("admin");
        admin.setRol(adminRolu);

        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(admin));
        when(personelRepository.findByIdForUpdate("admin-id"))
                .thenReturn(Optional.of(admin));

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-id","admin-id",RolAdi.PERSONEL));

        verifyNoInteractions(
                rolRepository, refreshTokenService, auditService,
                ekipmanTalepRepository, durumTuruRepository
        );
    }

    @Test
    void rolDegistirHedefRolAdminİseDeğiştirmez(){
        Rol adminRolu = new Rol();
        adminRolu.setRolAdi(RolAdi.ADMIN);

        Personel admin1 = new Personel();
        admin1.setPersonelId("admin-id1");
        admin1.setKullaniciAdi("admin");
        admin1.setRol(adminRolu);

        Personel admin2 = new Personel();
        admin2.setPersonelId("admin-id2");
        admin2.setKullaniciAdi("admin");
        admin2.setRol(adminRolu);

        when(personelRepository.findById("admin-id1")).thenReturn(Optional.of(admin1));
        when(personelRepository.findByIdForUpdate("admin-id2")).thenReturn(Optional.of(admin2));

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-id1","admin-id2",RolAdi.PERSONEL));

        verifyNoInteractions(
                rolRepository, refreshTokenService, auditService,
                ekipmanTalepRepository, durumTuruRepository
        );
    }
    @Test
    void hrNormalPersoneliPasiflestirirTokenlariIptalEderVeAuditKaydeder() {
        Personel hr = personelHazirla("hr-id", "hr", RolAdi.HR, true);

        Personel hedef = personelHazirla("hedef-id", "hedef", RolAdi.PERSONEL, true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(personelRepository.findByIdForUpdate("hedef-id"))
                .thenReturn(Optional.of(hedef));

        personelService.personelPasiflestir("hr-id", "hedef-id");

        assertFalse(hedef.getCalisiyorMu());
        assertEquals(LocalDate.now(), hedef.getIsCikisTarihi());

        verify(refreshTokenService).revokeAllForPersonel(hedef);

        verify(auditService).logKaydetOrtakTransaction(eq(AuditEylem.PERSONEL_PASIFE_ALINDI), same(hr), eq("hr"), eq("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 0. İptal edilen rezervasyon sayısı: 0"), same(hedef));
    }

    @Test
    void hrKendisiniPasiflestiremez() {
        Personel hr = personelHazirla("hr-id", "hr", RolAdi.HR, true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));
        when(personelRepository.findByIdForUpdate("hr-id"))
                .thenReturn(Optional.of(hr));

        assertThrows(AccessDeniedException.class, () -> personelService.personelPasiflestir("hr-id", "hr-id"));
        assertTrue(hr.getCalisiyorMu());

        verifyNoInteractions(refreshTokenService, auditService);
    }

    @Test
    void adminRolundekiHedefPasiflestirilemez() {

        Personel aktor = personelHazirla("admin-1", "admin1", RolAdi.ADMIN, true);
        Personel hedef = personelHazirla("admin-2", "admin2", RolAdi.ADMIN, true);

        when(personelRepository.findById("admin-1"))
                .thenReturn(Optional.of(aktor));
        when(personelRepository.findByIdForUpdate("admin-2"))
                .thenReturn(Optional.of(hedef));

        assertThrows(AccessDeniedException.class, () -> personelService.personelPasiflestir("admin-1", "admin-2"));
        assertTrue(hedef.getCalisiyorMu());

        verifyNoInteractions(refreshTokenService, auditService);
    }

    @Test
    void hrYoneticiyiPasiflestiremez() {
        Personel hr = personelHazirla("hr-id", "hr", RolAdi.HR, true);
        Personel yonetici = personelHazirla("yonetici-id", "yonetici", RolAdi.YONETICI, true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));
        when(personelRepository.findByIdForUpdate("yonetici-id"))
                .thenReturn(Optional.of(yonetici));

        assertThrows(AccessDeniedException.class, () -> personelService.personelPasiflestir("hr-id", "yonetici-id"));
        assertTrue(yonetici.getCalisiyorMu());

        verifyNoInteractions(refreshTokenService, auditService);
    }

    @Test
    void zatenPasifHedefCikisTarihiniKorurVeYanEtkiOlusturmaz() {
        Personel hr = personelHazirla("hr-id", "hr", RolAdi.HR, true);
        Personel hedef = personelHazirla("hedef-id", "hedef", RolAdi.PERSONEL, false);
        LocalDate mevcutCikisTarihi = LocalDate.of(2025, 6, 1);

        hedef.setIsCikisTarihi(mevcutCikisTarihi);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));
        when(personelRepository.findByIdForUpdate("hedef-id"))
                .thenReturn(Optional.of(hedef));

        personelService.personelPasiflestir("hr-id", "hedef-id");

        assertFalse(hedef.getCalisiyorMu());
        assertEquals(mevcutCikisTarihi, hedef.getIsCikisTarihi());

        verifyNoInteractions(refreshTokenService, auditService);
    }

    @Test
    void adminYoneticiyiPasiflestirinceDogrudanCalisanlarinYoneticisiniKaldirir() {
        Personel admin = personelHazirla("admin-id", "admin", RolAdi.ADMIN, true);
        Personel yonetici = personelHazirla("yonetici-id", "yonetici", RolAdi.YONETICI, true);
        Personel aktifCalisan = personelHazirla("aktif-id", "aktif", RolAdi.PERSONEL, true);
        Personel pasifCalisan = personelHazirla("pasif-id", "pasif", RolAdi.PERSONEL, false);

        aktifCalisan.setYonetici(yonetici);
        pasifCalisan.setYonetici(yonetici);

        when(personelRepository.findById("admin-id"))
                .thenReturn(Optional.of(admin));

        when(personelRepository.findByIdForUpdate("yonetici-id"))
                .thenReturn(Optional.of(yonetici));

        when(personelRepository.findAllByYonetici(yonetici))
                .thenReturn(List.of(aktifCalisan, pasifCalisan));

        personelService.personelPasiflestir(
                "admin-id",
                "yonetici-id"
        );

        assertNull(aktifCalisan.getYonetici());
        assertNull(pasifCalisan.getYonetici());
        assertFalse(yonetici.getCalisiyorMu());

        verify(refreshTokenService)
                .revokeAllForPersonel(yonetici);

        verify(auditService).logKaydetOrtakTransaction(
                eq(AuditEylem.PERSONEL_PASIFE_ALINDI),
                same(admin),
                eq("admin"),
                eq("Personel pasife alındı. Yöneticisi kaldırılan personel sayısı: 2. İptal edilen rezervasyon sayısı: 0"),
                same(yonetici)
        );
    }

    @Test
    void hrZatenPasifYoneticiyiPasiflestiremez() {
        Personel hr = personelHazirla(
                "hr-id",
                "hr",
                RolAdi.HR,
                true
        );

        Personel yonetici = personelHazirla(
                "yonetici-id",
                "yonetici",
                RolAdi.YONETICI,
                false
        );

        yonetici.setIsCikisTarihi(
                LocalDate.of(2025, 6, 1)
        );

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(personelRepository.findByIdForUpdate("yonetici-id"))
                .thenReturn(Optional.of(yonetici));

        assertThrows(
                AccessDeniedException.class,
                () -> personelService.personelPasiflestir(
                        "hr-id",
                        "yonetici-id"
                )
        );

        verifyNoInteractions(
                refreshTokenService,
                auditService
        );
    }


}
