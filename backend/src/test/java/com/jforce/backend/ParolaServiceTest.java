package com.jforce.backend;

import com.jforce.backend.model.dto.request.IlkParolaDegistirRequest;
import com.jforce.backend.model.dto.request.ParolaDegistirRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParolaServiceTest {

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private ParolaService parolaService;

    @Test
    void ilkParolaBasariylaDegistirilir() {
        Personel personel = new Personel();
        personel.setPersonelId("personel-id");
        personel.setKullaniciAdi("test.kullanici");
        personel.setParolaHash("eski-hash");
        personel.setCalisiyorMu(true);
        personel.setParolaDegistirilmeliMi(true);

        when(personelRepository.findByKullaniciAdi("test.kullanici"))
                .thenReturn(Optional.of(personel));

        when(passwordEncoder.matches("test.kullanici", "eski-hash"
        )).thenReturn(true);

        when(passwordEncoder.encode("Yeni1234!"))
                .thenReturn("yeni-hash");

        IlkParolaDegistirRequest request = new IlkParolaDegistirRequest("test.kullanici", "test.kullanici", "Yeni1234!");

        parolaService.ilkParolayiDegistir(request);

        assertEquals("yeni-hash", personel.getParolaHash());
        assertFalse(personel.getParolaDegistirilmeliMi());

        verify(refreshTokenService).revokeAllForPersonel(personel);

        verify(auditService).logKaydetOrtakTransaction(
                eq(AuditEylem.PAROLA_DEGISTIRILDI),
                same(personel),
                eq("test.kullanici"),
                anyString(),
                same(personel)
        );
    }

    @Test
    void normalParolaBasariylaDegistirilir() {
        Personel personel = new Personel();
        personel.setPersonelId("personel-id");
        personel.setKullaniciAdi("test.kullanici");
        personel.setParolaHash("eski-hash");
        personel.setCalisiyorMu(true);
        personel.setParolaDegistirilmeliMi(false);

        when(personelRepository.findById("personel-id"))
                .thenReturn(Optional.of(personel));

        when(passwordEncoder.matches("Eski1234!", "eski-hash"))
                .thenReturn(true);

        when(passwordEncoder.encode("Yeni1234!"))
                .thenReturn("yeni-hash");

        ParolaDegistirRequest request = new ParolaDegistirRequest("Eski1234!", "Yeni1234!");

        parolaService.parolaDegistir("personel-id", request);

        assertEquals("yeni-hash", personel.getParolaHash());
        assertFalse(personel.getParolaDegistirilmeliMi());

        verify(refreshTokenService).revokeAllForPersonel(personel);

        verify(auditService).logKaydetOrtakTransaction(
                eq(AuditEylem.PAROLA_DEGISTIRILDI),
                same(personel),
                eq("test.kullanici"),
                anyString(),
                same(personel)
        );
    }
}