package com.jforce.backend.model.dto.response;

import java.util.List;

public record EkipDiziniResponse(
        String ad,
        String soyad,
        String departman,
        String profilFoto,
        List<LookUpResponse> yetenekler
) {
}
