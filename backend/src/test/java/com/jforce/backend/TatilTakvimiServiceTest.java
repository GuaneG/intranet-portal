package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.config.RezervasyonAyarlari;
import com.jforce.backend.model.dto.request.TatilKaydetRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.entity.TatilTakvimiKilit;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.repository.TatilTakvimiKilitRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.TatilTakvimiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TatilTakvimiServiceTest {

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private TatilRepository tatilRepository;

    @Mock
    private TatilTakvimiKilitRepository tatilTakvimiKilitRepository;

    @Mock
    private OdaRepository odaRepository;

    @Mock
    private RezervasyonRepository rezervasyonRepository;

    @Mock
    private AuditService auditService;

    private TatilTakvimiService service;
    private Personel hr;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-22T09:00:00Z"),
                ZoneId.of("Europe/Istanbul")
        );
        RezervasyonAyarlari rezervasyonAyarlari = new RezervasyonAyarlari();
        rezervasyonAyarlari.setYarimGunBitisSaati(13);

        service = new TatilTakvimiService(
                personelRepository,
                tatilRepository,
                tatilTakvimiKilitRepository,
                odaRepository,
                rezervasyonRepository,
                rezervasyonAyarlari,
                clock,
                auditService
        );

        Rol hrRolu = new Rol();
        hrRolu.setRolAdi(RolAdi.HR);

        hr = new Personel();
        hr.setPersonelId("hr-id");
        hr.setKullaniciAdi("hr.aktor");
        hr.setCalisiyorMu(true);
        hr.setRol(hrRolu);

        when(tatilTakvimiKilitRepository.takvimiKilitle())
                .thenReturn(Optional.of(new TatilTakvimiKilit(1)));
    }

    @Test
    void silmedeOptimisticLockYarisiAuditsizKalir() {
        Tatil tatil = new Tatil();
        tatil.setTatilId(7);
        tatil.setAd("Silinecek Tatil");
        tatil.setTarih(LocalDate.of(2026, 11, 10));
        tatil.setBitisTarihi(LocalDate.of(2026, 11, 10));
        tatil.setKategori(TatilKategori.SIRKET);
        tatil.setGunSuresi(TatilGunSuresi.TAM_GUN);

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(tatilRepository.findById(7))
                .thenReturn(Optional.of(tatil));

        doThrow(
                new OptimisticLockingFailureException(
                        "stale tatil version"
                )
        )
                .when(tatilRepository)
                .flush();

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> service.tatilSil(
                        "hr-id",
                        7
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void guncellemedeOptimisticLockYarisiAuditsizKalir() {
        Tatil tatil = new Tatil();
        tatil.setTatilId(7);
        tatil.setAd("Mevcut Tatil");
        tatil.setTarih(LocalDate.of(2026, 11, 10));
        tatil.setBitisTarihi(LocalDate.of(2026, 11, 10));
        tatil.setKategori(TatilKategori.SIRKET);
        tatil.setGunSuresi(TatilGunSuresi.TAM_GUN);

        TatilKaydetRequest request = new TatilKaydetRequest(
                "Güncellenmiş Tatil",
                LocalDate.of(2026, 12, 10),
                TatilKategori.RESMI_DEGISKEN,
                TatilGunSuresi.YARIM_GUN
        );

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(tatilRepository.findById(7))
                .thenReturn(Optional.of(tatil));

        when(tatilRepository.saveAndFlush(any(Tatil.class)))
                .thenThrow(
                        new OptimisticLockingFailureException(
                                "stale tatil version"
                        )
                );

        assertThrows(
                OptimisticLockingFailureException.class,
                () -> service.tatilGuncelle(
                        "hr-id",
                        7,
                        request
                )
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void guncellemedeUniqueConstraintYarisi409aCevrilir() {
        Tatil tatil = new Tatil();
        tatil.setTatilId(7);
        tatil.setAd("Mevcut Tatil");
        tatil.setTarih(LocalDate.of(2026, 11, 10));
        tatil.setBitisTarihi(LocalDate.of(2026, 11, 10));
        tatil.setKategori(TatilKategori.SIRKET);
        tatil.setGunSuresi(TatilGunSuresi.TAM_GUN);

        TatilKaydetRequest request = new TatilKaydetRequest(
                "Güncellenmiş Tatil",
                LocalDate.of(2026, 12, 10),
                TatilKategori.RESMI_DEGISKEN,
                TatilGunSuresi.YARIM_GUN
        );

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(tatilRepository.findById(7))
                .thenReturn(Optional.of(tatil));

        when(tatilRepository.saveAndFlush(any(Tatil.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        KaynakCakismasiException exception = assertThrows(
                KaynakCakismasiException.class,
                () -> service.tatilGuncelle(
                        "hr-id",
                        7,
                        request
                )
        );

        assertEquals(
                "Seçilen tarih aralığı mevcut bir tatille çakışıyor.",
                exception.getMessage()
        );

        verifyNoInteractions(auditService);
    }

    @Test
    void olusturmadaUniqueConstraintYarisi409aCevrilir() {
        LocalDate tarih = LocalDate.of(2026, 10, 29);

        TatilKaydetRequest request = new TatilKaydetRequest(
                "Cumhuriyet Bayramı",
                tarih,
                TatilKategori.RESMI_SABIT,
                TatilGunSuresi.TAM_GUN
        );

        when(personelRepository.findById("hr-id"))
                .thenReturn(Optional.of(hr));

        when(tatilRepository.saveAndFlush(any(Tatil.class)))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "unique constraint"
                        )
                );

        KaynakCakismasiException exception = assertThrows(
                KaynakCakismasiException.class,
                () -> service.tatilOlustur("hr-id", request)
        );

        assertEquals(
                "Seçilen tarih aralığı mevcut bir tatille çakışıyor.",
                exception.getMessage()
        );

        verifyNoInteractions(auditService);
    }
}
