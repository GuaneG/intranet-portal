package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AdminParolaSifirlaRequest(
        @NotBlank
        String geciciParola
) {
}
