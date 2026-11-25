package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.DepartmanAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
public class DepartmanYonetimService {

    private final DepartmanRepository departmanRepository;
    private final PersonelRepository personelRepository;
    private final AuditService auditService;

    public DepartmanYonetimService(
            DepartmanRepository departmanRepository,
            PersonelRepository personelRepository,
            AuditService auditService
    ) {
        this.departmanRepository = departmanRepository;
        this.personelRepository = personelRepository;
        this.auditService = auditService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public LookUpResponse departmanOlustur(
            String aktorId,
            DepartmanAdiRequest request
    ) {
        Personel aktor = aktifAktoruDogrula(aktorId);

        String normalizeAd =
                departmanAdiniNormalizeEt(
                        request.departmanAdi()
                );

        if (departmanRepository
                .existsByDepartmanAdiIgnoreCase(
                        normalizeAd
                )) {
            throw new KaynakCakismasiException(
                    "Departman zaten mevcut."
            );
        }

        Departman departman = new Departman();
        departman.setDepartmanAdi(normalizeAd);

        Departman kaydedilen =
                departmaniGuvenliKaydet(departman);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.DEPARTMAN_EKLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Departman oluşturuldu: " + normalizeAd,
                null
        );

        return new LookUpResponse(
                kaydedilen.getDepartmanId(),
                kaydedilen.getDepartmanAdi()
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void departmanGuncelle(
            String aktorId,
            Integer departmanId,
            DepartmanAdiRequest request
    ) {
        Personel aktor = aktifAktoruDogrula(aktorId);

        if (departmanId == null || departmanId <= 0) {
            throw new GecersizIstekException("Departman ID pozitif olmalıdır.");
        }

        String normalizeAd = departmanAdiniNormalizeEt(request.departmanAdi());

        Departman departman = departmanRepository.findById(departmanId).orElseThrow(() -> new KaynakBulunamadiException("Departman bulunamadı."));

        String eskiAd = departman.getDepartmanAdi();

        if (eskiAd.equals(normalizeAd)) {
            return;
        }

        if (departmanRepository.existsByDepartmanAdiIgnoreCaseAndDepartmanIdNot(normalizeAd, departmanId)) {
            throw new KaynakCakismasiException("Departman zaten mevcut.");
        }

        departman.setDepartmanAdi(normalizeAd);
        departmaniGuvenliKaydet(departman);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.DEPARTMAN_GUNCELLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Departman güncellendi: "
                        + eskiAd
                        + " -> "
                        + normalizeAd,
                null
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void departmanSil(String aktorId, Integer departmanId) {
        Personel aktor = aktifAktoruDogrula(aktorId);

        if (departmanId == null || departmanId <= 0) {
            throw new GecersizIstekException("Departman ID pozitif olmalıdır.");
        }

        Departman departman = departmanRepository.findByIdForUpdate(departmanId)
                .orElseThrow(() -> new KaynakBulunamadiException("Departman bulunamadı."));

        if (personelRepository.existsByDepartman_DepartmanId(departmanId)) {
            throw new KaynakCakismasiException(
                    "Departman kullanımda olduğu için silinemez."
            );
        }

        try {
            departmanRepository.delete(departman);
            departmanRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException(
                    "Departman kullanımda olduğu için silinemez."
            );
        }

        auditService.logKaydetOrtakTransaction(
                AuditEylem.DEPARTMAN_SILINDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Departman silindi: " + departman.getDepartmanAdi(),
                null
        );
    }

    private Departman departmaniGuvenliKaydet(Departman departman) {
        try{
            return departmanRepository.saveAndFlush(departman);
        }catch(DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException("Departman zaten mevcut.");
        }
    }

    private String departmanAdiniNormalizeEt(String departmanAdi) {
        String normalizeAd = departmanAdi.trim();

        if (normalizeAd.length() > 50) {
            throw new GecersizIstekException("Departman adı en fazla 50 karakter olmalıdır.");
        }

        return normalizeAd;
    }

    private Personel aktifAktoruDogrula(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId).orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Pasif personel departman yönetimi yapamaz.");
        }
        return aktor;
    }
}
