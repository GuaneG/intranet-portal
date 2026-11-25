package com.jforce.backend;

import com.jforce.backend.config.RezervasyonAyarlari;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.model.entity.Rezervasyon;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.service.RezervasyonZamanPolitikasi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RezervasyonZamanPolitikasiTest {

    private static final ZoneId TURKIYE = ZoneId.of("Europe/Istanbul");
    private static final LocalDate BUGUN = LocalDate.of(2026, 9, 10);

    private TatilRepository tatilRepository;
    private RezervasyonAyarlari ayarlar;
    private RezervasyonZamanPolitikasi politika;

    @BeforeEach
    void setUp() {
        tatilRepository = mock(TatilRepository.class);
        ayarlar = varsayilanAyarlar();
        politika = politika(Instant.parse("2026-09-10T07:20:00Z"));
    }

    @Test
    void bugunIcinYalnizSonrakiTamSaattenItibarenRezervasyonYapilabilir() {
        when(tatilRepository.findAllKesisen(BUGUN, BUGUN)).thenReturn(List.of());

        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(BUGUN, 10, 11)
        );
        assertDoesNotThrow(() -> politika.olusturmaZamaniniDogrula(BUGUN, 11, 12));

        RezervasyonZamanPolitikasi tamSaatPolitikasi = politika(
                Instant.parse("2026-09-10T07:00:00Z")
        );
        assertThrows(
                GecersizIstekException.class,
                () -> tamSaatPolitikasi.olusturmaZamaniniDogrula(BUGUN, 10, 11)
        );
        assertDoesNotThrow(
                () -> tamSaatPolitikasi.olusturmaZamaniniDogrula(BUGUN, 11, 12)
        );
    }

    @Test
    void sureCalismaSaatiVeIleriTarihSinirlariUygulanir() {
        LocalDate yarin = BUGUN.plusDays(1);
        LocalDate doksaninciGun = BUGUN.plusDays(90);
        when(tatilRepository.findAllKesisen(yarin, yarin)).thenReturn(List.of());
        when(tatilRepository.findAllKesisen(doksaninciGun, doksaninciGun))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> politika.olusturmaZamaniniDogrula(yarin, 8, 9));
        assertDoesNotThrow(() -> politika.olusturmaZamaniniDogrula(yarin, 8, 12));
        assertDoesNotThrow(
                () -> politika.olusturmaZamaniniDogrula(doksaninciGun, 14, 18)
        );

        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(yarin, 8, 8)
        );
        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(yarin, 8, 13)
        );
        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(yarin, 7, 8)
        );
        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(yarin, 18, 19)
        );
        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(BUGUN.minusDays(1), 8, 9)
        );
        assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(BUGUN.plusDays(91), 8, 9)
        );
    }

    @Test
    void haftaSonuVeTamGunTatildeRezervasyonYapilamaz() {
        LocalDate cumartesi = LocalDate.of(2026, 9, 12);
        LocalDate tamGunTatil = LocalDate.of(2026, 9, 14);
        when(tatilRepository.findAllKesisen(tamGunTatil, tamGunTatil))
                .thenReturn(List.of(tatil(tamGunTatil, TatilGunSuresi.TAM_GUN)));

        GecersizIstekException haftaSonuHatasi = assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(cumartesi, 8, 9)
        );
        assertEquals("Hafta sonu rezervasyon yapılamaz.", haftaSonuHatasi.getMessage());

        GecersizIstekException tatilHatasi = assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(tamGunTatil, 8, 9)
        );
        assertEquals("Tatil gününde rezervasyon yapılamaz.", tatilHatasi.getMessage());
    }

    @Test
    void yarimGunTatildeYalnizSekizIleOnUcArasiKullanilabilir() {
        LocalDate yarimGunTatil = LocalDate.of(2026, 9, 11);
        when(tatilRepository.findAllKesisen(yarimGunTatil, yarimGunTatil))
                .thenReturn(List.of(tatil(yarimGunTatil, TatilGunSuresi.YARIM_GUN)));

        assertDoesNotThrow(
                () -> politika.olusturmaZamaniniDogrula(yarimGunTatil, 9, 13)
        );
        GecersizIstekException hata = assertThrows(
                GecersizIstekException.class,
                () -> politika.olusturmaZamaniniDogrula(yarimGunTatil, 12, 14)
        );
        assertEquals(
                "Yarım gün tatilde rezervasyon 13:00'e kadar yapılabilir.",
                hata.getMessage()
        );
    }

    @Test
    void acikGunBilgisiNormalYarimVeKapaliGunleriAyirir() {
        LocalDate normalGun = LocalDate.of(2026, 9, 11);
        LocalDate yarimGun = LocalDate.of(2026, 9, 14);
        LocalDate tamGun = LocalDate.of(2026, 9, 15);
        LocalDate pazar = LocalDate.of(2026, 9, 13);
        when(tatilRepository.findAllKesisen(normalGun, normalGun)).thenReturn(List.of());
        when(tatilRepository.findAllKesisen(yarimGun, yarimGun))
                .thenReturn(List.of(tatil(yarimGun, TatilGunSuresi.YARIM_GUN)));
        when(tatilRepository.findAllKesisen(tamGun, tamGun))
                .thenReturn(List.of(tatil(tamGun, TatilGunSuresi.TAM_GUN)));

        var normal = politika.acikGunBilgisi(normalGun);
        var yarim = politika.acikGunBilgisi(yarimGun);
        var tam = politika.acikGunBilgisi(tamGun);
        var haftaSonu = politika.acikGunBilgisi(pazar);

        assertEquals(8, normal.baslangicSaat());
        assertEquals(18, normal.bitisSaat());
        assertEquals(10, normal.acikSaatSayisi());
        assertEquals(5, yarim.acikSaatSayisi());
        assertEquals(0, tam.acikSaatSayisi());
        assertEquals(0, haftaSonu.acikSaatSayisi());
        assertTrue(tam.kapaliMi());
        assertTrue(haftaSonu.kapaliMi());
    }

    @Test
    void baslamaVeBitisAnlariClockUzerindenBelirlenir() {
        Rezervasyon baslamis = rezervasyon(BUGUN, 10, 11);
        Rezervasyon baslamamis = rezervasyon(BUGUN, 11, 12);
        Rezervasyon bitmis = rezervasyon(BUGUN, 9, 10);

        assertTrue(politika.baslamisMi(baslamis));
        assertFalse(politika.baslamisMi(baslamamis));
        assertTrue(politika.gecmisMi(bitmis));
        assertFalse(politika.gecmisMi(baslamis));
    }

    @Test
    void tutarsizRezervasyonAyarlariBaslangictaReddedilir() {
        RezervasyonAyarlari tutarsiz = varsayilanAyarlar();
        tutarsiz.setCalismaBitisSaati(8);

        assertThrows(IllegalStateException.class, tutarsiz::ayarlariDogrula);
    }

    private RezervasyonZamanPolitikasi politika(Instant instant) {
        return new RezervasyonZamanPolitikasi(
                tatilRepository,
                ayarlar,
                Clock.fixed(instant, TURKIYE)
        );
    }

    private RezervasyonAyarlari varsayilanAyarlar() {
        RezervasyonAyarlari sonuc = new RezervasyonAyarlari();
        sonuc.setCalismaBaslangicSaati(8);
        sonuc.setCalismaBitisSaati(18);
        sonuc.setYarimGunBitisSaati(13);
        sonuc.setMaksimumSureSaat(4);
        sonuc.setMaksimumIleriGun(90);
        return sonuc;
    }

    private Tatil tatil(LocalDate tarih, TatilGunSuresi sure) {
        Tatil tatil = new Tatil();
        tatil.setTarih(tarih);
        tatil.setBitisTarihi(tarih);
        tatil.setGunSuresi(sure);
        return tatil;
    }

    private Rezervasyon rezervasyon(LocalDate tarih, int baslangic, int bitis) {
        Rezervasyon rezervasyon = new Rezervasyon();
        rezervasyon.setTarih(tarih);
        rezervasyon.setBaslangicSaat(baslangic);
        rezervasyon.setBitisSaat(bitis);
        return rezervasyon;
    }
}
