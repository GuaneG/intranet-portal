package com.jforce.backend.service;

import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.BugunIzinDurumu;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.repository.IzinBilgileriRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BugunIzinDurumuService {

    private static final BigDecimal YARIM_GUN = new BigDecimal("0.5");

    private final IzinBilgileriRepository izinBilgileriRepository;
    private final IsGunuHesaplamaService isGunuHesaplamaService;
    private final Clock clock;

    public BugunIzinDurumuService(
            IzinBilgileriRepository izinBilgileriRepository,
            IsGunuHesaplamaService isGunuHesaplamaService,
            Clock clock
    ) {
        this.izinBilgileriRepository = izinBilgileriRepository;
        this.isGunuHesaplamaService = isGunuHesaplamaService;
        this.clock = clock;
    }

    public Map<String, BugunIzinDurumu> durumlariGetir(
            Collection<Personel> personeller
    ) {
        if (personeller.isEmpty()) {
            return Map.of();
        }

        LocalDate bugun = LocalDate.now(clock);
        BigDecimal bugunIsGunu = isGunuHesaplamaService
                .isGunuSayisiniHesapla(bugun, bugun);
        if (bugunIsGunu.signum() == 0) {
            return Map.of();
        }

        List<String> personelIds = personeller.stream()
                .map(Personel::getPersonelId)
                .distinct()
                .toList();
        BugunIzinDurumu durum = bugunIsGunu.compareTo(YARIM_GUN) == 0
                ? BugunIzinDurumu.YARIM_GUN_IZINLI
                : BugunIzinDurumu.IZINLI;
        Map<String, BugunIzinDurumu> sonuc = new HashMap<>();

        izinBilgileriRepository.findBugunIzinliPersonelIds(
                personelIds,
                TalepDurum.ONAYLANDI,
                bugun
        ).forEach(personelId -> sonuc.put(personelId, durum));

        return Map.copyOf(sonuc);
    }
}
