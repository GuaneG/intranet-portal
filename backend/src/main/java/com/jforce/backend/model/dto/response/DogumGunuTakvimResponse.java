package com.jforce.backend.model.dto.response;

import java.time.LocalDate;

public record DogumGunuTakvimResponse(
        String adSoyad,
        LocalDate tarih,
        String departman
) {
}
