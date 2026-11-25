package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record IzinTalepCreateRequest(
        @NotNull
        @Positive
        Integer izinTuruId,

        @NotNull
        LocalDate baslangicTarihi,

        @NotNull
        LocalDate bitisTarihi,

        String yoneticiyeNot
) {
}
