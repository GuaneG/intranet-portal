package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RezervasyonRequest(
        @NotNull @Positive
        Integer odaId,
        @NotNull
        LocalDate tarih,
        @NotNull @Min(0) @Max(23)
        Integer baslangicSaat,
        @NotNull @Min(1) @Max(24)
        Integer bitisSaat,
        @NotBlank
        @Size(max = 50)
        String baslik,
        @NotNull @Positive
        Integer katilimciSayisi

) {
}
