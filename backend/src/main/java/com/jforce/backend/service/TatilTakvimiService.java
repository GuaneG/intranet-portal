package com.jforce.backend.service;

import com.jforce.backend.config.RezervasyonAyarlari;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.model.dto.request.TatilKaydetRequest;
import com.jforce.backend.model.dto.response.TatilResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.repository.TatilTakvimiKilitRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jforce.backend.model.enums.AuditEylem;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class TatilTakvimiService {

    private final PersonelRepository personelRepository;
    private final TatilRepository tatilRepository;
    private final TatilTakvimiKilitRepository tatilTakvimiKilitRepository;
    private final OdaRepository odaRepository;
    private final RezervasyonRepository rezervasyonRepository;
    private final RezervasyonAyarlari rezervasyonAyarlari;
    private final Clock clock;
    private final AuditService auditService;

    public TatilTakvimiService(
            PersonelRepository personelRepository,
            TatilRepository tatilRepository,
            TatilTakvimiKilitRepository tatilTakvimiKilitRepository,
            OdaRepository odaRepository,
            RezervasyonRepository rezervasyonRepository,
            RezervasyonAyarlari rezervasyonAyarlari,
            Clock clock,
            AuditService auditService
    ) {
        this.personelRepository = personelRepository;
        this.tatilRepository = tatilRepository;
        this.tatilTakvimiKilitRepository = tatilTakvimiKilitRepository;
        this.odaRepository = odaRepository;
        this.rezervasyonRepository = rezervasyonRepository;
        this.rezervasyonAyarlari = rezervasyonAyarlari;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void tatilGuncelle(
            String aktorId,
            Integer tatilId,
            TatilKaydetRequest request
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        tatilYonetimYetkisiniDogrula(aktor);

        if (tatilId == null || tatilId <= 0) {
            throw new GecersizIstekException(
                    "Tatil ID pozitif olmalıdır."
            );
        }

        TarihAraligi aralik = tatilAraliginiDogrula(request);
        takvimiKilitle();

        Tatil tatil = tatilRepository
                .findById(tatilId)
                .orElseThrow(() ->
                        new KaynakBulunamadiException(
                                "Tatil bulunamadı."
                        )
                );

        if (tatil.getTarih().isBefore(LocalDate.now(clock))) {
            throw new KaynakCakismasiException(
                    "Geçmiş tatil kaydı güncellenemez."
            );
        }

        if (aralik.baslangic().isBefore(LocalDate.now(clock))) {
            throw new GecersizIstekException(
                    "Tatil tarihi geçmişte olamaz."
            );
        }

        String normalizeAd = request.ad().trim();

        boolean ayniBilgiler =
                tatil.getAd().equals(normalizeAd)
                        && tatil.getTarih().equals(aralik.baslangic())
                        && tatil.getBitisTarihi().equals(aralik.bitis())
                        && tatil.getKategori() == request.kategori()
                        && tatil.getGunSuresi() == request.gunSuresi();

        if (ayniBilgiler) {
            return;
        }

        if (!tatilRepository.cakisanBaskaTatilleriKilitleyerekBul(
                tatilId,
                aralik.baslangic(),
                aralik.bitis()
        ).isEmpty()) {
            throw new KaynakCakismasiException(
                    "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
            );
        }

        aktifRezervasyonCakismasiniDogrula(
                aralik,
                request.gunSuresi()
        );

        tatil.setAd(normalizeAd);
        tatil.setTarih(aralik.baslangic());
        tatil.setBitisTarihi(aralik.bitis());
        tatil.setKategori(request.kategori());
        tatil.setGunSuresi(request.gunSuresi());

        tatiliGuvenliKaydet(tatil);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.TATIL_GUNCELLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Tatil güncellendi.",
                null
        );
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public void tatilSil(
            String aktorId,
            Integer tatilId
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        tatilYonetimYetkisiniDogrula(aktor);

        if (tatilId == null || tatilId <= 0) {
            throw new GecersizIstekException(
                    "Tatil ID pozitif olmalıdır."
            );
        }

        takvimiKilitle();

        Tatil tatil = tatilRepository
                .findById(tatilId)
                .orElseThrow(() ->
                        new KaynakBulunamadiException(
                                "Tatil bulunamadı."
                        )
                );
        if (tatil.getTarih().isBefore(LocalDate.now(clock))) {
            throw new KaynakCakismasiException(
                    "Geçmiş tatil kaydı silinemez."
            );
        }

        tatilRepository.delete(tatil);
        tatilRepository.flush();

        auditService.logKaydetOrtakTransaction(
                AuditEylem.TATIL_SILINDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Tatil silindi.",
                null
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<TatilResponse> tatilleriListele(
            String aktorId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        aktifAktoruGetir(aktorId);

        tarihAraliginiDogrula(
                baslangicTarihi,
                bitisTarihi
        );

        return tatilRepository
                .findAllKesisen(
                        baslangicTarihi,
                        bitisTarihi
                )
                .stream()
                .map(this::responseOlustur)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    public TatilResponse tatilOlustur(
            String aktorId,
            TatilKaydetRequest request
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        tatilYonetimYetkisiniDogrula(aktor);

        TarihAraligi aralik = tatilAraliginiDogrula(request);

        if (aralik.baslangic().isBefore(LocalDate.now(clock))) {
            throw new GecersizIstekException(
                    "Geçmiş tarih için tatil oluşturulamaz."
            );
        }

        takvimiKilitle();

        if (!tatilRepository.cakisanTatilleriKilitleyerekBul(
                aralik.baslangic(),
                aralik.bitis()
        ).isEmpty()) {
            throw new KaynakCakismasiException(
                    "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
            );
        }

        aktifRezervasyonCakismasiniDogrula(
                aralik,
                request.gunSuresi()
        );

        Tatil tatil = new Tatil();
        tatil.setAd(request.ad().trim());
        tatil.setTarih(aralik.baslangic());
        tatil.setBitisTarihi(aralik.bitis());
        tatil.setKategori(request.kategori());
        tatil.setGunSuresi(request.gunSuresi());

        Tatil kaydedilen = tatiliGuvenliKaydet(tatil);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.TATIL_EKLENDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Tatil oluşturuldu.",
                null
        );

        return responseOlustur(kaydedilen);
    }

    private Personel aktifAktoruGetir(String aktorId) {
        Personel aktor = personelRepository
                .findById(aktorId)
                .orElseThrow(() ->
                        new KaynakBulunamadiException(
                                "Personel bulunamadı."
                        )
                );

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel tatil işlemi yapamaz."
            );
        }

        return aktor;
    }

    private Tatil tatiliGuvenliKaydet(Tatil tatil) {
        try {
            return tatilRepository.saveAndFlush(tatil);
        } catch (DataIntegrityViolationException exception) {
            throw new KaynakCakismasiException(
                    "Seçilen tarih aralığı mevcut bir tatille çakışıyor."
            );
        }
    }

    private void takvimiKilitle() {
        tatilTakvimiKilitRepository.takvimiKilitle()
                .orElseThrow(() -> new IllegalStateException(
                        "Tatil takvimi kilit satırı bulunamadı."
                ));
    }

    private void aktifRezervasyonCakismasiniDogrula(
            TarihAraligi aralik,
            TatilGunSuresi gunSuresi
    ) {
        odaRepository.findAllForUpdate();

        boolean cakismaVar = rezervasyonRepository.existsAktifTatilCakismasi(
                aralik.baslangic(),
                aralik.bitis(),
                gunSuresi == TatilGunSuresi.TAM_GUN,
                rezervasyonAyarlari.getYarimGunBitisSaati()
        );

        if (cakismaVar) {
            throw new KaynakCakismasiException(
                    "Seçilen tarih aralığında tatil saatleriyle "
                            + "çakışan aktif rezervasyonlar bulunuyor. "
                            + "Tatili kaydetmek için önce bu "
                            + "rezervasyonları iptal edin."
            );
        }
    }

    private TarihAraligi tatilAraliginiDogrula(
            TatilKaydetRequest request
    ) {
        LocalDate baslangic = request.tarih();

        if (baslangic == null) {
            throw new GecersizIstekException(
                    "Tatil başlangıç tarihi zorunludur."
            );
        }

        LocalDate bitis = request.bitisTarihi() == null
                ? baslangic
                : request.bitisTarihi();

        if (bitis.isBefore(baslangic)) {
            throw new GecersizIstekException(
                    "Tatil bitiş tarihi başlangıç tarihinden önce olamaz."
            );
        }

        if (ChronoUnit.DAYS.between(baslangic, bitis) > 365) {
            throw new GecersizIstekException(
                    "Tatil grubu en fazla 366 gün olabilir."
            );
        }

        return new TarihAraligi(baslangic, bitis);
    }

    private void tatilYonetimYetkisiniDogrula(Personel aktor) {
        RolAdi rolAdi = aktor.getRol().getRolAdi();

        if (rolAdi != RolAdi.HR && rolAdi != RolAdi.ADMIN) {

            throw new AccessDeniedException("Yalnız HR veya Admin tatil yönetebilir.");
        }
    }

    private void tarihAraliginiDogrula(
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        if (baslangicTarihi == null || bitisTarihi == null) {
            throw new GecersizIstekException(
                    "Başlangıç ve bitiş tarihleri zorunludur."
            );
        }

        if (bitisTarihi.isBefore(baslangicTarihi)) {
            throw new GecersizIstekException(
                    "Bitiş tarihi başlangıç tarihinden önce olamaz."
            );
        }

        long gunFarki = ChronoUnit.DAYS.between(
                baslangicTarihi,
                bitisTarihi
        );

        if (gunFarki > 365) {
            throw new GecersizIstekException(
                    "Tarih aralığı en fazla 366 gün olabilir."
            );
        }
    }

    private TatilResponse responseOlustur(Tatil tatil) {
        return new TatilResponse(
                tatil.getTatilId(),
                tatil.getAd(),
                tatil.getTarih(),
                tatil.getBitisTarihi(),
                tatil.getKategori(),
                tatil.getGunSuresi()
        );
    }

    private record TarihAraligi(
            LocalDate baslangic,
            LocalDate bitis
    ) {
    }
}
