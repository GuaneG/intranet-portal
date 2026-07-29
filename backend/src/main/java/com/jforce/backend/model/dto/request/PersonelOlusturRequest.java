package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;

public record PersonelOlusturRequest(
        @NotBlank
        @Size(min = 3, max = 50)
        String kullaniciAdi,

        @NotBlank
        @Size(min = 1,max = 50)
        String ad,

        @NotBlank
        @Size(min = 3, max = 50)
        String soyad,

        @NotNull
        @Past
        LocalDate dogumTarihi,

        @NotBlank
        @Email
        @Size(max=100)
        String ePosta,

        @NotNull
        @Positive
        Integer departmanId,

        @NotNull
        @PastOrPresent
        LocalDate isGirisTarihi,
        String yoneticiId
) {
}
