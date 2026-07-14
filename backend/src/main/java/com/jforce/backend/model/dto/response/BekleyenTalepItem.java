package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record BekleyenTalepItem(
        TalepTuru tur,
        String ozet,
        LocalDateTime talepTarihi
) {
}
