package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.TalepTuru;

import java.time.LocalDateTime;

public record BekleyenTalepItem(
        TalepTuru tur,
        String ozet,
        LocalDateTime talepTarihi
) {
}
