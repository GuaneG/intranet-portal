package com.jforce.backend.model.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

public record ProfilBilgileriResponse(
        String kullaniciAdi,
        String ad,
        String soyad,
        LocalDate dogumTarihi,
        String ePosta,
        Integer sicilNo,
        String departman,
        String rol,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String yonetici,
        Integer yillikIzinHakki,
        String profilFoto,
        Integer kalanIzin,
        List<LookUpResponse> yetenekler
) {
}
