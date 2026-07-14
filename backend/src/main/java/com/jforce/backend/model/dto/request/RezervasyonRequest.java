package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record RezervasyonRequest(
        @NotNull
        Integer odaId,
        @NotNull
        @FutureOrPresent
        LocalDate tarih,
        @NotNull @Min(0) @Max(23)
        Integer baslangicSaat,
        @NotNull @Min(0) @Max(23)
        Integer bitisSaat,
        @NotBlank
        @Size(max = 50)
        String baslik

) {
}
