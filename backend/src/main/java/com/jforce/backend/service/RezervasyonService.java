package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakCakismasiException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.request.RezervasyonRequest;
import com.jforce.backend.model.dto.request.RezervasyonIptalRequest;
import com.jforce.backend.model.dto.response.OdaResponse;
import com.jforce.backend.model.dto.response.OdaDoluAralikResponse;
import com.jforce.backend.model.dto.response.OdaDolulukItem;
import com.jforce.backend.model.dto.response.RezervasyonOlusturanResponse;
import com.jforce.backend.model.dto.response.RezervasyonGecmisResponse;
import com.jforce.backend.model.dto.response.RezervasyonResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rezervasyon;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.model.enums.OdaAnlikDurum;
import com.jforce.backend.model.enums.RezervasyonDurum;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.OdaRepository;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RezervasyonRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RezervasyonService {

    private static final long MAKSIMUM_TAKVIM_ARALIGI_GUN = 365;

    private final OdaRepository odaRepository;
    private final RezervasyonRepository rezervasyonRepository;
    private final PersonelRepository personelRepository;
    private final RezervasyonZamanPolitikasi zamanPolitikasi;
    private final AuditService auditService;
    private final Clock clock;

    public RezervasyonService(
            OdaRepository odaRepository,
            RezervasyonRepository rezervasyonRepository,
            PersonelRepository personelRepository,
            RezervasyonZamanPolitikasi zamanPolitikasi,
            AuditService auditService,
            Clock clock
    ) {
        this.odaRepository = odaRepository;
        this.rezervasyonRepository = rezervasyonRepository;
        this.personelRepository = personelRepository;
        this.zamanPolitikasi = zamanPolitikasi;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OdaResponse> odalariGetir(String aktorId) {
        aktifAktoruGetir(aktorId);
        return odaRepository.findAllByOrderByOdaAdAscOdaIdAsc().stream()
                .map(oda -> new OdaResponse(
                        oda.getOdaId(),
                        oda.getOdaAd(),
                        oda.getOdaKapasite()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OdaDolulukItem> bugunkuOdaDoluluklariniGetir() {
        LocalDate bugun = LocalDate.now(clock);
        LocalTime simdi = LocalTime.now(clock);
        RezervasyonZamanPolitikasi.AcikGunBilgisi acikGun =
                zamanPolitikasi.acikGunBilgisi(bugun);
        Map<Integer, List<Rezervasyon>> odaRezervasyonlari =
                rezervasyonRepository.findBugunkuAktifRezervasyonlar(bugun)
                        .stream()
                        .collect(Collectors.groupingBy(
                                rezervasyon -> rezervasyon.getOda().getOdaId()
                        ));

        return odaRepository.findAllByOrderByOdaAdAscOdaIdAsc().stream()
                .map(oda -> {
                    List<Rezervasyon> rezervasyonlar = odaRezervasyonlari
                            .getOrDefault(oda.getOdaId(), List.of());
                    List<OdaDoluAralikResponse> doluAraliklar = rezervasyonlar
                            .stream()
                            .map(rezervasyon -> new OdaDoluAralikResponse(
                                    rezervasyon.getBaslangicSaat(),
                                    rezervasyon.getBitisSaat()
                            ))
                            .toList();
                    int doluSaat = rezervasyonlar.stream()
                            .mapToInt(rezervasyon ->
                                    rezervasyon.getBitisSaat()
                                            - rezervasyon.getBaslangicSaat()
                            )
                            .sum();
                    int mevcutDakika = simdi.getHour() * 60 + simdi.getMinute();
                    boolean acik = !acikGun.kapaliMi()
                            && mevcutDakika >= acikGun.baslangicSaat() * 60
                            && mevcutDakika < acikGun.bitisSaat() * 60;
                    boolean anlikDolu = rezervasyonlar.stream()
                            .anyMatch(rezervasyon ->
                                    rezervasyon.getBaslangicSaat()
                                            <= simdi.getHour()
                                    && rezervasyon.getBitisSaat()
                                            > simdi.getHour()
                            );
                    OdaAnlikDurum anlikDurum = !acik
                            ? OdaAnlikDurum.KAPALI
                            : anlikDolu
                                    ? OdaAnlikDurum.DOLU
                                    : OdaAnlikDurum.MUSAIT;

                    return new OdaDolulukItem(
                            oda.getOdaId(),
                            oda.getOdaAd(),
                            oda.getOdaKapasite(),
                            anlikDurum,
                            dolulukYuzdesi(doluSaat, acikGun.acikSaatSayisi()),
                            doluAraliklar
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RezervasyonResponse> takvimiGetir(
            String aktorId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi,
            Integer odaId
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        tarihAraliginiDogrula(baslangicTarihi, bitisTarihi);

        return rezervasyonRepository.findAktifTakvim(
                        baslangicTarihi,
                        bitisTarihi,
                        odaId
                ).stream()
                .map(rezervasyon -> responseOlustur(rezervasyon, aktor))
                .toList();
    }

    @Transactional
    public RezervasyonResponse rezervasyonOlustur(
            String aktorId,
            RezervasyonRequest request
    ) {
        Personel aktor = personelRepository.findByIdForUpdate(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
        aktifligiDogrula(aktor);

        String normalizeBaslik = request.baslik().trim();
        var oda = odaRepository.findByIdForUpdate(request.odaId())
                .orElseThrow(() -> new KaynakBulunamadiException("Oda bulunamadı."));

        zamanPolitikasi.olusturmaZamaniniDogrula(
                request.tarih(),
                request.baslangicSaat(),
                request.bitisSaat()
        );
        if (request.katilimciSayisi() > oda.getOdaKapasite()) {
            throw new GecersizIstekException(
                    "Katılımcı sayısı oda kapasitesini aşamaz."
            );
        }

        if (rezervasyonRepository.existsAktifOdaCakismasi(
                oda.getOdaId(),
                request.tarih(),
                request.baslangicSaat(),
                request.bitisSaat()
        )) {
            throw new KaynakCakismasiException(
                    "Seçilen oda bu saat aralığında dolu."
            );
        }

        if (rezervasyonRepository.existsAktifOwnerCakismasi(
                aktor.getPersonelId(),
                request.tarih(),
                request.baslangicSaat(),
                request.bitisSaat()
        )) {
            throw new KaynakCakismasiException(
                    "Bu saat aralığında başka bir rezervasyonunuz bulunuyor."
            );
        }

        Rezervasyon rezervasyon = new Rezervasyon();
        rezervasyon.setOlusturan(aktor);
        rezervasyon.setOda(oda);
        rezervasyon.setTarih(request.tarih());
        rezervasyon.setBaslangicSaat(request.baslangicSaat());
        rezervasyon.setBitisSaat(request.bitisSaat());
        rezervasyon.setBaslik(normalizeBaslik);
        rezervasyon.setKatilimciSayisi(request.katilimciSayisi());
        rezervasyon.setDurum(RezervasyonDurum.AKTIF);
        rezervasyon.setOlusturmaZamani(LocalDateTime.now(clock));

        Rezervasyon kaydedilen = rezervasyonRepository.saveAndFlush(rezervasyon);
        auditService.logKaydetOrtakTransaction(
                AuditEylem.REZERVASYON_OLUSTURULDU,
                aktor,
                aktor.getKullaniciAdi(),
                "Rezervasyon oluşturuldu.",
                aktor
        );
        return responseOlustur(kaydedilen, aktor);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
    public SayfaliResponse<RezervasyonGecmisResponse> kendiGecmisiniGetir(
            String aktorId,
            int page,
            int size,
            RezervasyonDurum durum,
            Integer odaId,
            LocalDate baslangic,
            LocalDate bitis
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        filtreTarihleriniDogrula(baslangic, bitis);
        Page<Rezervasyon> sonuc = rezervasyonRepository.findKendiGecmisi(
                aktor.getPersonelId(),
                durum,
                odaId,
                baslangic,
                bitis,
                gecmisSayfalama(page, size)
        );
        return gecmisSayfasinaDonustur(sonuc);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public SayfaliResponse<RezervasyonGecmisResponse> adminGecmisiniGetir(
            String aktorId,
            int page,
            int size,
            RezervasyonDurum durum,
            Integer odaId,
            String olusturanId,
            LocalDate baslangic,
            LocalDate bitis
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        if (aktor.getRol().getRolAdi() != RolAdi.ADMIN) {
            throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor.");
        }
        filtreTarihleriniDogrula(baslangic, bitis);
        String dogrulanmisOlusturanId = uuidFiltresiniDogrula(olusturanId);
        Page<Rezervasyon> sonuc = rezervasyonRepository.findAdminGecmisi(
                durum,
                odaId,
                dogrulanmisOlusturanId,
                baslangic,
                bitis,
                gecmisSayfalama(page, size)
        );
        return gecmisSayfasinaDonustur(sonuc);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR', 'ADMIN')")
    public void rezervasyonIptalEt(
            String aktorId,
            String rezervasyonId,
            RezervasyonIptalRequest request
    ) {
        Personel aktor = aktifAktoruGetir(aktorId);
        String normalizeId = rezervasyonIdDogrula(rezervasyonId);
        String ownerId = rezervasyonRepository
                .findOwnerIdByRezervasyonId(normalizeId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Rezervasyon bulunamadı."
                ));
        Personel owner = personelRepository.findByIdForUpdate(ownerId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));
        Rezervasyon rezervasyon = rezervasyonRepository
                .findByIdForUpdate(normalizeId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Rezervasyon bulunamadı."
                ));

        boolean kendiRezervasyonu = aktor.getPersonelId().equals(ownerId);
        boolean gercekDbAdmin = aktor.getRol().getRolAdi() == RolAdi.ADMIN;
        if (!kendiRezervasyonu && !gercekDbAdmin) {
            throw new AccessDeniedException(
                    "Bu işlem için yetkiniz bulunmuyor."
            );
        }

        LocalDateTime simdi = LocalDateTime.now(clock);
        LocalDateTime baslangicZamani = rezervasyon.getTarih()
                .atTime(rezervasyon.getBaslangicSaat(), 0);
        if (rezervasyon.getDurum() != RezervasyonDurum.AKTIF
                || !baslangicZamani.isAfter(simdi)) {
            throw new KaynakCakismasiException(
                    "Rezervasyon artık iptal edilemez."
            );
        }

        String iptalNedeni = request == null ? null : request.iptalNedeni();
        if (!kendiRezervasyonu) {
            if (iptalNedeni == null || iptalNedeni.trim().isEmpty()) {
                throw new GecersizIstekException(
                        "Başkasının rezervasyonu iptal edilirken neden zorunludur."
                );
            }
            iptalNedeni = iptalNedeni.trim();
        } else if (iptalNedeni != null) {
            iptalNedeni = iptalNedeni.trim();
            if (iptalNedeni.isEmpty()) {
                iptalNedeni = null;
            }
        }

        rezervasyon.setDurum(RezervasyonDurum.IPTAL_EDILDI);
        rezervasyon.setIptalEden(aktor);
        rezervasyon.setIptalZamani(simdi);
        rezervasyon.setIptalNedeni(iptalNedeni);
        rezervasyonRepository.saveAndFlush(rezervasyon);

        auditService.logKaydetOrtakTransaction(
                AuditEylem.REZERVASYON_IPTAL_EDILDI,
                aktor,
                aktor.getKullaniciAdi(),
                "Rezervasyon iptal edildi.",
                owner
        );
    }

    @Transactional
    public int personelPasiflestirmeRezervasyonlariniIptalEt(
            Personel aktor,
            Personel lockedHedef
    ) {
        LocalDateTime simdi = LocalDateTime.now(clock);
        List<Rezervasyon> rezervasyonlar = rezervasyonRepository
                .findBaslamamisAktifOwnerRezervasyonlariForUpdate(
                        lockedHedef.getPersonelId(),
                        simdi.toLocalDate(),
                        simdi.getHour()
                );
        if (rezervasyonlar.isEmpty()) {
            return 0;
        }

        rezervasyonlar.forEach(rezervasyon -> {
            rezervasyon.setDurum(RezervasyonDurum.IPTAL_EDILDI);
            rezervasyon.setIptalEden(aktor);
            rezervasyon.setIptalZamani(simdi);
            rezervasyon.setIptalNedeni("Personel pasife alındı.");
        });
        rezervasyonRepository.saveAllAndFlush(rezervasyonlar);
        rezervasyonlar.forEach(rezervasyon ->
                auditService.logKaydetOrtakTransaction(
                        AuditEylem.REZERVASYON_IPTAL_EDILDI,
                        aktor,
                        aktor.getKullaniciAdi(),
                        "Rezervasyon iptal edildi.",
                        lockedHedef
                )
        );
        return rezervasyonlar.size();
    }

    private RezervasyonResponse responseOlustur(
            Rezervasyon rezervasyon,
            Personel aktor
    ) {
        boolean owner = rezervasyon.getOlusturan().getPersonelId()
                .equals(aktor.getPersonelId());
        boolean dbAdmin = aktor.getRol().getRolAdi() == RolAdi.ADMIN;
        boolean detayGorunur = owner || dbAdmin;

        return new RezervasyonResponse(
                rezervasyon.getRezervasyonId(),
                new OdaResponse(
                        rezervasyon.getOda().getOdaId(),
                        rezervasyon.getOda().getOdaAd(),
                        rezervasyon.getOda().getOdaKapasite()
                ),
                rezervasyon.getTarih(),
                rezervasyon.getBaslangicSaat(),
                rezervasyon.getBitisSaat(),
                new RezervasyonOlusturanResponse(
                        rezervasyon.getOlusturan().getAdi(),
                        rezervasyon.getOlusturan().getSoyadi()
                ),
                owner,
                detayGorunur ? rezervasyon.getBaslik() : null,
                detayGorunur ? rezervasyon.getKatilimciSayisi() : null
        );
    }

    private SayfaliResponse<RezervasyonGecmisResponse> gecmisSayfasinaDonustur(
            Page<Rezervasyon> sayfa
    ) {
        return new SayfaliResponse<>(
                sayfa.getContent().stream()
                        .map(this::gecmisResponseOlustur)
                        .toList(),
                sayfa.getNumber(),
                sayfa.getSize(),
                sayfa.getTotalElements(),
                sayfa.getTotalPages()
        );
    }

    private RezervasyonGecmisResponse gecmisResponseOlustur(
            Rezervasyon rezervasyon
    ) {
        Personel iptalEden = rezervasyon.getIptalEden();
        LocalDateTime bitisZamani = rezervasyon.getTarih()
                .atTime(rezervasyon.getBitisSaat(), 0);
        return new RezervasyonGecmisResponse(
                rezervasyon.getRezervasyonId(),
                new OdaResponse(
                        rezervasyon.getOda().getOdaId(),
                        rezervasyon.getOda().getOdaAd(),
                        rezervasyon.getOda().getOdaKapasite()
                ),
                rezervasyon.getTarih(),
                rezervasyon.getBaslangicSaat(),
                rezervasyon.getBitisSaat(),
                olusturanResponse(rezervasyon.getOlusturan()),
                rezervasyon.getBaslik(),
                rezervasyon.getKatilimciSayisi(),
                rezervasyon.getDurum(),
                !bitisZamani.isAfter(LocalDateTime.now(clock)),
                rezervasyon.getOlusturmaZamani(),
                rezervasyon.getIptalZamani(),
                iptalEden == null ? null : olusturanResponse(iptalEden),
                rezervasyon.getIptalNedeni()
        );
    }

    private RezervasyonOlusturanResponse olusturanResponse(Personel personel) {
        return new RezervasyonOlusturanResponse(
                personel.getAdi(),
                personel.getSoyadi()
        );
    }

    private PageRequest gecmisSayfalama(int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("tarih"))
                .and(Sort.by(Sort.Order.desc("baslangicSaat")))
                .and(Sort.by(Sort.Order.desc("rezervasyonId")));
        return PageRequest.of(page, size, sort);
    }

    private void filtreTarihleriniDogrula(LocalDate baslangic, LocalDate bitis) {
        if ((baslangic == null) != (bitis == null)) {
            throw new GecersizIstekException(
                    "Başlangıç ve bitiş tarihleri birlikte gönderilmelidir."
            );
        }
        if (baslangic != null && baslangic.isAfter(bitis)) {
            throw new GecersizIstekException("Geçersiz tarih aralığı.");
        }
    }

    private String uuidFiltresiniDogrula(String deger) {
        if (deger == null || deger.isBlank()) {
            return null;
        }
        String normalize = deger.trim();
        try {
            UUID uuid = UUID.fromString(normalize);
            if (!uuid.toString().equals(normalize.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("canonical UUID değil");
            }
            return uuid.toString();
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException(
                    "Oluşturan personel ID geçerli UUID olmalıdır."
            );
        }
    }

    private String rezervasyonIdDogrula(String deger) {
        if (deger == null) {
            throw new GecersizIstekException("Geçersiz istek");
        }
        try {
            UUID uuid = UUID.fromString(deger);
            String normalize = uuid.toString();
            if (!normalize.equals(deger.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("canonical UUID değil");
            }
            return normalize;
        } catch (IllegalArgumentException exception) {
            throw new GecersizIstekException("Geçersiz istek");
        }
    }

    private Personel aktifAktoruGetir(String aktorId) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException("Personel bulunamadı."));
        aktifligiDogrula(aktor);
        return aktor;
    }

    private void aktifligiDogrula(Personel aktor) {
        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException("Bu işlem için yetkiniz bulunmuyor.");
        }
    }

    private void tarihAraliginiDogrula(
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        long gunFarki = ChronoUnit.DAYS.between(baslangicTarihi, bitisTarihi);
        if (gunFarki < 0 || gunFarki > MAKSIMUM_TAKVIM_ARALIGI_GUN) {
            throw new GecersizIstekException("Geçersiz tarih aralığı.");
        }
    }

    private int dolulukYuzdesi(int doluSaat, int acikSaat) {
        if (acikSaat == 0) {
            return 0;
        }
        return BigDecimal.valueOf(doluSaat)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(acikSaat), 0, RoundingMode.HALF_UP)
                .intValueExact();
    }
}
