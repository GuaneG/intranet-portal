package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EkipmanTalepCreateRequest(
        @NotNull
        @Positive
        Integer ekipmanTipiId,
        String aciklama
) {
}
