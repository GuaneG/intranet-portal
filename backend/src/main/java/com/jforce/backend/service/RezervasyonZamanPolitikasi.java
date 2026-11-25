package com.jforce.backend.service;

import com.jforce.backend.config.RezervasyonAyarlari;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.entity.Rezervasyon;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.repository.TatilRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class RezervasyonZamanPolitikasi {

    private static final String GECERSIZ_ZAMAN_MESAJI =
            "Rezervasyon tarihi veya saati uygun değil.";
    private static final String CALISMA_SINIRI_MESAJI =
            "Rezervasyon çalışma saatleri ve süre sınırı içinde olmalıdır.";

    private final TatilRepository tatilRepository;
    private final RezervasyonAyarlari ayarlar;
    private final Clock clock;

    public RezervasyonZamanPolitikasi(
            TatilRepository tatilRepository,
            RezervasyonAyarlari ayarlar,
            Clock clock
    ) {
        this.tatilRepository = tatilRepository;
        this.ayarlar = ayarlar;
        this.clock = clock;
    }

    public void olusturmaZamaniniDogrula(
            LocalDate tarih,
            int baslangicSaat,
            int bitisSaat
    ) {
        LocalDate bugun = LocalDate.now(clock);
        if (tarih == null
                || tarih.isBefore(bugun)
                || tarih.isAfter(bugun.plusDays(ayarlar.getMaksimumIleriGun()))) {
            throw new GecersizIstekException(GECERSIZ_ZAMAN_MESAJI);
        }

        int sure = bitisSaat - baslangicSaat;
        if (baslangicSaat < ayarlar.getCalismaBaslangicSaati()
                || bitisSaat > ayarlar.getCalismaBitisSaati()
                || sure < 1
                || sure > ayarlar.getMaksimumSureSaat()) {
            throw new GecersizIstekException(CALISMA_SINIRI_MESAJI);
        }

        if (tarih.equals(bugun) && baslangicSaat < LocalTime.now(clock).getHour() + 1) {
            throw new GecersizIstekException(GECERSIZ_ZAMAN_MESAJI);
        }

        if (haftaSonuMu(tarih)) {
            throw new GecersizIstekException("Hafta sonu rezervasyon yapılamaz.");
        }

        AcikGunBilgisi acikGun = acikGunBilgisi(tarih);
        if (acikGun.kapaliMi()) {
            throw new GecersizIstekException("Tatil gününde rezervasyon yapılamaz.");
        }
        if (bitisSaat > acikGun.bitisSaat()) {
            throw new GecersizIstekException(
                    "Yarım gün tatilde rezervasyon 13:00'e kadar yapılabilir."
            );
        }
    }

    public AcikGunBilgisi acikGunBilgisi(LocalDate tarih) {
        if (haftaSonuMu(tarih)) {
            return AcikGunBilgisi.kapali();
        }

        var tatiller = tatilRepository.findAllKesisen(tarih, tarih);
        boolean tamGun = tatiller.stream()
                .anyMatch(tatil -> tatil.getGunSuresi() == TatilGunSuresi.TAM_GUN);
        if (tamGun) {
            return AcikGunBilgisi.kapali();
        }

        boolean yarimGun = tatiller.stream()
                .anyMatch(tatil -> tatil.getGunSuresi() == TatilGunSuresi.YARIM_GUN);
        int bitisSaati = yarimGun
                ? ayarlar.getYarimGunBitisSaati()
                : ayarlar.getCalismaBitisSaati();
        return new AcikGunBilgisi(ayarlar.getCalismaBaslangicSaati(), bitisSaati);
    }

    public boolean baslamisMi(Rezervasyon rezervasyon) {
        LocalDateTime baslangic = LocalDateTime.of(
                rezervasyon.getTarih(),
                LocalTime.of(rezervasyon.getBaslangicSaat(), 0)
        );
        return !baslangic.isAfter(LocalDateTime.now(clock));
    }

    public boolean gecmisMi(Rezervasyon rezervasyon) {
        LocalDateTime bitis = LocalDateTime.of(
                rezervasyon.getTarih(),
                LocalTime.of(rezervasyon.getBitisSaat(), 0)
        );
        return !bitis.isAfter(LocalDateTime.now(clock));
    }

    private boolean haftaSonuMu(LocalDate tarih) {
        DayOfWeek gun = tarih.getDayOfWeek();
        return gun == DayOfWeek.SATURDAY || gun == DayOfWeek.SUNDAY;
    }

    public record AcikGunBilgisi(int baslangicSaat, int bitisSaat) {

        private static AcikGunBilgisi kapali() {
            return new AcikGunBilgisi(0, 0);
        }

        public boolean kapaliMi() {
            return baslangicSaat == bitisSaat;
        }

        public int acikSaatSayisi() {
            return bitisSaat - baslangicSaat;
        }
    }
}
