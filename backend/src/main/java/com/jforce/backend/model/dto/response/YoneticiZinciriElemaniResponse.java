package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RolAdi;

public record YoneticiZinciriElemaniResponse(
        int seviye,
        String ad,
        String soyad,
        String departman,
        RolAdi rol,
        String profilFoto
) {
}
