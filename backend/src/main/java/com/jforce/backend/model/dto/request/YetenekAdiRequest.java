package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record YetenekAdiRequest(
        @NotBlank
        String yetenekAdi
) {
}
