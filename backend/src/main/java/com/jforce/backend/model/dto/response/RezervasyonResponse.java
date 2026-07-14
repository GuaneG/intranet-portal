package com.jforce.backend.model.dto.response;

import jakarta.persistence.criteria.CriteriaBuilder;

import java.time.LocalDate;

public record RezervasyonResponse(
        String id,
        String baslik,
        LocalDate tarih,
        Integer baslangicSaat,
        Integer bitisSaat,
        String olusturanAd,
        String olusturanSoyad,
        boolean benMiOlusturdum
) {
}
