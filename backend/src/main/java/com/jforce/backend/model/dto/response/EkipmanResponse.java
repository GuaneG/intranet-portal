package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.EkipmanDurum;

public record EkipmanResponse(
        String id,
        Integer ekipmanTipiId,
        String ekipmanTipi,
        String marka,
        String model,
        String seriNo,
        EkipmanDurum durum,
        String kullanimDisiNedeni,
        boolean acikZimmetVarMi
) {
}
