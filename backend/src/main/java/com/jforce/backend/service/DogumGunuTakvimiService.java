package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.KaynakBulunamadiException;
import com.jforce.backend.model.dto.response.DogumGunuTakvimResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DogumGunuTakvimiService {

    private final PersonelRepository personelRepository;

    public DogumGunuTakvimiService(PersonelRepository personelRepository) {
        this.personelRepository = personelRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public List<DogumGunuTakvimResponse> dogumGunleriniGetir(
            String aktorId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        Personel aktor = personelRepository.findById(aktorId)
                .orElseThrow(() -> new KaynakBulunamadiException(
                        "Personel bulunamadı."
                ));

        if (!Boolean.TRUE.equals(aktor.getCalisiyorMu())) {
            throw new AccessDeniedException(
                    "Pasif personel doğum günü takvimini görüntüleyemez."
            );
        }

        tarihAraliginiDogrula(baslangicTarihi, bitisTarihi);

        List<DogumGunuTakvimResponse> sonuclar = new ArrayList<>();
        List<Personel> adaylar = personelRepository
                .findAktifDogumGunuTakvimiPersonelleri(
                        araliktakiAylariBul(baslangicTarihi, bitisTarihi)
                );

        for (Personel personel : adaylar) {
            for (int yil = baslangicTarihi.getYear();
                 yil <= bitisTarihi.getYear();
                 yil++) {
                LocalDate takvimTarihi = takvimTarihiniOlustur(
                        personel,
                        yil
                );

                if (takvimTarihi != null
                        && !takvimTarihi.isBefore(baslangicTarihi)
                        && !takvimTarihi.isAfter(bitisTarihi)) {
                    sonuclar.add(new DogumGunuTakvimResponse(
                            personel.getAdi() + " " + personel.getSoyadi(),
                            takvimTarihi,
                            personel.getDepartman().getDepartmanAdi()
                    ));
                }
            }
        }

        return sonuclar.stream()
                .sorted(Comparator
                        .comparing(DogumGunuTakvimResponse::tarih)
                        .thenComparing(DogumGunuTakvimResponse::adSoyad)
                        .thenComparing(DogumGunuTakvimResponse::departman))
                .toList();
    }

    private Set<Integer> araliktakiAylariBul(
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        Set<Integer> aylar = new LinkedHashSet<>();
        YearMonth mevcutAy = YearMonth.from(baslangicTarihi);
        YearMonth bitisAyi = YearMonth.from(bitisTarihi);

        while (!mevcutAy.isAfter(bitisAyi)) {
            aylar.add(mevcutAy.getMonthValue());
            mevcutAy = mevcutAy.plusMonths(1);
        }

        return aylar;
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
        if (ChronoUnit.DAYS.between(baslangicTarihi, bitisTarihi) > 365) {
            throw new GecersizIstekException(
                    "Tarih aralığı en fazla 366 gün olabilir."
            );
        }
    }

    private LocalDate takvimTarihiniOlustur(Personel personel, int yil) {
        try {
            return LocalDate.of(
                    yil,
                    personel.getDogumTarihi().getMonthValue(),
                    personel.getDogumTarihi().getDayOfMonth()
            );
        } catch (DateTimeException exception) {
            return null;
        }
    }

}
