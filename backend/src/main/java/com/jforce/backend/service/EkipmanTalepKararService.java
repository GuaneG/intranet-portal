package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.EkipmanTalepOnayRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepRedRequest;
import com.jforce.backend.model.dto.response.EkipmanTalepKararItemResponse;
import com.jforce.backend.model.dto.response.EkipmanTalepKararVerenResponse;
import com.jforce.backend.model.dto.response.EkipmanTalepSahibiResponse;
import com.jforce.backend.model.dto.response.EkipmanTalepZimmetResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.dto.response.UygunEkipmanResponse;
import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.EkipmanBilgileriRepository;
import com.jforce.backend.repository.EkipmanTalepRepository;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.ZimmetlemeBilgileriRepository;
import org.springframework.dao.DataIntegrityViolationException;
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
public class EkipmanTalepKararService {

    private final EkipmanAktoruYetkiService aktorYetkiService;
    private final EkipmanTalepRepository talepRepository;
    private final EkipmanBilgileriRepository ekipmanRepository;
    private final ZimmetlemeBilgileriRepository zimmetRepository;
    private final PersonelRepository personelRepository;
    private final DurumTuruRepository durumTuruRepository;
    private final AuditService auditService;
    private final Clock clock;

    public EkipmanTalepKararService(
            EkipmanAktoruYetkiService aktorYetkiService,
            EkipmanTalepRepository talepRepository,
            EkipmanBilgileriRepository ekipmanRepository,
            ZimmetlemeBilgileriRepository zimmetRepository,
            PersonelRepository personelRepository,
            DurumTuruRepository durumTuruRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.aktorYetkiService = aktorYetkiService;
        this.talepRepository = talepRepository;
        this.ekipmanRepository = ekipmanRepository;
        this.zimmetRepository = zimmetRepository;
        this.personelRepository = personelRepository;
        this.durumTuruRepository = durumTuruRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('YONETICI')")
    public SayfaliResponse<EkipmanTalepKararItemResponse> takimTalepleriniGetir(
            String aktorId,
            TalepDurum durum,
            Integer ekipmanTipiId,
            int page,
            int size
    ) {
        Personel yonetici = aktorYetkiService.yoneticiAktoruGetir(aktorId);
        filtreleriDogrula(ekipmanTipiId, page, size);
        Page<EkipmanTalep> sonuc = talepRepository.findTakimTalepleri(
                yonetici.getPersonelId(), durum, ekipmanTipiId, PageRequest.of(page, size)
        );
        return talepSayfasiOlustur(sonuc);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<EkipmanTalepKararItemResponse> adminTalepleriniGetir(
            String aktorId,
            TalepDurum durum,
            Integer ekipmanTipiId,
            String personelId,
            int page,
            int size
    ) {
        aktorYetkiService.adminAktoruGetir(aktorId);
        filtreleriDogrula(ekipmanTipiId, page, size);
        String dogrulanmisPersonelId = personelId == null
                ? null
                : canonicalUuid(personelId, "Geçersiz personel ID.");
        Page<EkipmanTalep> sonuc = talepRepository.findAdminTalepleri(
                durum, ekipmanTipiId, dogrulanmisPersonelId, PageRequest.of(page, size)
        );
        return talepSayfasiOlustur(sonuc);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
    public SayfaliResponse<UygunEkipmanResponse> uygunEkipmanlariGetir(
            String aktorId,
            String talepId,
            int page,
            int size
    ) {
        Personel aktor = aktorYetkiService.kararAktoruGetir(aktorId);
        sayfalamaDogrula(page, size);
        String dogrulanmisTalepId = canonicalUuid(talepId, "Geçersiz ekipman talebi ID.");
        EkipmanTalep talep = talepRepository.findById(dogrulanmisTalepId)
                .orElseThrow(this::talepBulunamadi);
        kapsamDogrula(aktor, talep.getPersonel());

        Page<EkipmanBilgileri> sonuc = ekipmanRepository.findUygunCihazlar(
                talep.getEkipmanTipi().getEkipmanTipiId(),
                EkipmanDurum.DEPODA,
                PageRequest.of(page, size)
        );
        return new SayfaliResponse<>(
                sonuc.getContent().stream().map(this::uygunEkipmanResponse).toList(),
                sonuc.getNumber(),
                sonuc.getSize(),
                sonuc.getTotalElements(),
                sonuc.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
    public void talebiOnayla(
            String aktorId,
            String talepId,
            EkipmanTalepOnayRequest request
    ) {
        Personel aktor = aktorYetkiService.kararAktoruGetir(aktorId);
        String dogrulanmisTalepId = canonicalUuid(
                talepId, "Geçersiz ekipman talebi ID."
        );
        String ownerId = talepRepository.findOwnerIdByTalepId(dogrulanmisTalepId)
                .orElseThrow(this::talepBulunamadi);
        Personel owner = personelRepository.findByIdForUpdate(ownerId)
                .orElseThrow(this::talepBulunamadi);
        EkipmanTalep talep = kilitliTalebiYenidenGetir(
                dogrulanmisTalepId, ownerId
        );

        kapsamDogrula(aktor, owner);
        bekleyenTalepVeAktifOwnerDogrula(talep, owner);

        String ekipmanId = canonicalUuid(
                request == null ? null : request.ekipmanId(),
                "Geçersiz ekipman ID."
        );
        EkipmanBilgileri ekipman = ekipmanRepository.findByIdForUpdate(ekipmanId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Ekipman bulunamadı."
                ));
        secilenCihaziDogrula(talep, ekipman);
        if (zimmetRepository.existsByEkipmanIdAndIadeTarihiIsNull(ekipmanId)) {
            throw ekipmanUygunDegil();
        }

        LocalDateTime islemZamani = LocalDateTime.now(clock);
        ZimmetlemeBilgileri zimmet = new ZimmetlemeBilgileri();
        zimmet.setEkipmanId(ekipman);
        zimmet.setPersonelId(owner);
        zimmet.setTalep(talep);
        zimmet.setTeslimTarihi(islemZamani);
        try {
            zimmetRepository.saveAndFlush(zimmet);
        } catch (DataIntegrityViolationException exception) {
            throw ekipmanUygunDegil();
        }

        ekipman.setEkipmanDurum(EkipmanDurum.PERSONELDE);
        talep.setTalepDurum(durumGetir(TalepDurum.ONAYLANDI));
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(islemZamani);
        talep.setKararNotu(null);
        ekipmanRepository.saveAndFlush(ekipman);
        talepRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TALEBI_ONAYLANDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Ekipman talebi onaylandı.",
                owner
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
    public void talebiReddet(
            String aktorId,
            String talepId,
            EkipmanTalepRedRequest request
    ) {
        Personel aktor = aktorYetkiService.kararAktoruGetir(aktorId);
        String dogrulanmisTalepId = canonicalUuid(
                talepId, "Geçersiz ekipman talebi ID."
        );
        String ownerId = talepRepository.findOwnerIdByTalepId(dogrulanmisTalepId)
                .orElseThrow(this::talepBulunamadi);
        Personel owner = personelRepository.findByIdForUpdate(ownerId)
                .orElseThrow(this::talepBulunamadi);
        EkipmanTalep talep = kilitliTalebiYenidenGetir(
                dogrulanmisTalepId, ownerId
        );

        kapsamDogrula(aktor, owner);
        bekleyenTalepVeAktifOwnerDogrula(talep, owner);
        String kararNotu = kararNotunuNormalizeEt(request);

        talep.setTalepDurum(durumGetir(TalepDurum.REDDEDILDI));
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(LocalDateTime.now(clock));
        talep.setKararNotu(kararNotu);
        talepRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.EKIPMAN_TALEBI_REDDEDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Ekipman talebi reddedildi.",
                owner
        );
    }

    private EkipmanTalep kilitliTalebiYenidenGetir(
            String talepId,
            String beklenenOwnerId
    ) {
        EkipmanTalep talep = talepRepository.findByIdForUpdate(talepId)
                .orElseThrow(this::talepBulunamadi);
        if (!Objects.equals(
                beklenenOwnerId, talep.getPersonel().getPersonelId()
        )) {
            throw talepBulunamadi();
        }
        return talep;
    }

    private void bekleyenTalepVeAktifOwnerDogrula(
            EkipmanTalep talep,
            Personel owner
    ) {
        if (!Boolean.TRUE.equals(owner.getCalisiyorMu())) {
            throw new KaynakCakismasiException(
                    "Talep sahibi artık aktif değil."
            );
        }
        if (talep.getTalepDurum().getDurumKodu() != TalepDurum.ONAY_BEKLIYOR) {
            throw new KaynakCakismasiException(
                    "Talep artık işlem beklemiyor."
            );
        }
    }

    private void secilenCihaziDogrula(
            EkipmanTalep talep,
            EkipmanBilgileri ekipman
    ) {
        boolean ayniTip = Objects.equals(
                talep.getEkipmanTipi().getEkipmanTipiId(),
                ekipman.getEkipmanTipiId().getEkipmanTipiId()
        );
        if (!ayniTip || ekipman.getEkipmanDurum() != EkipmanDurum.DEPODA) {
            throw ekipmanUygunDegil();
        }
    }

    private String kararNotunuNormalizeEt(EkipmanTalepRedRequest request) {
        if (request == null || request.kararNotu() == null) {
            throw new GecersizIstekException("Geçersiz istek");
        }
        String kararNotu = request.kararNotu().trim();
        if (kararNotu.isEmpty() || kararNotu.length() > 500) {
            throw new GecersizIstekException("Geçersiz istek");
        }
        return kararNotu;
    }

    private DurumTuru durumGetir(TalepDurum durum) {
        return durumTuruRepository.findByDurumKodu(durum)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Talep durumu bulunamadı."
                ));
    }

    private KaynakCakismasiException ekipmanUygunDegil() {
        return new KaynakCakismasiException(
                "Ekipman artık zimmete uygun değil."
        );
    }

    private void kapsamDogrula(Personel aktor, Personel owner) {
        boolean kapsamda;
        if (aktor.getRol().getRolAdi() == RolAdi.ADMIN) {
            kapsamda = !Objects.equals(aktor.getPersonelId(), owner.getPersonelId());
        } else {
            kapsamda = !Objects.equals(aktor.getPersonelId(), owner.getPersonelId())
                    && Boolean.TRUE.equals(owner.getCalisiyorMu())
                    && owner.getYonetici() != null
                    && Objects.equals(owner.getYonetici().getPersonelId(), aktor.getPersonelId());
        }
        if (!kapsamda) {
            throw talepBulunamadi();
        }
    }

    private SayfaliResponse<EkipmanTalepKararItemResponse> talepSayfasiOlustur(
            Page<EkipmanTalep> sonuc
    ) {
        Map<String, ZimmetlemeBilgileri> zimmetler = zimmetleriGetir(sonuc.getContent());
        return new SayfaliResponse<>(
                sonuc.getContent().stream()
                        .map(talep -> talepResponse(
                                talep, zimmetler.get(talep.getTalepId())))
                        .toList(),
                sonuc.getNumber(),
                sonuc.getSize(),
                sonuc.getTotalElements(),
                sonuc.getTotalPages()
        );
    }

    private Map<String, ZimmetlemeBilgileri> zimmetleriGetir(
            Collection<EkipmanTalep> talepler
    ) {
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

    private EkipmanTalepKararItemResponse talepResponse(
            EkipmanTalep talep,
            ZimmetlemeBilgileri zimmet
    ) {
        Personel owner = talep.getPersonel();
        TalepDurum durum = talep.getTalepDurum().getDurumKodu();
        return new EkipmanTalepKararItemResponse(
                talep.getTalepId(),
                new EkipmanTalepSahibiResponse(
                        owner.getAdi(),
                        owner.getSoyadi(),
                        owner.getDepartman().getDepartmanAdi(),
                        owner.getProfilFoto(),
                        Boolean.TRUE.equals(owner.getCalisiyorMu())
                ),
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
                kararVeren.getAdi(), kararVeren.getSoyadi()
        );
    }

    private EkipmanTalepZimmetResponse zimmetResponse(ZimmetlemeBilgileri zimmet) {
        if (zimmet == null) {
            return null;
        }
        EkipmanBilgileri ekipman = zimmet.getEkipmanId();
        return new EkipmanTalepZimmetResponse(
                zimmet.getZimmetId(),
                ekipman.getEkipmanId(),
                ekipman.getMarka(),
                ekipman.getModel(),
                ekipman.getSeriNo(),
                zimmet.getTeslimTarihi(),
                zimmet.getIadeTarihi()
        );
    }

    private UygunEkipmanResponse uygunEkipmanResponse(EkipmanBilgileri ekipman) {
        return new UygunEkipmanResponse(
                ekipman.getEkipmanId(),
                ekipman.getMarka(),
                ekipman.getModel(),
                ekipman.getSeriNo()
        );
    }

    private void filtreleriDogrula(Integer ekipmanTipiId, int page, int size) {
        sayfalamaDogrula(page, size);
        if (ekipmanTipiId != null && ekipmanTipiId <= 0) {
            throw new GecersizIstekException("Ekipman tipi ID pozitif olmalıdır.");
        }
    }

    private void sayfalamaDogrula(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new GecersizIstekException("Geçersiz sayfalama bilgisi.");
        }
    }

    private String canonicalUuid(String deger, String mesaj) {
        if (deger == null) {
            throw new GecersizIstekException(mesaj);
        }
        try {
            String canonical = UUID.fromString(deger).toString();
            if (!canonical.equals(deger)) {
                throw new GecersizIstekException(mesaj);
            }
            return canonical;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException(mesaj);
        }
    }

    private KaynakBulunamadiException talepBulunamadi() {
        return new KaynakBulunamadiException("Ekipman talebi bulunamadı.");
    }
}
