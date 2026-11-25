package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;

import java.time.LocalDate;

public record TatilResponse(
        Integer tatilId,
        String ad,
        LocalDate tarih,
        LocalDate bitisTarihi,
        TatilKategori kategori,
        TatilGunSuresi gunSuresi
) {
}
