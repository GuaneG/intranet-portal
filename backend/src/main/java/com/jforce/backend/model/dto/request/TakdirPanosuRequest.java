package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TakdirPanosuRequest(
        @NotBlank
        @Size(min = 36, max = 36)
        String aliciPersonelId,
        @NotBlank
        @Size(max = 300)
        String mesaj
) {
}
