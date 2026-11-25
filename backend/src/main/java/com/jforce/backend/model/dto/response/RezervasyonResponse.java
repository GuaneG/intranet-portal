package com.jforce.backend.model.dto.response;

import java.time.LocalDate;

public record RezervasyonResponse(
        String id,
        OdaResponse oda,
        LocalDate tarih,
        Integer baslangicSaat,
        Integer bitisSaat,
        RezervasyonOlusturanResponse olusturan,
        boolean benMiOlusturdum,
        String baslik,
        Integer katilimciSayisi
) {
}
