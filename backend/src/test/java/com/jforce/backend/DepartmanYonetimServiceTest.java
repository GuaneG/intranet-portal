package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.DepartmanAdiRequest;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.DepartmanYonetimService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartmanYonetimServiceTest {

    @Mock
    private DepartmanRepository departmanRepository;

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private AuditService auditService;

    private DepartmanYonetimService service;
    private Personel aktifHr;

    @BeforeEach
    void setUp() {
        service = new DepartmanYonetimService(
                departmanRepository,
                personelRepository,
                auditService
        );

        aktifHr = new Personel();
        aktifHr.setPersonelId("hr-id");
        aktifHr.setKullaniciAdi("hr.aktor");
        aktifHr.setCalisiyorMu(true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(aktifHr));
    }

    @Test
    void olusturmadaUniqueConstraintYarisi409aCevrilir() {
        when(departmanRepository
                .existsByDepartmanAdiIgnoreCase(
                        "Finans"
                ))
                .thenReturn(false);

        when(departmanRepository.saveAndFlush(
                any(Departman.class)
        )).thenThrow(
                new DataIntegrityViolationException(
                        "Duplicate entry"
                )
        );

        KaynakCakismasiException exception =
                assertThrows(
                        KaynakCakismasiException.class,
                        () -> service.departmanOlustur(
                                "hr-id",
                                new DepartmanAdiRequest(
                                        "Finans"
                                )
                        )
                );

        assertEquals(
                "Departman zaten mevcut.",
                exception.getMessage()
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void guncellemedeUniqueConstraintYarisi409aCevrilir() {
        Departman mevcutDepartman = new Departman();
        mevcutDepartman.setDepartmanId(7);
        mevcutDepartman.setDepartmanAdi("Finans");

        when(departmanRepository.findById(7))
                .thenReturn(
                        Optional.of(mevcutDepartman)
                );

        when(departmanRepository
                .existsByDepartmanAdiIgnoreCaseAndDepartmanIdNot(
                        "Kurumsal Finans",
                        7
                ))
                .thenReturn(false);

        when(departmanRepository.saveAndFlush(
                mevcutDepartman
        )).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        KaynakCakismasiException exception =
                assertThrows(
                        KaynakCakismasiException.class,
                        () -> service.departmanGuncelle(
                                "hr-id",
                                7,
                                new DepartmanAdiRequest(
                                        "Kurumsal Finans"
                                )
                        )
                );

        assertEquals(
                "Departman zaten mevcut.",
                exception.getMessage()
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void silmedeForeignKeyYarisi409aCevrilir() {
        Departman departman = new Departman();
        departman.setDepartmanId(7);
        departman.setDepartmanAdi("Finans");

        when(departmanRepository.findByIdForUpdate(7))
                .thenReturn(Optional.of(departman));
        when(personelRepository.existsByDepartman_DepartmanId(7))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("Foreign key constraint"))
                .when(departmanRepository)
                .flush();

        KaynakCakismasiException exception = assertThrows(
                KaynakCakismasiException.class,
                () -> service.departmanSil("hr-id", 7)
        );

        assertEquals(
                "Departman kullanımda olduğu için silinemez.",
                exception.getMessage()
        );
        verifyNoInteractions(auditService);
    }
}
