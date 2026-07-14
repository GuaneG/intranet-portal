package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record ZimmetResponse(
        String id,
        String ekipmanTipi,
        String marka,
        String model,
        String seriNo,
        LocalDateTime teslimTarihi,
        LocalDateTime iadeTarihi    // nullable
) {}