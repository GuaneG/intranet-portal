package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanCreateRequest;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.EkipmanAktoruYetkiService;
import com.jforce.backend.service.EkipmanEnvanterService;
import com.jforce.backend.service.EkipmanTipiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EkipmanEnvanterServiceTest {

    @Mock
    private EkipmanAktoruYetkiService ekipmanAktoruYetkiService;

    @Mock
    private EkipmanTipiService ekipmanTipiService;

    @Mock
    private EkipmanBilgileriRepository ekipmanRepository;

    @Mock
    private ZimmetlemeBilgileriRepository zimmetRepository;

    @Mock
    private AuditService auditService;

    private EkipmanEnvanterService service;
    private Personel admin;
    private EkipmanTipi aktifTip;

    @BeforeEach
    void setUp() {
        service = new EkipmanEnvanterService(
                ekipmanAktoruYetkiService,
                ekipmanTipiService,
                ekipmanRepository,
                zimmetRepository,
                auditService
        );

        admin = new Personel();
        admin.setPersonelId("admin-id");
        admin.setKullaniciAdi("admin.aktor");

        aktifTip = new EkipmanTipi();
        aktifTip.setEkipmanTipiId(7);
        aktifTip.setTipAdi("Laptop");
        aktifTip.setAktifMi(true);
    }

    @Test
    void olusturmadaDbUniqueYarisiKontrollu409aCevrilirVeAuditOlusturmaz() {
        EkipmanCreateRequest request = new EkipmanCreateRequest(
                7,
                "Lenovo",
                "T14",
                "DB-RACE-001"
        );

        when(ekipmanAktoruYetkiService.adminAktoruGetir("admin-id"))
                .thenReturn(admin);
        when(ekipmanTipiService.aktifTipGetir(7)).thenReturn(aktifTip);
        when(ekipmanRepository.existsBySeriNoIgnoreCase("DB-RACE-001"))
                .thenReturn(false);
        when(ekipmanRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException(
                        "unique constraint"
                ));

        KaynakCakismasiException hata = assertThrows(
                KaynakCakismasiException.class,
                () -> service.ekipmanOlustur("admin-id", request)
        );

        assertEquals(
                "Ekipman seri numarası zaten mevcut.",
                hata.getMessage()
        );
        verifyNoInteractions(auditService);
    }
}
