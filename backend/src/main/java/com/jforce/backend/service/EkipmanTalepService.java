package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanTalepCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanTalepKararVerenResponse;
import com.jforce.backend.model.dto.response.EkipmanTalepResponse;
import com.jforce.backend.model.dto.response.EkipmanTalepZimmetResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.EkipmanTipi;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EkipmanTalepService {

    private static final int ACIKLAMA_UZUNLUGU = 255;

    private final EkipmanAktoruYetkiService ekipmanAktoruYetkiService;
    private final EkipmanTipiService ekipmanTipiService;
    private final EkipmanTalepRepository ekipmanTalepRepository;
    private final ZimmetlemeBilgileriRepository zimmetRepository;
    private final PersonelRepository personelRepository;
    private final DurumTuruRepository durumTuruRepository;
    private final AuditService auditService;
    private final Clock clock;

    public EkipmanTalepService(
            EkipmanAktoruYetkiService ekipmanAktoruYetkiService,
            EkipmanTipiService ekipmanTipiService,
            EkipmanTalepRepository ekipmanTalepRepository,
            ZimmetlemeBilgileriRepository zimmetRepository,
            PersonelRepository personelRepository,
            DurumTuruRepository durumTuruRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.ekipmanAktoruYetkiService = ekipmanAktoruYetkiService;
        this.ekipmanTipiService = ekipmanTipiService;
        this.ekipmanTalepRepository = ekipmanTalepRepository;
        this.zimmetRepository = zimmetRepository;
        this.personelRepository = personelRepository;
        this.durumTuruRepository = durumTuruRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'HR')")
    public EkipmanTalepResponse talepOlustur(
            String aktorId,
            EkipmanTalepCreateRequest request
    ) {
        requestDogrula(request);
        Personel owner = kilitliOwnerGetir(aktorId);
        EkipmanTipi tip = ekipmanTipiService.aktifTipGetir(request.ekipmanTipiId());
        duplicatePendingDogrula(owner.getPersonelId(), tip.getEkipmanTipiId());

        EkipmanTalep talep = new EkipmanTalep();
        talep.setPersonel(owner);
        talep.setEkipmanTipi(tip);
        talep.setAciklama(aciklamaNormalizeEt(request.aciklama()));
        talep.setTalepDurum(durumGetir(TalepDurum.ONAY_BEKLIYOR));
        talep.setTalepTarih(LocalDateTime.now(clock));
        talep.setIslemYapanId(null);
        talep.setIslemTarihi(null);
        talep.setKararNotu(null);

        EkipmanTalep kaydedilen = ekipmanTalepRepository.saveAndFlush(talep);
        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TALEBI_OLUSTURULDU,
                owner,
                owner.getKullaniciAdi(),
                "Ekipman talebi oluşturuldu.",
                owner
        );
        return responseOlustur(kaydedilen, null);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'HR')")
    public SayfaliResponse<EkipmanTalepResponse> kendiTalepleriniGetir(
            String aktorId,
            TalepDurum durum,
            Integer ekipmanTipiId,
            int page,
            int size
    ) {
        Personel owner = ekipmanAktoruYetkiService.selfServiceAktoruGetir(aktorId);
        sayfalamaDogrula(page, size);
        opsiyonelTipIdDogrula(ekipmanTipiId);

        Page<EkipmanTalep> sonuc = ekipmanTalepRepository.findKendiTalepleri(
                owner.getPersonelId(),
                durum,
                ekipmanTipiId,
                PageRequest.of(page, size)
        );
        Map<String, ZimmetlemeBilgileri> zimmetler = zimmetleriGetir(sonuc.getContent());

        return new SayfaliResponse<>(
                sonuc.getContent().stream()
                        .map(talep -> responseOlustur(talep, zimmetler.get(talep.getTalepId())))
                        .toList(),
                sonuc.getNumber(),
                sonuc.getSize(),
                sonuc.getTotalElements(),
                sonuc.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'HR')")
    public void talepGuncelle(
            String aktorId,
            String talepId,
            EkipmanTalepUpdateRequest request
    ) {
        requestDogrula(request);
        String dogrulanmisTalepId = canonicalUuid(talepId);
        ekipmanAktoruYetkiService.selfServiceAktoruGetir(aktorId);
        String ownerId = ownerSnapshotGetir(dogrulanmisTalepId);
        ownershipDogrula(aktorId, ownerId);

        Personel owner = kilitliOwnerGetir(ownerId);
        EkipmanTalep talep = kilitliTalepGetir(dogrulanmisTalepId);
        ownershipDogrula(owner.getPersonelId(), talep.getPersonel().getPersonelId());
        pendingDogrula(talep);

        EkipmanTipi yeniTip = ekipmanTipiService.aktifTipGetir(request.ekipmanTipiId());
        String yeniAciklama = aciklamaNormalizeEt(request.aciklama());
        boolean tipDegisti = !Objects.equals(
                talep.getEkipmanTipi().getEkipmanTipiId(),
                yeniTip.getEkipmanTipiId()
        );
        if (tipDegisti) {
            duplicatePendingDogrula(owner.getPersonelId(), yeniTip.getEkipmanTipiId());
        }

        if (!tipDegisti && Objects.equals(talep.getAciklama(), yeniAciklama)) {
            return;
        }

        talep.setEkipmanTipi(yeniTip);
        talep.setAciklama(yeniAciklama);
        ekipmanTalepRepository.saveAndFlush(talep);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'HR')")
    public void talepIptalEt(String aktorId, String talepId) {
        String dogrulanmisTalepId = canonicalUuid(talepId);
        ekipmanAktoruYetkiService.selfServiceAktoruGetir(aktorId);
        String ownerId = ownerSnapshotGetir(dogrulanmisTalepId);
        ownershipDogrula(aktorId, ownerId);

        Personel owner = kilitliOwnerGetir(ownerId);
        EkipmanTalep talep = kilitliTalepGetir(dogrulanmisTalepId);
        ownershipDogrula(owner.getPersonelId(), talep.getPersonel().getPersonelId());
        pendingDogrula(talep);

        talep.setTalepDurum(durumGetir(TalepDurum.IPTAL_EDILDI));
        talep.setIslemYapanId(owner);
        talep.setIslemTarihi(LocalDateTime.now(clock));
        talep.setKararNotu(null);
        ekipmanTalepRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TALEBI_IPTAL_EDILDI,
                owner,
                owner.getKullaniciAdi(),
                "Ekipman talebi iptal edildi.",
                owner
        );
    }

    private Personel kilitliOwnerGetir(String ownerId) {
        Personel owner = personelRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
        ekipmanAktoruYetkiService.selfServiceAktoruDogrula(owner);
        return owner;
    }

    private String ownerSnapshotGetir(String talepId) {
        return ekipmanTalepRepository.findOwnerIdByTalepId(talepId)
                .orElseThrow(() -> new KaynakBulunamadiException("Ekipman talebi bulunamadı."));
    }

    private EkipmanTalep kilitliTalepGetir(String talepId) {
        return ekipmanTalepRepository.findByIdForUpdate(talepId)
                .orElseThrow(() -> new KaynakBulunamadiException("Ekipman talebi bulunamadı."));
    }

    private void ownershipDogrula(String aktorId, String ownerId) {
        if (!Objects.equals(aktorId, ownerId)) {
            throw new KaynakBulunamadiException("Ekipman talebi bulunamadı.");
        }
    }

    private void pendingDogrula(EkipmanTalep talep) {
        if (talep.getTalepDurum().getDurumKodu() != TalepDurum.ONAY_BEKLIYOR) {
            throw new KaynakCakismasiException("Talep artık işlem beklemiyor.");
        }
    }

    private void duplicatePendingDogrula(String ownerId, Integer tipId) {
        if (ekipmanTalepRepository.existsPendingByPersonelAndTip(
                ownerId, tipId, TalepDurum.ONAY_BEKLIYOR)) {
            throw new KaynakCakismasiException(
                    "Aynı ekipman tipi için bekleyen talep zaten mevcut."
            );
        }
    }

    private DurumTuru durumGetir(TalepDurum durum) {
        return durumTuruRepository.findByDurumKodu(durum)
                .orElseThrow(() -> new KaynakBulunamadiException("Talep durumu bulunamadı."));
    }

    private Map<String, ZimmetlemeBilgileri> zimmetleriGetir(Collection<EkipmanTalep> talepler) {
        if (talepler.isEmpty()) {
            return Collections.emptyMap();
        }
        return zimmetRepository.findAllByTalepIds(
                        talepler.stream().map(EkipmanTalep::getTalepId).toList())
                .stream()
                .collect(Collectors.toMap(
                        zimmet -> zimmet.getTalep().getTalepId(),
                        Function.identity()
                ));
    }

    private EkipmanTalepResponse responseOlustur(
            EkipmanTalep talep,
            ZimmetlemeBilgileri zimmet
    ) {
        TalepDurum durum = talep.getTalepDurum().getDurumKodu();
        return new EkipmanTalepResponse(
                talep.getTalepId(),
                talep.getEkipmanTipi().getEkipmanTipiId(),
                talep.getEkipmanTipi().getTipAdi(),
                talep.getAciklama(),
                durum,
                durum.getGorunenAd(),
                talep.getTalepTarih(),
                talep.getKararNotu(),
                talep.getIslemTarihi(),
                kararVerenResponse(talep.getIslemYapanId()),
                zimmetResponse(zimmet)
        );
    }

    private EkipmanTalepKararVerenResponse kararVerenResponse(Personel kararVeren) {
        return kararVeren == null ? null : new EkipmanTalepKararVerenResponse(
                kararVeren.getAdi(),
                kararVeren.getSoyadi()
        );
    }

    private EkipmanTalepZimmetResponse zimmetResponse(ZimmetlemeBilgileri zimmet) {
        if (zimmet == null) {
            return null;
        }
        return new EkipmanTalepZimmetResponse(
                zimmet.getZimmetId(),
                zimmet.getEkipmanId().getEkipmanId(),
                zimmet.getEkipmanId().getMarka(),
                zimmet.getEkipmanId().getModel(),
                zimmet.getEkipmanId().getSeriNo(),
                zimmet.getTeslimTarihi(),
                zimmet.getIadeTarihi()
        );
    }

    private void requestDogrula(EkipmanTalepCreateRequest request) {
        if (request == null || request.ekipmanTipiId() == null
                || request.ekipmanTipiId() <= 0) {
            throw new GecersizIstekException("Ekipman tipi ID pozitif olmalıdır.");
        }
    }

    private void requestDogrula(EkipmanTalepUpdateRequest request) {
        if (request == null || request.ekipmanTipiId() == null
                || request.ekipmanTipiId() <= 0) {
            throw new GecersizIstekException("Ekipman tipi ID pozitif olmalıdır.");
        }
    }

    private String aciklamaNormalizeEt(String aciklama) {
        if (aciklama == null) {
            return null;
        }
        String normalize = aciklama.trim();
        if (normalize.isEmpty()) {
            return null;
        }
        if (normalize.length() > ACIKLAMA_UZUNLUGU) {
            throw new GecersizIstekException(
                    "Açıklama en fazla 255 karakter olmalıdır."
            );
        }
        return normalize;
    }

    private void sayfalamaDogrula(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GecersizIstekException("Geçersiz sayfalama bilgisi.");
        }
    }

    private void opsiyonelTipIdDogrula(Integer tipId) {
        if (tipId != null && tipId <= 0) {
            throw new GecersizIstekException("Ekipman tipi ID pozitif olmalıdır.");
        }
    }

    private String canonicalUuid(String deger) {
        if (deger == null) {
            throw new GecersizIstekException("Geçersiz ekipman talebi ID.");
        }
        try {
            String canonical = UUID.fromString(deger).toString();
            if (!canonical.equals(deger)) {
                throw new GecersizIstekException("Geçersiz ekipman talebi ID.");
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException("Geçersiz ekipman talebi ID.");
        }
    }
}
