package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.EkipmanDurum;

public record EkipmanResponse(
        String id,
        String ekipmanTipi,
        String marka,
        String model,
        String seriNo,
        EkipmanDurum durum
) {
}
