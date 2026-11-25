package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.IzinTalepRedRequest;
import com.jforce.backend.model.dto.response.IzinTalepKararItemResponse;
import com.jforce.backend.model.dto.response.IzinTalepKararVerenResponse;
import com.jforce.backend.model.dto.response.IzinTalepSahibiResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IzinTalepKararService {

    private final IzinBilgileriRepository izinBilgileriRepository;
    private final IzinAktoruYetkiService izinAktoruYetkiService;
    private final PersonelRepository personelRepository;
    private final DurumTuruRepository durumTuruRepository;
    private final AuditService auditService;
    private final Clock clock;

    public IzinTalepKararService(
            IzinBilgileriRepository izinBilgileriRepository,
            IzinAktoruYetkiService izinAktoruYetkiService,
            PersonelRepository personelRepository,
            DurumTuruRepository durumTuruRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.izinBilgileriRepository = izinBilgileriRepository;
        this.izinAktoruYetkiService = izinAktoruYetkiService;
        this.personelRepository = personelRepository;
        this.durumTuruRepository = durumTuruRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('YONETICI')")
    public SayfaliResponse<IzinTalepKararItemResponse>
    takimTalepleriniGetir(
            String aktorId,
            int page,
            int size,
            TalepDurum durum
    ) {
        Personel aktor = izinAktoruYetkiService.kararAktoruGetir(aktorId);
        Page<IzinBilgileri> talepler = izinBilgileriRepository
                .findTakimKararTalepleri(
                        aktor.getPersonelId(),
                        durum,
                        pageRequest(page, size)
                );
        return sayfayaDonustur(talepler);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<IzinTalepKararItemResponse>
    adminTalepleriniGetir(
            String aktorId,
            int page,
            int size,
            TalepDurum durum
    ) {
        izinAktoruYetkiService.kararAktoruGetir(aktorId);
        Page<IzinBilgileri> talepler = izinBilgileriRepository
                .findAdminKararTalepleri(
                        durum,
                        pageRequest(page, size)
                );
        return sayfayaDonustur(talepler);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
    public void talebiOnayla(String aktorId, String talepId) {
        Personel aktor = izinAktoruYetkiService.kararAktoruGetir(aktorId);
        String normalizeTalepId = talepIdDogrula(talepId);
        IzinBilgileri talep = izinBilgileriRepository
                .findByIdForUpdate(normalizeTalepId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin talebi bulunamadı."
                ));
        Personel owner = personelRepository
                .findByIdForUpdate(
                        talep.getPersonelId().getPersonelId()
                )
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));

        kararYetkisiniDogrula(aktor, owner);
        bekleyenDurumuDogrula(talep);
        onayTarihiniDogrula(talep);
        onayBakiyesiniDogrula(owner, talep);

        DurumTuru onaylandi = durumTuruRepository
                .findByDurumKodu(TalepDurum.ONAYLANDI)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin durumu bulunamadı."
                ));
        talep.setIzinDurumId(onaylandi);
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(LocalDateTime.now(clock));
        talep.setKararNotu(null);
        izinBilgileriRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.IZIN_TALEBI_ONAYLANDI,
                aktor,
                aktor.getKullaniciAdi(),
                "İzin talebi onaylandı.",
                owner
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('YONETICI', 'ADMIN')")
    public void talebiReddet(
            String aktorId,
            String talepId,
            IzinTalepRedRequest request
    ) {
        Personel aktor = izinAktoruYetkiService.kararAktoruGetir(aktorId);
        String normalizeTalepId = talepIdDogrula(talepId);
        IzinBilgileri talep = izinBilgileriRepository
                .findByIdForUpdate(normalizeTalepId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin talebi bulunamadı."
                ));
        Personel owner = personelRepository
                .findByIdForUpdate(
                        talep.getPersonelId().getPersonelId()
                )
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));

        kararYetkisiniDogrula(aktor, owner);
        bekleyenDurumuDogrula(talep);
        String kararNotu = kararNotunuNormalizeEt(request);

        DurumTuru reddedildi = durumTuruRepository
                .findByDurumKodu(TalepDurum.REDDEDILDI)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin durumu bulunamadı."
                ));
        talep.setIzinDurumId(reddedildi);
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(LocalDateTime.now(clock));
        talep.setKararNotu(kararNotu);
        izinBilgileriRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.IZIN_TALEBI_REDDEDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "İzin talebi reddedildi.",
                owner
        );
    }

    private String kararNotunuNormalizeEt(IzinTalepRedRequest request) {
        if (request == null || request.kararNotu() == null) {
            throw new GecersizIstekException("Geçersiz istek");
        }

        String kararNotu = request.kararNotu().trim();
        if (kararNotu.isEmpty() || kararNotu.length() > 500) {
            throw new GecersizIstekException("Geçersiz istek");
        }
        return kararNotu;
    }

    private String talepIdDogrula(String talepId) {
        if (talepId == null) {
            throw new GecersizIstekException("Geçersiz istek");
        }

        try {
            UUID parsed = UUID.fromString(talepId);
            String normalize = parsed.toString();
            if (!normalize.equals(talepId.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("canonical UUID değil");
            }
            return normalize;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException("Geçersiz istek");
        }
    }

    private void kararYetkisiniDogrula(Personel aktor, Personel owner) {
        if (aktor.getRol().getRolAdi() == RolAdi.ADMIN) {
            return;
        }

        Personel ownerYonetici = owner.getYonetici();
        boolean dogrudanAktifCalisan = Boolean.TRUE.equals(
                owner.getCalisiyorMu()
        ) && ownerYonetici != null
                && ownerYonetici.getPersonelId().equals(
                        aktor.getPersonelId()
                );

        if (!dogrudanAktifCalisan) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }
    }

    private void bekleyenDurumuDogrula(IzinBilgileri talep) {
        if (talep.getIzinDurumId().getDurumKodu()
                != TalepDurum.ONAY_BEKLIYOR) {
            throw new KaynakCakismasiException(
                    "Talep artık işlem beklemiyor."
            );
        }
    }

    private void onayTarihiniDogrula(IzinBilgileri talep) {
        if (talep.getBaslangicTarihi().isBefore(LocalDate.now(clock))) {
            throw new KaynakCakismasiException(
                    "Başlangıç tarihi geçmiş izin talebi onaylanamaz."
            );
        }
    }

    private void onayBakiyesiniDogrula(
            Personel owner,
            IzinBilgileri talep
    ) {
        if (!talep.getIzinTuruId().isYillikHaktanDuserMi()) {
            return;
        }

        BigDecimal kullanilanVeBekleyen = izinBilgileriRepository
                .yillikHaktanDusenToplam(
                        owner.getPersonelId(),
                        List.of(
                                TalepDurum.ONAYLANDI,
                                TalepDurum.ONAY_BEKLIYOR
                        )
                );
        BigDecimal hak = BigDecimal.valueOf(
                owner.getYillikIzinHakki()
        );
        if (kullanilanVeBekleyen.compareTo(hak) > 0) {
            throw new KaynakCakismasiException(
                    "İzin bakiyesi artık yeterli değil."
            );
        }
    }

    private PageRequest pageRequest(int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("talepTarihi"))
                .and(Sort.by(Sort.Order.desc("izinId")));
        return PageRequest.of(page, size, sort);
    }

    private SayfaliResponse<IzinTalepKararItemResponse> sayfayaDonustur(
            Page<IzinBilgileri> talepler
    ) {
        return new SayfaliResponse<>(
                talepler.getContent().stream()
                        .map(this::responseaDonustur)
                        .toList(),
                talepler.getNumber(),
                talepler.getSize(),
                talepler.getTotalElements(),
                talepler.getTotalPages()
        );
    }

    private IzinTalepKararItemResponse responseaDonustur(
            IzinBilgileri izinTalebi
    ) {
        Personel talepSahibi = izinTalebi.getPersonelId();
        Personel kararVeren = izinTalebi.getIslemYapanId();
        TalepDurum durum = izinTalebi.getIzinDurumId().getDurumKodu();

        IzinTalepSahibiResponse talepSahibiResponse =
                new IzinTalepSahibiResponse(
                        talepSahibi.getAdi(),
                        talepSahibi.getSoyadi(),
                        talepSahibi.getDepartman().getDepartmanAdi(),
                        talepSahibi.getProfilFoto(),
                        Boolean.TRUE.equals(talepSahibi.getCalisiyorMu())
                );
        IzinTalepKararVerenResponse kararVerenResponse = kararVeren == null
                ? null
                : new IzinTalepKararVerenResponse(
                        kararVeren.getAdi(),
                        kararVeren.getSoyadi()
                );

        return new IzinTalepKararItemResponse(
                izinTalebi.getIzinId(),
                talepSahibiResponse,
                izinTalebi.getIzinTuruId().getIzinTuruAdi(),
                izinTalebi.getBaslangicTarihi(),
                izinTalebi.getBitisTarihi(),
                izinTalebi.getIsGunuSayisi(),
                izinTalebi.getYoneticiyeNot(),
                durum,
                durum.getGorunenAd(),
                izinTalebi.getTalepTarihi(),
                izinTalebi.getKararNotu(),
                izinTalebi.getIslemTarihi(),
                kararVerenResponse
        );
    }
}
