package com.jforce.backend;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.PersonelYonetimYetkiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersonelYonetimYetkiServiceTest {

    @Mock
    private PersonelRepository personelRepository;

    private PersonelYonetimYetkiService yetkiService;

    @BeforeEach
    void setUp() {
        yetkiService =
                new PersonelYonetimYetkiService(
                        personelRepository
                );
    }

    @Test
    void aktifHrAktoruRepositorydenGetirilir() {
        Personel hr = personelHazirla(
                "hr-id",
                RolAdi.HR,
                true
        );

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        Personel sonuc =
                yetkiService.aktifAktoruGetir("hr-id");

        assertSame(hr, sonuc);
    }

    @Test
    void aktorBulunamazsaKontrollu404ExceptionOlusur() {
        when(personelRepository.findById("olmayan-id"))
                .thenReturn(Optional.empty());

        KaynakBulunamadiException exception =
                assertThrows(
                        KaynakBulunamadiException.class,
                        () -> yetkiService.aktifAktoruGetir(
                                "olmayan-id"
                        )
                );

        assertEquals(
                "Personel bulunamadı.",
                exception.getMessage()
        );
    }

    @Test
    void pasifAktorYonetimIslemiYapamaz() {
        Personel pasifHr = personelHazirla(
                "pasif-hr-id",
                RolAdi.HR,
                false
        );

        when(personelRepository.findById("pasif-hr-id"))
                .thenReturn(Optional.of(pasifHr));

        assertThrows(
                AccessDeniedException.class,
                () -> yetkiService.aktifAktoruGetir(
                        "pasif-hr-id"
                )
        );
    }

    @Test
    void hrVeAdminDisindakiAktorYonetimIslemiYapamaz() {
        Personel personel = personelHazirla(
                "personel-id",
                RolAdi.PERSONEL,
                true
        );

        when(personelRepository.findById("personel-id"))
                .thenReturn(Optional.of(personel));

        assertThrows(
                AccessDeniedException.class,
                () -> yetkiService.aktifAktoruGetir(
                        "personel-id"
                )
        );
    }

    @Test
    void hrYalnizPersonelVeHrHedefleriniYonetebilir() {
        Personel hr = personelHazirla(
                "hr-id",
                RolAdi.HR,
                true
        );

        Set<RolAdi> roller =
                yetkiService.izinliHedefRolleri(hr);

        assertEquals(
                Set.of(
                        RolAdi.PERSONEL,
                        RolAdi.HR
                ),
                roller
        );
    }

    @Test
    void adminPersonelHrVeYoneticiHedefleriniYonetebilir() {
        Personel admin = personelHazirla(
                "admin-id",
                RolAdi.ADMIN,
                true
        );

        Set<RolAdi> roller =
                yetkiService.izinliHedefRolleri(admin);

        assertEquals(
                Set.of(
                        RolAdi.PERSONEL,
                        RolAdi.HR,
                        RolAdi.YONETICI
                ),
                roller
        );
    }

    @Test
    void hrKendiKaydiniYonetemez() {
        Personel hr = personelHazirla(
                "hr-id",
                RolAdi.HR,
                true
        );

        assertThrows(
                AccessDeniedException.class,
                () -> yetkiService.hedefiDogrula(hr, hr)
        );
    }

    @Test
    void hrYoneticiHedefiYonetemez() {
        Personel hr = personelHazirla(
                "hr-id",
                RolAdi.HR,
                true
        );
        Personel yonetici = personelHazirla(
                "yonetici-id",
                RolAdi.YONETICI,
                true
        );

        assertThrows(
                AccessDeniedException.class,
                () -> yetkiService.hedefiDogrula(
                        hr,
                        yonetici
                )
        );
    }

    @Test
    void adminBaskaAdminHedefiYonetemez() {
        Personel aktor = personelHazirla(
                "admin-aktor-id",
                RolAdi.ADMIN,
                true
        );
        Personel hedef = personelHazirla(
                "admin-hedef-id",
                RolAdi.ADMIN,
                true
        );

        assertThrows(
                AccessDeniedException.class,
                () -> yetkiService.hedefiDogrula(
                        aktor,
                        hedef
                )
        );
    }

    @Test
    void hrBaskaHrHedefiYonetebilir() {
        Personel aktor = personelHazirla(
                "hr-aktor-id",
                RolAdi.HR,
                true
        );
        Personel hedef = personelHazirla(
                "hr-hedef-id",
                RolAdi.HR,
                true
        );

        assertDoesNotThrow(
                () -> yetkiService.hedefiDogrula(
                        aktor,
                        hedef
                )
        );
    }

    @Test
    void adminYoneticiHedefiYonetebilir() {
        Personel admin = personelHazirla(
                "admin-id",
                RolAdi.ADMIN,
                true
        );
        Personel yonetici = personelHazirla(
                "yonetici-id",
                RolAdi.YONETICI,
                true
        );

        assertDoesNotThrow(
                () -> yetkiService.hedefiDogrula(
                        admin,
                        yonetici
                )
        );
    }

    private Personel personelHazirla(
            String personelId,
            RolAdi rolAdi,
            boolean aktif
    ) {
        Rol rol = new Rol();
        rol.setRolAdi(rolAdi);

        Personel personel = new Personel();
        personel.setPersonelId(personelId);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);

        return personel;
    }
}