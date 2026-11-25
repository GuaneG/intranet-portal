package com.jforce.backend;

import com.jforce.backend.model.dto.request.IzinTalepRedRequest;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.entity.IzinTuru;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.IzinAktoruYetkiService;
import com.jforce.backend.service.IzinTalepKararService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IzinTalepKararServiceTest {

    private static final String AKTOR_ID =
            "00000000-0000-0000-0000-000000004001";
    private static final String OWNER_ID =
            "00000000-0000-0000-0000-000000004002";
    private static final String TALEP_ID =
            "00000000-0000-0000-0000-000000004003";

    @Mock
    private IzinBilgileriRepository izinBilgileriRepository;
    @Mock
    private IzinAktoruYetkiService izinAktoruYetkiService;
    @Mock
    private PersonelRepository personelRepository;
    @Mock
    private DurumTuruRepository durumTuruRepository;
    @Mock
    private AuditService auditService;

    private IzinTalepKararService service;
    private Personel aktor;
    private Personel owner;
    private IzinBilgileri talep;

    @BeforeEach
    void setUp() {
        service = new IzinTalepKararService(
                izinBilgileriRepository,
                izinAktoruYetkiService,
                personelRepository,
                durumTuruRepository,
                auditService,
                Clock.fixed(
                        Instant.parse("2026-08-25T09:00:00Z"),
                        ZoneId.of("Europe/Istanbul")
                )
        );

        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        aktor = new Personel();
        aktor.setPersonelId(AKTOR_ID);
        aktor.setKullaniciAdi("karar.unit.yonetici");
        aktor.setRol(yoneticiRolu);
        aktor.setCalisiyorMu(true);

        owner = new Personel();
        owner.setPersonelId(OWNER_ID);
        owner.setCalisiyorMu(true);
        owner.setYonetici(aktor);

        DurumTuru bekliyor = new DurumTuru();
        bekliyor.setDurumKodu(TalepDurum.ONAY_BEKLIYOR);
        IzinTuru mazeretIzni = new IzinTuru();
        mazeretIzni.setYillikHaktanDuserMi(false);

        talep = new IzinBilgileri();
        talep.setIzinId(TALEP_ID);
        talep.setPersonelId(owner);
        talep.setIzinDurumId(bekliyor);
        talep.setIzinTuruId(mazeretIzni);
        talep.setBaslangicTarihi(LocalDate.of(2026, 9, 7));

        when(izinAktoruYetkiService.kararAktoruGetir(AKTOR_ID))
                .thenReturn(aktor);
        when(izinBilgileriRepository.findByIdForUpdate(TALEP_ID))
                .thenReturn(Optional.of(talep));
        when(personelRepository.findByIdForUpdate(OWNER_ID))
                .thenReturn(Optional.of(owner));
    }

    @Test
    void onayPersistenceHatasiAyniNesneyleYukariTasinirAuditOlusmaz() {
        DurumTuru onaylandi = new DurumTuru();
        onaylandi.setDurumKodu(TalepDurum.ONAYLANDI);
        when(durumTuruRepository.findByDurumKodu(TalepDurum.ONAYLANDI))
                .thenReturn(Optional.of(onaylandi));
        DataIntegrityViolationException persistenceHatasi =
                new DataIntegrityViolationException("constraint-secret");
        when(izinBilgileriRepository.saveAndFlush(talep))
                .thenThrow(persistenceHatasi);

        DataIntegrityViolationException firlatilan = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.talebiOnayla(AKTOR_ID, TALEP_ID)
        );

        assertSame(persistenceHatasi, firlatilan);
        verifyNoInteractions(auditService);
    }

    @Test
    void redPersistenceHatasiAyniNesneyleYukariTasinirAuditOlusmaz() {
        DurumTuru reddedildi = new DurumTuru();
        reddedildi.setDurumKodu(TalepDurum.REDDEDILDI);
        when(durumTuruRepository.findByDurumKodu(TalepDurum.REDDEDILDI))
                .thenReturn(Optional.of(reddedildi));
        DataIntegrityViolationException persistenceHatasi =
                new DataIntegrityViolationException("constraint-secret");
        when(izinBilgileriRepository.saveAndFlush(talep))
                .thenThrow(persistenceHatasi);

        DataIntegrityViolationException firlatilan = assertThrows(
                DataIntegrityViolationException.class,
                () -> service.talebiReddet(
                        AKTOR_ID,
                        TALEP_ID,
                        new IzinTalepRedRequest("Uygun değil.")
                )
        );

        assertSame(persistenceHatasi, firlatilan);
        verifyNoInteractions(auditService);
    }
}
