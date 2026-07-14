package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EkipmanTalepCreateRequest(
        @NotNull
        Integer ekipmanTipiId,
        @Size(max = 255)
        String aciklama
) {
}
