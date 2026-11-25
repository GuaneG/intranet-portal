package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record EkipmanTalepZimmetResponse(
        String id,
        String ekipmanId,
        String marka,
        String model,
        String seriNo,
        LocalDateTime teslimTarihi,
        LocalDateTime iadeTarihi
) {
}
