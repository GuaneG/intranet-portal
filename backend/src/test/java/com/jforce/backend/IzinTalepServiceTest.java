package com.jforce.backend;

import com.jforce.backend.model.dto.request.IzinTalepCreateRequest;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.entity.IzinTuru;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.IzinTuruRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.IsGunuHesaplamaService;
import com.jforce.backend.service.IzinAktoruYetkiService;
import com.jforce.backend.service.IzinTalepService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IzinTalepServiceTest {

    @Test
    void persistenceHatasiAyniInstanceIleTasinirVeAuditOlusturulmaz() {
        PersonelRepository personelRepository = mock(PersonelRepository.class);
        IzinAktoruYetkiService izinAktoruYetkiService = mock(
                IzinAktoruYetkiService.class
        );
        IzinTuruRepository izinTuruRepository = mock(IzinTuruRepository.class);
        DurumTuruRepository durumTuruRepository = mock(
                DurumTuruRepository.class
        );
        IsGunuHesaplamaService isGunuHesaplamaService = mock(
                IsGunuHesaplamaService.class
        );
        IzinBilgileriRepository izinBilgileriRepository = mock(
                IzinBilgileriRepository.class
        );
        AuditService auditService = mock(AuditService.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-24T09:00:00Z"),
                ZoneOffset.UTC
        );
        IzinTalepService service = new IzinTalepService(
                personelRepository,
                izinAktoruYetkiService,
                izinTuruRepository,
                durumTuruRepository,
                isGunuHesaplamaService,
                izinBilgileriRepository,
                auditService,
                clock
        );

        String aktorId = "aktor-id";
        Personel aktor = mock(Personel.class);
        IzinTuru izinTuru = mock(IzinTuru.class);
        DurumTuru durum = mock(DurumTuru.class);
        LocalDate tarih = LocalDate.of(2026, 8, 31);
        IzinTalepCreateRequest request = new IzinTalepCreateRequest(
                1,
                tarih,
                tarih,
                null
        );
        DataIntegrityViolationException persistenceHatasi =
                new DataIntegrityViolationException(
                        "SQL constraint ayrıntısı"
                );

        when(personelRepository.findByIdForUpdate(aktorId))
                .thenReturn(Optional.of(aktor));
        when(aktor.getPersonelId()).thenReturn(aktorId);
        when(izinTuruRepository.findById(1)).thenReturn(Optional.of(izinTuru));
        when(durumTuruRepository.findByDurumKodu(TalepDurum.ONAY_BEKLIYOR))
                .thenReturn(Optional.of(durum));
        when(isGunuHesaplamaService.isGunuSayisiniHesapla(tarih, tarih))
                .thenReturn(BigDecimal.ONE);
        when(izinBilgileriRepository.findCakismaAdaylari(
                aktorId,
                List.of(TalepDurum.ONAY_BEKLIYOR, TalepDurum.ONAYLANDI),
                tarih,
                tarih
        )).thenReturn(List.of());
        when(izinBilgileriRepository.saveAndFlush(any(IzinBilgileri.class)))
                .thenThrow(persistenceHatasi);

        DataIntegrityViolationException firlatilan = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.talepOlustur(aktorId, request)
        );

        assertSame(persistenceHatasi, firlatilan);
        verifyNoInteractions(auditService);
    }
}
