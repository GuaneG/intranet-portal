package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanTipiCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanTipiUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanTipiLookupResponse;
import com.jforce.backend.model.dto.response.EkipmanTipiResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.EkipmanTipiRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class EkipmanTipiService {

    private final EkipmanAktoruYetkiService ekipmanAktoruYetkiService;
    private final EkipmanTipiRepository ekipmanTipiRepository;
    private final EkipmanTalepRepository ekipmanTalepRepository;
    private final AuditService auditService;

    public EkipmanTipiService(
            EkipmanAktoruYetkiService ekipmanAktoruYetkiService,
            EkipmanTipiRepository ekipmanTipiRepository,
            EkipmanTalepRepository ekipmanTalepRepository,
            AuditService auditService
    ) {
        this.ekipmanAktoruYetkiService = ekipmanAktoruYetkiService;
        this.ekipmanTipiRepository = ekipmanTipiRepository;
        this.ekipmanTalepRepository = ekipmanTalepRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
    public List<EkipmanTipiLookupResponse> aktifTipleriGetir(String aktorId) {
        ekipmanAktoruYetkiService.aktifAktorGetir(aktorId);

        return ekipmanTipiRepository
                .findAllByAktifMiTrueOrderByTipAdiAscEkipmanTipiIdAsc()
                .stream()
                .map(tip -> new EkipmanTipiLookupResponse(
                        tip.getEkipmanTipiId(),
                        tip.getTipAdi()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<EkipmanTipiResponse> adminTipleriGetir(
            String aktorId,
            String arama,
            Boolean aktif,
            int page,
            int size
    ) {
        ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        sayfalamayiDogrula(page, size);
        String normalizeArama = bosIseNull(arama);

        Page<EkipmanTipi> sonuc = ekipmanTipiRepository.findAdminSayfasi(
                normalizeArama,
                aktif,
                PageRequest.of(page, size)
        );

        List<EkipmanTipiResponse> icerik = sonuc.getContent()
                .stream()
                .map(this::responseOlustur)
                .toList();

        return new SayfaliResponse<>(
                icerik,
                sonuc.getNumber(),
                sonuc.getSize(),
                sonuc.getTotalElements(),
                sonuc.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public EkipmanTipiResponse tipOlustur(
            String aktorId,
            EkipmanTipiCreateRequest request
    ) {
        Personel aktor = ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        String normalizeAd = adiNormalizeEt(request == null ? null : request.ad());

        if (ekipmanTipiRepository.existsByTipAdiIgnoreCase(normalizeAd)) {
            throw new KaynakCakismasiException(
                    "Ekipman tipi zaten mevcut."
            );
        }

        EkipmanTipi tip = new EkipmanTipi();
        tip.setTipAdi(normalizeAd);
        tip.setAktifMi(true);
        tip = guvenliKaydet(tip);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TIPI_EKLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Ekipman tipi eklendi.",
                aktor
        );

        return responseOlustur(tip);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void tipGuncelle(
            String aktorId,
            Integer ekipmanTipiId,
            EkipmanTipiUpdateRequest request
    ) {
        Personel aktor = ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        idDogrula(ekipmanTipiId);

        if (request == null
                || (request.ad() == null && request.aktifMi() == null)) {
            throw new GecersizIstekException(
                    "En az bir güncelleme alanı gönderilmelidir."
            );
        }

        String hedefAd = request.ad() == null
                ? null
                : adiNormalizeEt(request.ad());
        EkipmanTipi tip = ekipmanTipiRepository
                .findByIdForUpdate(ekipmanTipiId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Ekipman tipi bulunamadı."
                ));
        hedefAd = hedefAd == null ? tip.getTipAdi() : hedefAd;
        boolean hedefAktiflik = request.aktifMi() == null
                ? tip.isAktifMi()
                : request.aktifMi();

        if (Objects.equals(tip.getTipAdi(), hedefAd)
                && tip.isAktifMi() == hedefAktiflik) {
            return;
        }

        if (!Objects.equals(tip.getTipAdi(), hedefAd)
                && ekipmanTipiRepository
                .existsByTipAdiIgnoreCaseAndEkipmanTipiIdNot(
                        hedefAd,
                        ekipmanTipiId
                )) {
            throw new KaynakCakismasiException(
                    "Ekipman tipi zaten mevcut."
            );
        }

        if (tip.isAktifMi()
                && !hedefAktiflik
                && ekipmanTalepRepository.existsByEkipmanTipiIdAndDurum(
                        ekipmanTipiId,
                        TalepDurum.ONAY_BEKLIYOR
                )) {
            throw new KaynakCakismasiException(
                    "Ekipman tipinin bekleyen talepleri bulunduğu için pasifleştirilemez."
            );
        }

        tip.setTipAdi(hedefAd);
        tip.setAktifMi(hedefAktiflik);
        guvenliKaydet(tip);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TIPI_GUNCELLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Ekipman tipi güncellendi.",
                aktor
        );
    }

    @Transactional
    public EkipmanTipi aktifTipGetir(Integer ekipmanTipiId) {
        idDogrula(ekipmanTipiId);
        EkipmanTipi tip = ekipmanTipiRepository
                .findByIdForUpdate(ekipmanTipiId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Ekipman tipi bulunamadı."
                ));

        if (!tip.isAktifMi()) {
            throw new KaynakCakismasiException(
                    "Ekipman tipi aktif değil."
            );
        }

        return tip;
    }

    private EkipmanTipi guvenliKaydet(EkipmanTipi tip) {
        try {
            return ekipmanTipiRepository.saveAndFlush(tip);
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException(
                    "Ekipman tipi zaten mevcut."
            );
        }
    }

    private EkipmanTipiResponse responseOlustur(EkipmanTipi tip) {
        return new EkipmanTipiResponse(
                tip.getEkipmanTipiId(),
                tip.getTipAdi(),
                tip.isAktifMi()
        );
    }

    private String adiNormalizeEt(String ad) {
        if (ad == null) {
            throw new GecersizIstekException(
                    "Ekipman tipi adı zorunludur."
            );
        }

        String normalizeAd = ad.trim();
        if (normalizeAd.isEmpty() || normalizeAd.length() > 50) {
            throw new GecersizIstekException(
                    "Ekipman tipi adı 1 ile 50 karakter arasında olmalıdır."
            );
        }
        return normalizeAd;
    }

    private String bosIseNull(String deger) {
        if (deger == null) {
            return null;
        }
        String normalizeDeger = deger.trim();
        return normalizeDeger.isEmpty() ? null : normalizeDeger;
    }

    private void sayfalamayiDogrula(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GecersizIstekException(
                    "Geçersiz sayfalama bilgisi."
            );
        }
    }

    private void idDogrula(Integer ekipmanTipiId) {
        if (ekipmanTipiId == null || ekipmanTipiId <= 0) {
            throw new GecersizIstekException(
                    "Ekipman tipi ID pozitif olmalıdır."
            );
        }
    }
}
