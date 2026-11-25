package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.BugunIzinDurumu;

import java.util.List;

public record EkipDiziniResponse(
        String ad,
        String soyad,
        String departman,
        String profilFoto,
        List<LookUpResponse> yetenekler,
        boolean yoneticinMi,
        BugunIzinDurumu bugunIzinDurumu
) {
}
