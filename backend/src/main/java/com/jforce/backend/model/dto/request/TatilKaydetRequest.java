package com.jforce.backend.model.dto.request;

import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TatilKaydetRequest(

        @NotBlank
        @Size(max = 100)
        String ad,

        @NotNull
        LocalDate tarih,

        LocalDate bitisTarihi,

        @NotNull
        TatilKategori kategori,

        @NotNull
        TatilGunSuresi gunSuresi
) {
    public TatilKaydetRequest(
            String ad,
            LocalDate tarih,
            TatilKategori kategori,
            TatilGunSuresi gunSuresi
    ) {
        this(ad, tarih, null, kategori, gunSuresi);
    }
}
