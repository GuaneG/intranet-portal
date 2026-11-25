package com.jforce.backend;

import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanTalepOnayRequest;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.EkipmanAktoruYetkiService;
import com.jforce.backend.service.EkipmanTalepKararService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EkipmanTalepKararServiceTest {

    @Mock private EkipmanAktoruYetkiService aktorYetkiService;
    @Mock private EkipmanTalepRepository talepRepository;
    @Mock private EkipmanBilgileriRepository ekipmanRepository;
    @Mock private ZimmetlemeBilgileriRepository zimmetRepository;
    @Mock private PersonelRepository personelRepository;
    @Mock private DurumTuruRepository durumTuruRepository;
    @Mock private AuditService auditService;

    private EkipmanTalepKararService service;

    @BeforeEach
    void setUp() {
        service = new EkipmanTalepKararService(
                aktorYetkiService,
                talepRepository,
                ekipmanRepository,
                zimmetRepository,
                personelRepository,
                durumTuruRepository,
                auditService,
                Clock.fixed(
                        Instant.parse("2026-08-31T08:00:00Z"),
                        ZoneId.of("Europe/Istanbul")
                )
        );
    }

    @Test
    void zimmetUniqueConstraintYarisiGuvenli409aCevrilirVeAuditYazmaz() {
        String aktorId = UUID.randomUUID().toString();
        String ownerId = UUID.randomUUID().toString();
        String talepId = UUID.randomUUID().toString();
        String ekipmanId = UUID.randomUUID().toString();

        Rol yoneticiRolu = new Rol();
        yoneticiRolu.setRolAdi(RolAdi.YONETICI);
        Personel aktor = personel(aktorId, yoneticiRolu, true, null);
        Rol personelRolu = new Rol();
        personelRolu.setRolAdi(RolAdi.PERSONEL);
        Personel owner = personel(ownerId, personelRolu, true, aktor);

        EkipmanTipi tip = new EkipmanTipi();
        tip.setEkipmanTipiId(7);
        EkipmanTalep talep = new EkipmanTalep();
        talep.setTalepId(talepId);
        talep.setPersonel(owner);
        talep.setEkipmanTipi(tip);
        DurumTuru bekliyor = new DurumTuru();
        bekliyor.setDurumKodu(TalepDurum.ONAY_BEKLIYOR);
        talep.setTalepDurum(bekliyor);

        EkipmanBilgileri ekipman = new EkipmanBilgileri();
        ekipman.setEkipmanId(ekipmanId);
        ekipman.setEkipmanTipiId(tip);
        ekipman.setEkipmanDurum(EkipmanDurum.DEPODA);

        when(aktorYetkiService.kararAktoruGetir(aktorId)).thenReturn(aktor);
        when(talepRepository.findOwnerIdByTalepId(talepId)).thenReturn(Optional.of(ownerId));
        when(personelRepository.findByIdForUpdate(ownerId)).thenReturn(Optional.of(owner));
        when(talepRepository.findByIdForUpdate(talepId)).thenReturn(Optional.of(talep));
        when(ekipmanRepository.findByIdForUpdate(ekipmanId)).thenReturn(Optional.of(ekipman));
        when(zimmetRepository.existsByEkipmanIdAndIadeTarihiIsNull(ekipmanId))
                .thenReturn(false);
        when(zimmetRepository.saveAndFlush(any(ZimmetlemeBilgileri.class)))
                .thenThrow(new DataIntegrityViolationException("generated unique constraint"));

        KaynakCakismasiException exception = assertThrows(
                KaynakCakismasiException.class,
                () -> service.talebiOnayla(
                        aktorId,
                        talepId,
                        new EkipmanTalepOnayRequest(ekipmanId)
                )
        );

        assertEquals("Ekipman artık zimmete uygun değil.", exception.getMessage());
        assertEquals(TalepDurum.ONAY_BEKLIYOR, talep.getTalepDurum().getDurumKodu());
        assertEquals(EkipmanDurum.DEPODA, ekipman.getEkipmanDurum());
        verify(ekipmanRepository, never()).saveAndFlush(any(EkipmanBilgileri.class));
        verify(talepRepository, never()).saveAndFlush(any(EkipmanTalep.class));
        verifyNoInteractions(auditService, durumTuruRepository);
    }

    private Personel personel(
            String id,
            Rol rol,
            boolean aktif,
            Personel yonetici
    ) {
        Personel personel = new Personel();
        personel.setPersonelId(id);
        personel.setKullaniciAdi("test-" + id);
        personel.setRol(rol);
        personel.setCalisiyorMu(aktif);
        personel.setYonetici(yonetici);
        return personel;
    }
}
