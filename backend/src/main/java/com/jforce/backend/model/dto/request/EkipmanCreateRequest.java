package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EkipmanCreateRequest(
        @NotNull
        Integer ekipmanTipi,
        @NotBlank
        @Size(max = 50)
        String marka,
        @Size(max = 50)
        String model,
        @NotBlank
        @Size(max = 100)
        String seriNo
) {
}
