package com.jforce.backend.service;

import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.ProfilFotografiResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ProfilFotografiService {

    private static final Logger log = LoggerFactory.getLogger(ProfilFotografiService.class);

    private final PersonelRepository personelRepository;
    private final ProfilFotografiDepolamaService depolamaService;
    private final AuditService auditService;

    public ProfilFotografiService(
            PersonelRepository personelRepository,
            ProfilFotografiDepolamaService depolamaService,
            AuditService auditService
    ) {
        this.personelRepository = personelRepository;
        this.depolamaService = depolamaService;
        this.auditService = auditService;
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ProfilFotografiResponse profilFotografimiGuncelle(
            String personelId,
            MultipartFile dosya
    ) {
        Personel personel = aktifPrincipalGetir(personelId);

        String eskiAnahtar = personel.getProfilFoto();
        String yeniAnahtar = depolamaService.kaydet(dosya);
        AtomicBoolean yeniDosyaSilindi = new AtomicBoolean(false);
        try {
            transactionSenkronizasyonuKaydet(
                    eskiAnahtar,
                    yeniAnahtar,
                    yeniDosyaSilindi
            );
        } catch (RuntimeException ex) {
            yeniDosyayiBirKereSil(yeniAnahtar, yeniDosyaSilindi);
            throw ex;
        }

        personel.setProfilFoto(yeniAnahtar);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.PROFIL_FOTOGRAFI_GUNCELLENDI,
                personel,
                personel.getKullaniciAdi(),
                "Profil fotoğrafı güncellendi.",
                personel
        );

        return new ProfilFotografiResponse(yeniAnahtar);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void profilFotografimiSil(String personelId) {
        Personel personel = aktifPrincipalGetir(personelId);
        String eskiAnahtar = personel.getProfilFoto();
        if (!depolamaService.ozelFotografMi(eskiAnahtar)) {
            return;
        }

        silmeSenkronizasyonuKaydet(eskiAnahtar);
        personel.setProfilFoto("def_pfp.png");

        auditService.logKaydetOrtakTransaction(
                AuditEylem.PROFIL_FOTOGRAFI_SILINDI,
                personel,
                personel.getKullaniciAdi(),
                "Profil fotoğrafı silindi.",
                personel
        );
    }

    private void transactionSenkronizasyonuKaydet(
            String eskiAnahtar,
            String yeniAnahtar,
            AtomicBoolean yeniDosyaSilindi
    ) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Profil fotoğrafı yükleme işlemi aktif transaction gerektirir."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (depolamaService.ozelFotografMi(eskiAnahtar)) {
                            eskiDosyayiSil(eskiAnahtar);
                        }
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            yeniDosyayiBirKereSil(yeniAnahtar, yeniDosyaSilindi);
                        }
                    }
                }
        );
    }

    private void silmeSenkronizasyonuKaydet(String eskiAnahtar) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Profil fotoğrafı silme işlemi aktif transaction gerektirir."
            );
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eskiDosyayiSil(eskiAnahtar);
                    }
                }
        );
    }

    private void eskiDosyayiSil(String eskiAnahtar) {
        try {
            depolamaService.sil(eskiAnahtar);
        } catch (RuntimeException ex) {
            log.warn("Commit sonrası eski profil fotoğrafı temizlenemedi.");
        }
    }

    private void yeniDosyayiBirKereSil(
            String yeniAnahtar,
            AtomicBoolean yeniDosyaSilindi
    ) {
        if (!yeniDosyaSilindi.compareAndSet(false, true)) {
            return;
        }
        try {
            depolamaService.sil(yeniAnahtar);
        } catch (RuntimeException ex) {
            log.warn("Yeni profil fotoğrafı telafi işlemiyle temizlenemedi.");
        }
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ProfilFotografiDosyasi profilFotografiniGetir(
            String aktorPersonelId,
            String dosyaAnahtari
    ) {
        aktifPrincipalGetir(aktorPersonelId);
        return depolamaService.getir(dosyaAnahtari);
    }

    private Personel aktifPrincipalGetir(String personelId) {
        Personel personel = personelRepository.findById(personelId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel profil fotoğrafı işlemi yapamaz.");
        }
        return personel;
    }
}
