package com.jforce.backend.model.dto.request;
import jakarta.validation.constraints.*;

public record LoginRequest(

        @NotBlank
        String kullaniciAdi,
        @NotBlank
        String parola

) {
}
