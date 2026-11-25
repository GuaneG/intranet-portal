package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.request.IzinTalepCreateRequest;
import com.jforce.backend.model.dto.response.IzinTalepKararVerenResponse;
import com.jforce.backend.model.dto.response.IzinTalepResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.entity.IzinTuru;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.DurumTuruRepository;
import com.jforce.backend.repository.IzinBilgileriRepository;
import com.jforce.backend.repository.IzinTuruRepository;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class IzinTalepService {

    private static final String CAKISMA_MESAJI =
            "Seçilen tarih aralığında mevcut bir izin talebiniz bulunuyor.";
    private static final List<TalepDurum> AKTIF_TALEP_DURUMLARI = List.of(
            TalepDurum.ONAY_BEKLIYOR,
            TalepDurum.ONAYLANDI
    );

    private final PersonelRepository personelRepository;
    private final IzinAktoruYetkiService izinAktoruYetkiService;
    private final IzinTuruRepository izinTuruRepository;
    private final DurumTuruRepository durumTuruRepository;
    private final IsGunuHesaplamaService isGunuHesaplamaService;
    private final IzinBilgileriRepository izinBilgileriRepository;
    private final AuditService auditService;
    private final Clock clock;

    public IzinTalepService(
            PersonelRepository personelRepository,
            IzinAktoruYetkiService izinAktoruYetkiService,
            IzinTuruRepository izinTuruRepository,
            DurumTuruRepository durumTuruRepository,
            IsGunuHesaplamaService isGunuHesaplamaService,
            IzinBilgileriRepository izinBilgileriRepository,
            AuditService auditService,
            Clock clock
    ) {
        this.personelRepository = personelRepository;
        this.izinAktoruYetkiService = izinAktoruYetkiService;
        this.izinTuruRepository = izinTuruRepository;
        this.durumTuruRepository = durumTuruRepository;
        this.isGunuHesaplamaService = isGunuHesaplamaService;
        this.izinBilgileriRepository = izinBilgileriRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR')")
    public IzinTalepResponse talepOlustur(
            String aktorId,
            IzinTalepCreateRequest request
    ) {
        Personel aktor = personelRepository.findByIdForUpdate(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));
        izinAktoruYetkiService.talepIslemineUygunluguDogrula(aktor);

        String normalizeNot = requestiDogrulaVeNotuNormalizeEt(request);

        IzinTuru izinTuru = izinTuruRepository.findById(request.izinTuruId())
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin türü bulunamadı."
                ));
        DurumTuru durum = durumTuruRepository
                .findByDurumKodu(TalepDurum.ONAY_BEKLIYOR)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin durumu bulunamadı."
                ));
        BigDecimal isGunuSayisi = isGunuHesaplamaService
                .isGunuSayisiniHesapla(
                        request.baslangicTarihi(),
                        request.bitisTarihi()
                );
        if (isGunuSayisi.signum() <= 0) {
            throw new GecersizIstekException(
                    "İzin talebi en az bir iş günü içermelidir."
            );
        }

        cakismayiDogrula(
                aktor.getPersonelId(),
                request.baslangicTarihi(),
                request.bitisTarihi()
        );
        bakiyeyiDogrula(aktor, izinTuru, isGunuSayisi);

        IzinBilgileri izinTalebi = new IzinBilgileri();
        izinTalebi.setPersonelId(aktor);
        izinTalebi.setIzinTuruId(izinTuru);
        izinTalebi.setBaslangicTarihi(request.baslangicTarihi());
        izinTalebi.setBitisTarihi(request.bitisTarihi());
        izinTalebi.setIsGunuSayisi(isGunuSayisi);
        izinTalebi.setYoneticiyeNot(normalizeNot);
        izinTalebi.setIzinDurumId(durum);
        izinTalebi.setTalepTarihi(LocalDateTime.now(clock));

        IzinBilgileri kaydedilen = izinBilgileriRepository.saveAndFlush(
                izinTalebi
        );

        auditService.logKaydetOrtakTransaction(
                AuditEylem.IZIN_TALEBI_OLUSTURULDU,
                aktor,
                aktor.getKullaniciAdi(),
                "İzin talebi oluşturuldu.",
                aktor
        );

        return responseaDonustur(kaydedilen);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR')")
    public SayfaliResponse<IzinTalepResponse> kendiTalepleriniGetir(
            String aktorId,
            int page,
            int size,
            TalepDurum durum
    ) {
        Personel aktor = izinAktoruYetkiService.talepAktoruGetir(aktorId);
        Sort sort = Sort.by(Sort.Order.desc("talepTarihi"))
                .and(Sort.by(Sort.Order.desc("izinId")));
        Page<IzinBilgileri> talepler = izinBilgileriRepository
                .findKendiTalepleri(
                        aktor.getPersonelId(),
                        durum,
                        PageRequest.of(page, size, sort)
                );

        return new SayfaliResponse<>(
                talepler.getContent()
                        .stream()
                        .map(this::responseaDonustur)
                        .toList(),
                talepler.getNumber(),
                talepler.getSize(),
                talepler.getTotalElements(),
                talepler.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
    public void talebiIptalEt(String aktorId, String talepId) {
        Personel aktor = izinAktoruYetkiService.iptalAktoruGetir(aktorId);
        String normalizeTalepId = talepIdDogrula(talepId);
        IzinBilgileri talep = izinBilgileriRepository
                .findByIdForUpdate(normalizeTalepId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin talebi bulunamadı."
                ));

        if (!talep.getPersonelId().getPersonelId().equals(
                aktor.getPersonelId()
        )) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }
        if (talep.getIzinDurumId().getDurumKodu()
                != TalepDurum.ONAY_BEKLIYOR) {
            throw new KaynakCakismasiException(
                    "Talep artık işlem beklemiyor."
            );
        }

        DurumTuru iptalEdildi = durumTuruRepository
                .findByDurumKodu(TalepDurum.IPTAL_EDILDI)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "İzin durumu bulunamadı."
                ));
        talep.setIzinDurumId(iptalEdildi);
        talep.setIslemYapanId(aktor);
        talep.setIslemTarihi(LocalDateTime.now(clock));
        talep.setKararNotu(null);
        izinBilgileriRepository.saveAndFlush(talep);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.IZIN_TALEBI_IPTAL_EDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "İzin talebi iptal edildi.",
                aktor
        );
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

    private void cakismayiDogrula(
            String personelId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        List<IzinBilgileri> adaylar = izinBilgileriRepository
                .findCakismaAdaylari(
                        personelId,
                        AKTIF_TALEP_DURUMLARI,
                        baslangicTarihi,
                        bitisTarihi
                );

        for (IzinBilgileri aday : adaylar) {
            LocalDate ortakBaslangic = baslangicTarihi.isAfter(
                    aday.getBaslangicTarihi()
            ) ? baslangicTarihi : aday.getBaslangicTarihi();
            LocalDate ortakBitis = bitisTarihi.isBefore(
                    aday.getBitisTarihi()
            ) ? bitisTarihi : aday.getBitisTarihi();

            if (isGunuHesaplamaService
                    .isGunuSayisiniHesapla(ortakBaslangic, ortakBitis)
                    .signum() > 0) {
                throw new KaynakCakismasiException(CAKISMA_MESAJI);
            }
        }
    }

    private void bakiyeyiDogrula(
            Personel aktor,
            IzinTuru izinTuru,
            BigDecimal yeniIsGunuSayisi
    ) {
        if (!izinTuru.isYillikHaktanDuserMi()) {
            return;
        }

        BigDecimal kullanilan = izinBilgileriRepository
                .yillikHaktanDusenToplam(
                        aktor.getPersonelId(),
                        List.of(TalepDurum.ONAYLANDI)
                );
        BigDecimal rezerve = izinBilgileriRepository
                .yillikHaktanDusenToplam(
                        aktor.getPersonelId(),
                        List.of(TalepDurum.ONAY_BEKLIYOR)
                );
        BigDecimal kullanilabilir = BigDecimal
                .valueOf(aktor.getYillikIzinHakki())
                .subtract(kullanilan)
                .subtract(rezerve);

        if (yeniIsGunuSayisi.compareTo(kullanilabilir) > 0) {
            throw new GecersizIstekException("Yetersiz izin bakiyesi.");
        }
    }

    private String requestiDogrulaVeNotuNormalizeEt(
            IzinTalepCreateRequest request
    ) {
        LocalDate baslangicTarihi = request.baslangicTarihi();
        LocalDate bitisTarihi = request.bitisTarihi();
        LocalDate bugun = LocalDate.now(clock);

        if (baslangicTarihi == null
                || bitisTarihi == null
                || baslangicTarihi.isBefore(bugun)
                || bitisTarihi.isBefore(bugun)
                || bitisTarihi.isBefore(baslangicTarihi)) {
            throw new GecersizIstekException("Geçersiz tarih aralığı.");
        }

        if (ChronoUnit.DAYS.between(baslangicTarihi, bitisTarihi) > 365) {
            throw new GecersizIstekException(
                    "İzin talebi en fazla 366 gün olabilir."
            );
        }

        String normalizeNot = request.yoneticiyeNot() == null
                ? null
                : request.yoneticiyeNot().trim();

        if (normalizeNot != null && normalizeNot.length() > 1000) {
            throw new GecersizIstekException(
                    "Yönetici notu en fazla 1000 karakter olabilir."
            );
        }

        return normalizeNot == null || normalizeNot.isEmpty()
                ? null
                : normalizeNot;
    }

    private IzinTalepResponse responseaDonustur(IzinBilgileri izinTalebi) {
        TalepDurum durum = izinTalebi.getIzinDurumId().getDurumKodu();
        Personel kararVeren = izinTalebi.getIslemYapanId();
        IzinTalepKararVerenResponse kararVerenResponse = kararVeren == null
                ? null
                : new IzinTalepKararVerenResponse(
                        kararVeren.getAdi(),
                        kararVeren.getSoyadi()
                );

        return new IzinTalepResponse(
                izinTalebi.getIzinId(),
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
