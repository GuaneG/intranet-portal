package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanDurumUpdateRequest;
import com.jforce.backend.model.dto.request.EkipmanUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class EkipmanEnvanterService {

    private final EkipmanAktoruYetkiService ekipmanAktoruYetkiService;
    private final EkipmanTipiService ekipmanTipiService;
    private final EkipmanBilgileriRepository ekipmanRepository;
    private final ZimmetlemeBilgileriRepository zimmetRepository;
    private final AuditService auditService;

    public EkipmanEnvanterService(
            EkipmanAktoruYetkiService ekipmanAktoruYetkiService,
            EkipmanTipiService ekipmanTipiService,
            EkipmanBilgileriRepository ekipmanRepository,
            ZimmetlemeBilgileriRepository zimmetRepository,
            AuditService auditService
    ) {
        this.ekipmanAktoruYetkiService = ekipmanAktoruYetkiService;
        this.ekipmanTipiService = ekipmanTipiService;
        this.ekipmanRepository = ekipmanRepository;
        this.zimmetRepository = zimmetRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<EkipmanResponse> ekipmanlariGetir(
            String aktorId,
            Integer ekipmanTipiId,
            EkipmanDurum durum,
            String arama,
            int page,
            int size
    ) {
        ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        sayfalamayiDogrula(page, size);
        nullablePozitifIdDogrula(ekipmanTipiId);

        Page<EkipmanBilgileri> sonuc = ekipmanRepository.findAdminSayfasi(
                ekipmanTipiId,
                durum,
                bosIseNull(arama),
                PageRequest.of(page, size)
        );

        Set<String> acikZimmetliCihazlar = acikZimmetliCihazlariGetir(
                sonuc.getContent()
        );
        List<EkipmanResponse> icerik = sonuc.getContent().stream()
                .map(cihaz -> responseOlustur(
                        cihaz,
                        acikZimmetliCihazlar.contains(cihaz.getEkipmanId())
                ))
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
    public EkipmanResponse ekipmanOlustur(
            String aktorId,
            EkipmanCreateRequest request
    ) {
        Personel aktor = ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        if (request == null) {
            throw new GecersizIstekException("Geçersiz ekipman bilgisi.");
        }

        EkipmanTipi tip = ekipmanTipiService.aktifTipGetir(
                request.ekipmanTipiId()
        );
        String marka = zorunluMetniNormalizeEt(
                request.marka(), 50, "Marka zorunludur."
        );
        String model = nullableMetniNormalizeEt(request.model(), 50);
        String seriNo = zorunluMetniNormalizeEt(
                request.seriNo(), 100, "Seri numarası zorunludur."
        );

        if (ekipmanRepository.existsBySeriNoIgnoreCase(seriNo)) {
            throw seriNoCakismasi();
        }

        EkipmanBilgileri cihaz = new EkipmanBilgileri();
        cihaz.setEkipmanTipiId(tip);
        cihaz.setMarka(marka);
        cihaz.setModel(model);
        cihaz.setSeriNo(seriNo);
        cihaz.setEkipmanDurum(EkipmanDurum.DEPODA);
        cihaz.setKullanimDisiNedeni(null);
        cihaz = guvenliKaydet(cihaz);

        auditKaydet(
                AuditEylem.EKIPMAN_EKLENDI,
                aktor,
                "Ekipman eklendi."
        );
        return responseOlustur(cihaz, false);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void metadataGuncelle(
            String aktorId,
            String ekipmanId,
            EkipmanUpdateRequest request
    ) {
        Personel aktor = ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        String dogrulanmisId = canonicalUuid(ekipmanId);
        if (request == null || (request.marka() == null
                && request.model() == null
                && request.seriNo() == null)) {
            throw new GecersizIstekException(
                    "En az bir güncelleme alanı gönderilmelidir."
            );
        }

        EkipmanBilgileri cihaz = kilitliCihazGetir(dogrulanmisId);
        String hedefMarka = request.marka() == null
                ? cihaz.getMarka()
                : zorunluMetniNormalizeEt(
                        request.marka(), 50, "Marka boş olamaz."
                );
        String hedefModel = request.model() == null
                ? cihaz.getModel()
                : nullableMetniNormalizeEt(request.model(), 50);
        String hedefSeriNo = request.seriNo() == null
                ? cihaz.getSeriNo()
                : zorunluMetniNormalizeEt(
                        request.seriNo(), 100, "Seri numarası boş olamaz."
                );

        if (Objects.equals(cihaz.getMarka(), hedefMarka)
                && Objects.equals(cihaz.getModel(), hedefModel)
                && Objects.equals(cihaz.getSeriNo(), hedefSeriNo)) {
            return;
        }

        if (!Objects.equals(cihaz.getSeriNo(), hedefSeriNo)
                && ekipmanRepository
                .existsBySeriNoIgnoreCaseAndEkipmanIdNot(
                        hedefSeriNo,
                        cihaz.getEkipmanId()
                )) {
            throw seriNoCakismasi();
        }

        cihaz.setMarka(hedefMarka);
        cihaz.setModel(hedefModel);
        cihaz.setSeriNo(hedefSeriNo);
        guvenliKaydet(cihaz);
        auditKaydet(
                AuditEylem.EKIPMAN_GUNCELLENDI,
                aktor,
                "Ekipman güncellendi."
        );
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void durumGuncelle(
            String aktorId,
            String ekipmanId,
            EkipmanDurumUpdateRequest request
    ) {
        Personel aktor = ekipmanAktoruYetkiService.adminAktoruGetir(aktorId);
        String dogrulanmisId = canonicalUuid(ekipmanId);
        if (request == null || request.durum() == null) {
            throw new GecersizIstekException("Ekipman durumu zorunludur.");
        }

        EkipmanBilgileri cihaz = kilitliCihazGetir(dogrulanmisId);
        if (cihaz.getEkipmanDurum() == EkipmanDurum.PERSONELDE
                || request.durum() == EkipmanDurum.PERSONELDE) {
            throw new KaynakCakismasiException(
                    "Personeldeki ekipmanın durumu iade edilmeden değiştirilemez."
            );
        }

        boolean degisti = switch (cihaz.getEkipmanDurum()) {
            case DEPODA -> depodakiCihaziGuncelle(cihaz, request);
            case KULLANIM_DISI -> kullanimDisiCihaziGuncelle(cihaz, request);
            case PERSONELDE -> false;
        };

        if (!degisti) {
            return;
        }

        ekipmanRepository.saveAndFlush(cihaz);
        auditKaydet(
                AuditEylem.EKIPMAN_GUNCELLENDI,
                aktor,
                "Ekipman güncellendi."
        );
    }

    private boolean depodakiCihaziGuncelle(
            EkipmanBilgileri cihaz,
            EkipmanDurumUpdateRequest request
    ) {
        if (request.durum() == EkipmanDurum.DEPODA) {
            nonblankNedeniReddet(request.neden());
            return false;
        }

        String neden = zorunluMetniNormalizeEt(
                request.neden(), 500, "Kullanım dışı nedeni zorunludur."
        );
        if (zimmetRepository.existsByEkipmanIdAndIadeTarihiIsNull(
                cihaz.getEkipmanId()
        )) {
            throw new KaynakCakismasiException(
                    "Açık zimmeti bulunan ekipman kullanım dışına alınamaz."
            );
        }
        cihaz.setEkipmanDurum(EkipmanDurum.KULLANIM_DISI);
        cihaz.setKullanimDisiNedeni(neden);
        return true;
    }

    private boolean kullanimDisiCihaziGuncelle(
            EkipmanBilgileri cihaz,
            EkipmanDurumUpdateRequest request
    ) {
        if (request.durum() == EkipmanDurum.DEPODA) {
            nonblankNedeniReddet(request.neden());
            cihaz.setEkipmanDurum(EkipmanDurum.DEPODA);
            cihaz.setKullanimDisiNedeni(null);
            return true;
        }

        String neden = zorunluMetniNormalizeEt(
                request.neden(), 500, "Kullanım dışı nedeni zorunludur."
        );
        if (Objects.equals(cihaz.getKullanimDisiNedeni(), neden)) {
            return false;
        }
        cihaz.setKullanimDisiNedeni(neden);
        return true;
    }

    private void nonblankNedeniReddet(String neden) {
        if (neden != null && !neden.trim().isEmpty()) {
            throw new GecersizIstekException(
                    "Depodaki ekipman için kullanım dışı nedeni gönderilemez."
            );
        }
    }

    private Set<String> acikZimmetliCihazlariGetir(
            List<EkipmanBilgileri> cihazlar
    ) {
        if (cihazlar.isEmpty()) {
            return Set.of();
        }
        List<String> cihazIdleri = cihazlar.stream()
                .map(EkipmanBilgileri::getEkipmanId)
                .toList();
        return new HashSet<>(zimmetRepository.findOpenEquipmentIds(cihazIdleri));
    }

    private EkipmanBilgileri kilitliCihazGetir(String ekipmanId) {
        return ekipmanRepository.findByIdForUpdate(ekipmanId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Ekipman bulunamadı."
                ));
    }

    private EkipmanBilgileri guvenliKaydet(EkipmanBilgileri cihaz) {
        try {
            return ekipmanRepository.saveAndFlush(cihaz);
        } catch (DataIntegrityViolationException exception) {
            throw seriNoCakismasi();
        }
    }

    private KaynakCakismasiException seriNoCakismasi() {
        return new KaynakCakismasiException(
                "Ekipman seri numarası zaten mevcut."
        );
    }

    private EkipmanResponse responseOlustur(
            EkipmanBilgileri cihaz,
            boolean acikZimmetVarMi
    ) {
        return new EkipmanResponse(
                cihaz.getEkipmanId(),
                cihaz.getEkipmanTipiId().getEkipmanTipiId(),
                cihaz.getEkipmanTipiId().getTipAdi(),
                cihaz.getMarka(),
                cihaz.getModel(),
                cihaz.getSeriNo(),
                cihaz.getEkipmanDurum(),
                cihaz.getKullanimDisiNedeni(),
                acikZimmetVarMi
        );
    }

    private void auditKaydet(
            AuditEylem eylem,
            Personel aktor,
            String detay
    ) {
        auditService.logKaydetOrtakTransaction(
                eylem,
                aktor,
                aktor.getKullaniciAdi(),
                detay,
                aktor
        );
    }

    private String canonicalUuid(String deger) {
        if (deger == null) {
            throw new GecersizIstekException("Geçersiz ekipman ID.");
        }
        try {
            String canonical = UUID.fromString(deger).toString();
            if (!canonical.equals(deger)) {
                throw new GecersizIstekException("Geçersiz ekipman ID.");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException("Geçersiz ekipman ID.");
        }
    }

    private String zorunluMetniNormalizeEt(
            String deger,
            int maksimum,
            String mesaj
    ) {
        if (deger == null) {
            throw new GecersizIstekException(mesaj);
        }
        String normalize = deger.trim();
        if (normalize.isEmpty() || normalize.length() > maksimum) {
            throw new GecersizIstekException(mesaj);
        }
        return normalize;
    }

    private String nullableMetniNormalizeEt(String deger, int maksimum) {
        if (deger == null) {
            return null;
        }
        String normalize = deger.trim();
        if (normalize.isEmpty()) {
            return null;
        }
        if (normalize.length() > maksimum) {
            throw new GecersizIstekException("Geçersiz metin uzunluğu.");
        }
        return normalize;
    }

    private String bosIseNull(String deger) {
        if (deger == null) {
            return null;
        }
        String normalize = deger.trim();
        return normalize.isEmpty() ? null : normalize;
    }

    private void sayfalamayiDogrula(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GecersizIstekException("Geçersiz sayfalama bilgisi.");
        }
    }

    private void nullablePozitifIdDogrula(Integer id) {
        if (id != null && id <= 0) {
            throw new GecersizIstekException(
                    "Ekipman tipi ID pozitif olmalıdır."
            );
        }
    }
}
