package com.jforce.backend.model.dto.response;

public record IzinTalepSahibiResponse(
        String ad,
        String soyad,
        String departman,
        String profilFoto,
        boolean aktifMi
) {
}
