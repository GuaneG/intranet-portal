package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record YetenekCreateRequest(
        @NotBlank @Size(max = 50)
        String yetenekAdi
) {
}
