package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record IzinTalepCreateRequest(
        @NotNull
        Integer izinTuruId,

        @NotNull
        @FutureOrPresent
        LocalDate baslangicTarihi,

        @NotNull
        @FutureOrPresent
        LocalDate bitisTarihi,

        String yoneticiyeNot

) {
}
