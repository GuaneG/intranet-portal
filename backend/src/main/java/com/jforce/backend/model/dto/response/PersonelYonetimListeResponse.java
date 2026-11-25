package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RolAdi;

public record PersonelYonetimListeResponse(
        String personelId,
        String ad,
        String soyad,
        String kullaniciAdi,
        String profilFoto,
        RolAdi rol,
        Integer departmanId,
        String departmanAdi,
        Boolean aktif
) {
}
