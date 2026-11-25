package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.TalepDurum;

import java.time.LocalDateTime;

public record EkipmanTalepResponse(
        String id,
        Integer ekipmanTipiId,
        String ekipmanTipi,
        String aciklama,
        TalepDurum durum,
        String durumAdi,
        LocalDateTime talepTarihi,
        String kararNotu,
        LocalDateTime islemTarihi,
        EkipmanTalepKararVerenResponse kararVeren,
        EkipmanTalepZimmetResponse zimmet
) {
}
