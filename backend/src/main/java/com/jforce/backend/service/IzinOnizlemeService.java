package com.jforce.backend.service;

import com.jforce.backend.model.dto.response.IsGunuOnizlemeResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class IzinOnizlemeService {

    private final IzinAktoruYetkiService izinAktoruYetkiService;
    private final IsGunuHesaplamaService isGunuHesaplamaService;

    public IzinOnizlemeService(
            IzinAktoruYetkiService izinAktoruYetkiService,
            IsGunuHesaplamaService isGunuHesaplamaService
    ) {
        this.izinAktoruYetkiService = izinAktoruYetkiService;
        this.isGunuHesaplamaService =
                isGunuHesaplamaService;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PERSONEL', 'YONETICI', 'HR')")
    public IsGunuOnizlemeResponse isGunuOnizle(
            String aktorId,
            LocalDate baslangicTarihi,
            LocalDate bitisTarihi
    ) {
        izinAktoruYetkiService.talepAktoruGetir(aktorId);

        return new IsGunuOnizlemeResponse(
                isGunuHesaplamaService
                        .isGunuSayisiniHesapla(
                                baslangicTarihi,
                                bitisTarihi
                        )
        );
    }
}
