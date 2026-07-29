package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record IlkParolaDegistirRequest(
        @NotBlank
        @Size(max = 50)
        String kullaniciAdi,

        @NotBlank
        String mevcutParola,

        @NotBlank
        String yeniParola
) {
}