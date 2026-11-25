package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.enums.BugunIzinDurumu;

import java.util.List;

public record HiyerarsiDugumResponse(
        String ad,
        String soyad,
        String departman,
        RolAdi rol,
        String profilFoto,
        boolean yoneticinMi,
        BugunIzinDurumu bugunIzinDurumu,
        List<HiyerarsiDugumResponse> dogrudanBaglilar
) {
}
