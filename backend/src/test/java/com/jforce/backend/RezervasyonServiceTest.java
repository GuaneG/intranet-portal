package com.jforce.backend;

import com.jforce.backend.model.dto.request.RezervasyonRequest;
import com.jforce.backend.model.entity.Oda;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.RezervasyonService;
import com.jforce.backend.service.RezervasyonZamanPolitikasi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RezervasyonServiceTest {

    @Mock
    private OdaRepository odaRepository;

    @Mock
    private RezervasyonRepository rezervasyonRepository;

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private RezervasyonZamanPolitikasi zamanPolitikasi;

    @Mock
    private AuditService auditService;

    private RezervasyonService service;

    @BeforeEach
    void setUp() {
        service = new RezervasyonService(
                odaRepository,
                rezervasyonRepository,
                personelRepository,
                zamanPolitikasi,
                auditService,
                Clock.fixed(
                        Instant.parse("2026-09-10T07:20:00Z"),
                        ZoneId.of("Europe/Istanbul")
                )
        );
    }

    @Test
    void beklenmeyenPersistenceHatasiAyniInstanceIleYayilirVeAuditOlusmaz() {
        Personel aktor = new Personel();
        aktor.setPersonelId("aktor-id");
        aktor.setKullaniciAdi("aktor.personel");
        aktor.setCalisiyorMu(true);

        Oda oda = new Oda();
        oda.setOdaId(7);
        oda.setOdaAd("Büyük Salon");
        oda.setOdaKapasite(12);

        RezervasyonRequest request = new RezervasyonRequest(
                7,
                LocalDate.of(2026, 9, 11),
                9,
                10,
                "Persistence testi",
                3
        );
        DataIntegrityViolationException persistenceHatasi =
                new DataIntegrityViolationException(
                        "SQL constraint ve dahili şema ayrıntısı"
                );

        when(personelRepository.findByIdForUpdate("aktor-id"))
                .thenReturn(Optional.of(aktor));
        when(odaRepository.findByIdForUpdate(7))
                .thenReturn(Optional.of(oda));
        when(rezervasyonRepository.existsAktifOdaCakismasi(
                7, request.tarih(), 9, 10
        )).thenReturn(false);
        when(rezervasyonRepository.existsAktifOwnerCakismasi(
                "aktor-id", request.tarih(), 9, 10
        )).thenReturn(false);
        when(rezervasyonRepository.saveAndFlush(any()))
                .thenThrow(persistenceHatasi);

        DataIntegrityViolationException firlatilan = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.rezervasyonOlustur("aktor-id", request)
        );

        assertSame(persistenceHatasi, firlatilan);
        verifyNoInteractions(auditService);
    }
}
