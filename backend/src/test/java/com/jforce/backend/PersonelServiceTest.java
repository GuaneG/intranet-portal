package com.jforce.backend;

import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.PersonelService;
import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonelServiceTest {

    @Mock
    private PersonelRepository personelRepository;

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

    private PersonelService personelService;

    @BeforeEach
    void setUp() {
        personelService = new PersonelService(
                personelRepository, rolRepository,
                refreshTokenService, auditService,
                departmanRepository, parolaService
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
        when(personelRepository.findById("hedef-id"))
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
        when(personelRepository.findById("hedef-id"))
                .thenReturn(Optional.of(hedef));

        personelService.rolDegistir("admin-id", "hedef-id", RolAdi.PERSONEL);

        assertSame(personelRolu, hedef.getRol());

        verifyNoInteractions(rolRepository,refreshTokenService, auditService);

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

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-id","admin-id",RolAdi.PERSONEL));

        verifyNoInteractions(rolRepository,refreshTokenService, auditService);
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
        when(personelRepository.findById("admin-id2")).thenReturn(Optional.of(admin2));

        assertThrows(AccessDeniedException.class, () -> personelService.rolDegistir("admin-id1","admin-id2",RolAdi.PERSONEL));

        verifyNoInteractions(rolRepository,refreshTokenService, auditService);
    }
}