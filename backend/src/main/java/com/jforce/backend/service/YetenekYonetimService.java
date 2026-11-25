package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Yetenek;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.PersonelYetenekRepository;
import com.jforce.backend.repository.YetenekRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class YetenekYonetimService {

    private final YetenekRepository yetenekRepository;
    private final PersonelRepository personelRepository;
    private final PersonelYetenekRepository personelYetenekRepository;
    private final AuditService auditService;

    public YetenekYonetimService(
            YetenekRepository yetenekRepository,
            PersonelRepository personelRepository,
            PersonelYetenekRepository personelYetenekRepository,
            AuditService auditService
    ) {
        this.yetenekRepository = yetenekRepository;
        this.personelRepository = personelRepository;
        this.personelYetenekRepository = personelYetenekRepository;
        this.auditService = auditService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public LookUpResponse yetenekOlustur(String aktorId, YetenekAdiRequest request) {
        Personel aktor = aktifAktoruDogrula(aktorId);
        String normalizeAd = yetenekAdiniNormalizeEt(request.yetenekAdi());

        if (yetenekRepository.existsByYetenekAdiIgnoreCase(normalizeAd)) {
            throw new KaynakCakismasiException("Yetenek zaten mevcut.");
        }

        Yetenek yetenek = new Yetenek();
        yetenek.setYetenekAdi(normalizeAd);

        yetenek = yetenegiGuvenliKaydet(yetenek);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.YETENEK_EKLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Yetenek oluşturuldu: " + normalizeAd,
                null
        );

        return new LookUpResponse(yetenek.getYetenekId(), yetenek.getYetenekAdi());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void yetenekGuncelle(String aktorId, Integer yetenekId, YetenekAdiRequest request) {
        Personel aktor = aktifAktoruDogrula(aktorId);

        if (yetenekId == null || yetenekId <= 0) {
            throw new GecersizIstekException("Yetenek ID pozitif olmalıdır.");
        }

        String normalizeAd = yetenekAdiniNormalizeEt(request.yetenekAdi());
        Yetenek yetenek = yetenekRepository.findById(yetenekId).orElseThrow(() -> new KaynakBulunamadiException("Yetenek bulunamadı."));
        String eskiAd = yetenek.getYetenekAdi();

        if (eskiAd.equals(normalizeAd)) {
            return;
        }

        if (yetenekRepository.existsByYetenekAdiIgnoreCaseAndYetenekIdNot(normalizeAd, yetenekId)) {
            throw new KaynakCakismasiException("Yetenek zaten mevcut.");
        }

        yetenek.setYetenekAdi(normalizeAd);
        yetenegiGuvenliKaydet(yetenek);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.YETENEK_GUNCELLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Yetenek güncellendi: "
                        + eskiAd
                        + " -> "
                        + normalizeAd,
                null
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void yetenekSil(String aktorId, Integer yetenekId) {
        Personel aktor = aktifAktoruDogrula(aktorId);

        if (yetenekId == null || yetenekId <= 0) {
            throw new GecersizIstekException("Yetenek ID pozitif olmalıdır.");
        }

        Yetenek yetenek = yetenekRepository.findByIdForUpdate(yetenekId)
                .orElseThrow(() -> new KaynakBulunamadiException("Yetenek bulunamadı."));

        if (personelYetenekRepository.existsByYetenek_YetenekId(yetenekId)) {
            throw new KaynakCakismasiException(
                    "Yetenek kullanımda olduğu için silinemez."
            );
        }

        try {
            yetenekRepository.delete(yetenek);
            yetenekRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException(
                    "Yetenek kullanımda olduğu için silinemez."
            );
        }

        auditService.logKaydetOrtakTransaction(
                AuditEylem.YETENEK_SILINDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Yetenek silindi: " + yetenek.getYetenekAdi(),
                null
        );
    }

    private Personel aktifAktoruDogrula(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel yetenek yönetimi yapamaz.");
        }
        return aktor;
    }

    private String yetenekAdiniNormalizeEt(String yetenekAdi) {
        String normalizeAd = yetenekAdi.trim();
        if (normalizeAd.length() > 50) {
            throw new GecersizIstekException("Yetenek adı en fazla 50 karakter olmalıdır.");
        }
        return normalizeAd;
    }

    private Yetenek yetenegiGuvenliKaydet(Yetenek yetenek) {
        try {
            return yetenekRepository.saveAndFlush(yetenek);
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException("Yetenek zaten mevcut.");
        }
    }
}
