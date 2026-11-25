package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.YetenekRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.YetenekYonetimService;
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
class YetenekYonetimServiceTest {

    @Mock
    private YetenekRepository yetenekRepository;

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private PersonelYetenekRepository personelYetenekRepository;

    @Mock
    private AuditService auditService;

    private YetenekYonetimService service;
    private Personel hr;

    @BeforeEach
    void setUp() {
        service = new YetenekYonetimService(
                yetenekRepository,
                personelRepository,
                personelYetenekRepository,
                auditService
        );

        Rol hrRolu = new Rol();
        hrRolu.setRolAdi(RolAdi.HR);

        hr = new Personel();
        hr.setPersonelId("hr-id");
        hr.setKullaniciAdi("hr.aktor");
        hr.setRol(hrRolu);
        hr.setCalisiyorMu(true);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));
    }

    @Test
    void olusturmadaUniqueConstraintYarisi409aCevrilir() {
        when(
                yetenekRepository
                        .existsByYetenekAdiIgnoreCase("Java")
        ).thenReturn(false);

        when(yetenekRepository.saveAndFlush(any(Yetenek.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        assertThrows(
                KaynakCakismasiException.class,
                () -> service.yetenekOlustur(
                        "hr-id",
                        new YetenekAdiRequest("Java")
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void guncellemedeUniqueConstraintYarisi409aCevrilir() {
        Yetenek java = new Yetenek();
        java.setYetenekId(7);
        java.setYetenekAdi("Java");

        when(yetenekRepository.findById(7))
                .thenReturn(Optional.of(java));

        when(
                yetenekRepository
                        .existsByYetenekAdiIgnoreCaseAndYetenekIdNot(
                                "Java Backend",
                                7
                        )
        ).thenReturn(false);

        when(yetenekRepository.saveAndFlush(java))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        assertThrows(
                KaynakCakismasiException.class,
                () -> service.yetenekGuncelle(
                        "hr-id",
                        7,
                        new YetenekAdiRequest("Java Backend")
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void silmedeForeignKeyYarisi409aCevrilir() {
        Yetenek yetenek = new Yetenek();
        yetenek.setYetenekId(7);
        yetenek.setYetenekAdi("Java");

        when(yetenekRepository.findByIdForUpdate(7))
                .thenReturn(Optional.of(yetenek));
        when(personelYetenekRepository.existsByYetenek_YetenekId(7))
                .thenReturn(false);
        doThrow(new DataIntegrityViolationException("Foreign key constraint"))
                .when(yetenekRepository)
                .flush();

        KaynakCakismasiException exception = assertThrows(
                KaynakCakismasiException.class,
                () -> service.yetenekSil("hr-id", 7)
        );

        assertEquals(
                "Yetenek kullanımda olduğu için silinemez.",
                exception.getMessage()
        );
        verifyNoInteractions(auditService);
    }
}
