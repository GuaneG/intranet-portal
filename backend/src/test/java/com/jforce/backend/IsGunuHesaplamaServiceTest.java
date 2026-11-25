package com.jforce.backend;

import com.jforce.backend.repository.TatilRepository;
import com.jforce.backend.service.IsGunuHesaplamaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.jforce.backend.model.entity.Tatil;
import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.exception.GecersizIstekException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IsGunuHesaplamaServiceTest {

    @Mock
    private TatilRepository tatilRepository;

    private IsGunuHesaplamaService service;

    @BeforeEach
    void setUp() {
        service =
                new IsGunuHesaplamaService(
                        tatilRepository
                );
    }


    @Test
    void haftaIcindekiTamGunTatilToplamaKatkiYapmaz() {
        LocalDate baslangic =
                LocalDate.of(2026, 8, 24);

        LocalDate bitis =
                LocalDate.of(2026, 8, 28);

        Tatil tamGunTatil = new Tatil();
        tamGunTatil.setTarih(
                LocalDate.of(2026, 8, 26)
        );
        tamGunTatil.setBitisTarihi(
                LocalDate.of(2026, 8, 26)
        );
        tamGunTatil.setGunSuresi(
                TatilGunSuresi.TAM_GUN
        );

        when(
                tatilRepository
                        .findAllKesisen(
                                baslangic,
                                bitis
                        )
        ).thenReturn(
                List.of(tamGunTatil)
        );

        BigDecimal sonuc =
                service.isGunuSayisiniHesapla(
                        baslangic,
                        bitis
                );

        assertEquals(
                new BigDecimal("4.0"),
                sonuc
        );

        verify(tatilRepository)
                .findAllKesisen(
                        baslangic,
                        bitis
                );
    }

    @Test
    void haftaIcindekiYarimGunTatilYarimGunKatkiYapar() {
        LocalDate baslangic =
                LocalDate.of(2026, 8, 24);

        LocalDate bitis =
                LocalDate.of(2026, 8, 28);

        Tatil yarimGunTatil = new Tatil();
        yarimGunTatil.setTarih(
                LocalDate.of(2026, 8, 26)
        );
        yarimGunTatil.setBitisTarihi(
                LocalDate.of(2026, 8, 26)
        );
        yarimGunTatil.setGunSuresi(
                TatilGunSuresi.YARIM_GUN
        );

        when(
                tatilRepository
                        .findAllKesisen(
                                baslangic,
                                bitis
                        )
        ).thenReturn(
                List.of(yarimGunTatil)
        );

        BigDecimal sonuc =
                service.isGunuSayisiniHesapla(
                        baslangic,
                        bitis
                );

        assertEquals(
                new BigDecimal("4.5"),
                sonuc
        );

        verify(tatilRepository)
                .findAllKesisen(
                        baslangic,
                        bitis
                );
    }

    @ParameterizedTest
    @MethodSource("gecersizTarihAraliklari")
    void gecersizTarihAraliklariRepositoryeGitmedenReddedilir(
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        assertThrows(
                GecersizIstekException.class,
                () -> service.isGunuSayisiniHesapla(
                        baslangicTarihi,
                        bitisTarihi
                )
        );

        verifyNoInteractions(tatilRepository);
    }

    static Stream<Arguments> gecersizTarihAraliklari() {
        return Stream.of(
                Arguments.of(
                        null,
                        LocalDate.of(2026, 8, 24)
                ),
                Arguments.of(
                        LocalDate.of(2026, 8, 24),
                        null
                ),
                Arguments.of(
                        LocalDate.of(2026, 8, 28),
                        LocalDate.of(2026, 8, 24)
                ),
                Arguments.of(
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2027, 1, 2)
                )
        );
    }

    @Test
    void tatilOlmayanPazartesiCumaArasiBesIsGunuDoner() {
        LocalDate baslangic =
                LocalDate.of(2026, 8, 24);

        LocalDate bitis =
                LocalDate.of(2026, 8, 28);

        when(
                tatilRepository
                        .findAllKesisen(
                                baslangic,
                                bitis
                        )
        ).thenReturn(List.of());

        BigDecimal sonuc =
                service.isGunuSayisiniHesapla(
                        baslangic,
                        bitis
                );

        assertEquals(
                new BigDecimal("5.0"),
                sonuc
        );

        verify(tatilRepository)
                .findAllKesisen(
                        baslangic,
                        bitis
                );
    }

    @Test
    void cokGunlukYarimTatilHerKapsananHaftaIcineYarimKatkiYapar() {
        LocalDate baslangic = LocalDate.of(2026, 8, 24);
        LocalDate bitis = LocalDate.of(2026, 8, 28);

        Tatil grup = new Tatil();
        grup.setTarih(baslangic);
        grup.setBitisTarihi(bitis);
        grup.setGunSuresi(TatilGunSuresi.YARIM_GUN);

        when(
                tatilRepository
                        .findAllKesisen(
                                baslangic,
                                bitis
                        )
        ).thenReturn(List.of(grup));

        assertEquals(
                new BigDecimal("2.5"),
                service.isGunuSayisiniHesapla(
                        baslangic,
                        bitis
                )
        );
    }

    @Test
    void onceBaslayanTamGunGrupKesisenHaftaIciGunleriniDusurur() {
        LocalDate baslangic = LocalDate.of(2026, 8, 28);
        LocalDate bitis = LocalDate.of(2026, 9, 2);

        Tatil grup = new Tatil();
        grup.setTarih(LocalDate.of(2026, 8, 27));
        grup.setBitisTarihi(LocalDate.of(2026, 8, 31));
        grup.setGunSuresi(TatilGunSuresi.TAM_GUN);

        when(
                tatilRepository
                        .findAllKesisen(
                                baslangic,
                                bitis
                        )
        ).thenReturn(List.of(grup));

        assertEquals(
                new BigDecimal("2.0"),
                service.isGunuSayisiniHesapla(
                        baslangic,
                        bitis
                )
        );
    }
}
