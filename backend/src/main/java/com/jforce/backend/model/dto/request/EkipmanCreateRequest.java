package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record EkipmanCreateRequest(
        @NotNull
        @Positive
        Integer ekipmanTipiId,
        @NotBlank
        String marka,
        String model,
        @NotBlank
        String seriNo
) {
}
