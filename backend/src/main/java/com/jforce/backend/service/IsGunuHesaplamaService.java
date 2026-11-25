package com.jforce.backend.service;

import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.repository.TatilRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class IsGunuHesaplamaService {

    private static final BigDecimal TAM_GUN = new BigDecimal("1.0");
    private static final BigDecimal YARIM_GUN = new BigDecimal("0.5");

    private final TatilRepository tatilRepository;

    public IsGunuHesaplamaService(TatilRepository tatilRepository) {
        this.tatilRepository = tatilRepository;
    }

    @Transactional(readOnly = true)
    public BigDecimal isGunuSayisiniHesapla(LocalDate baslangicTarihi, LocalDate bitisTarihi) {
        tarihAraliginiDogrula(baslangicTarihi, bitisTarihi);

        List<Tatil> tatiller = tatilRepository
                .findAllKesisen(
                        baslangicTarihi,
                        bitisTarihi
                );

        BigDecimal toplam = BigDecimal.ZERO.setScale(1);

        for (
                LocalDate gun = baslangicTarihi;
                !gun.isAfter(bitisTarihi);
                gun = gun.plusDays(1)
        ) {
            if (haftaSonuMu(gun)) {
                continue;
            }

            TatilGunSuresi tatilGunSuresi =
                    kapsayanTatilSuresi(gun, tatiller);

            if (tatilGunSuresi == TatilGunSuresi.TAM_GUN) {
                continue;
            }

            if (tatilGunSuresi == TatilGunSuresi.YARIM_GUN) {
                toplam = toplam.add(YARIM_GUN);
                continue;
            }

            toplam = toplam.add(TAM_GUN);
        }

        return toplam.setScale(1);
    }

    private TatilGunSuresi kapsayanTatilSuresi(
            LocalDate gun,
            List<Tatil> tatiller
    ) {
        return tatiller.stream()
                .filter(tatil -> !gun.isBefore(tatil.getTarih()))
                .filter(tatil -> !gun.isAfter(tatil.getBitisTarihi()))
                .map(Tatil::getGunSuresi)
                .findFirst()
                .orElse(null);
    }

    private void tarihAraliginiDogrula(LocalDate baslangicTarihi, LocalDate bitisTarihi) {
        if (baslangicTarihi == null || bitisTarihi == null) {
            throw new GecersizIstekException("Başlangıç ve bitiş tarihleri zorunludur.");
        }

        if (bitisTarihi.isBefore(baslangicTarihi)) {
            throw new GecersizIstekException("Bitiş tarihi başlangıç tarihinden önce olamaz.");
        }

        long gunFarki = ChronoUnit.DAYS.between(baslangicTarihi, bitisTarihi);

        if (gunFarki > 365) {
            throw new GecersizIstekException("Tarih aralığı en fazla 366 gün olabilir.");
        }
    }

    private boolean haftaSonuMu(LocalDate gun) {
        DayOfWeek haftaninGunu = gun.getDayOfWeek();
        return haftaninGunu == DayOfWeek.SATURDAY || haftaninGunu == DayOfWeek.SUNDAY;
    }
}
