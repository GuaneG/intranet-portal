package com.jforce.backend;

import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.ProfilFotografiDepolamaService;
import com.jforce.backend.service.ProfilFotografiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfilFotografiServiceTest {

    private static final String PERSONEL_ID = "personel-id";
    private static final String YENI_ANAHTAR =
            "00000000-0000-4000-8000-000000000001.jpg";
    private static final String TRANSACTION_HATASI =
            "Profil fotoğrafı yükleme işlemi aktif transaction gerektirir.";

    @Mock
    private PersonelRepository personelRepository;

    @Mock
    private ProfilFotografiDepolamaService depolamaService;

    @Mock
    private AuditService auditService;

    @Mock
    private MultipartFile dosya;

    private ProfilFotografiService profilFotografiService;
    private Personel personel;

    @BeforeEach
    void setUp() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());

        profilFotografiService = new ProfilFotografiService(
                personelRepository,
                depolamaService,
                auditService
        );

        personel = new Personel();
        personel.setPersonelId(PERSONEL_ID);
        personel.setKullaniciAdi("test.personel");
        personel.setCalisiyorMu(true);
        personel.setProfilFoto("def_pfp.png");

        when(personelRepository.findById(PERSONEL_ID))
                .thenReturn(Optional.of(personel));
        when(depolamaService.kaydet(dosya)).thenReturn(YENI_ANAHTAR);
    }

    @Test
    void transactionYokkenYeniDosyayiTamBirKereSilipAsilHatayiKorur() {
        IllegalStateException hata = assertThrows(
                IllegalStateException.class,
                () -> profilFotografiService.profilFotografimiGuncelle(
                        PERSONEL_ID,
                        dosya
                )
        );

        assertEquals(TRANSACTION_HATASI, hata.getMessage());
        assertNull(hata.getCause());
        assertEquals("def_pfp.png", personel.getProfilFoto());

        InOrder storageSirasi = inOrder(depolamaService);
        storageSirasi.verify(depolamaService).kaydet(dosya);
        storageSirasi.verify(depolamaService).sil(YENI_ANAHTAR);
        verify(depolamaService, times(1)).sil(YENI_ANAHTAR);
        verifyNoInteractions(auditService);
    }

    @Test
    void telafiSilmesiHataVerseDeAsilTransactionHatasiniKorur() {
        doThrow(new IllegalStateException("Cleanup hatası."))
                .when(depolamaService)
                .sil(YENI_ANAHTAR);

        IllegalStateException hata = assertThrows(
                IllegalStateException.class,
                () -> profilFotografiService.profilFotografimiGuncelle(
                        PERSONEL_ID,
                        dosya
                )
        );

        assertEquals(TRANSACTION_HATASI, hata.getMessage());
        assertNull(hata.getCause());
        assertEquals("def_pfp.png", personel.getProfilFoto());
        verify(depolamaService, times(1)).sil(YENI_ANAHTAR);
        verifyNoInteractions(auditService);
    }
}
