package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RezervasyonDurum;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RezervasyonGecmisResponse(
        String id,
        OdaResponse oda,
        LocalDate tarih,
        Integer baslangicSaat,
        Integer bitisSaat,
        RezervasyonOlusturanResponse olusturan,
        String baslik,
        Integer katilimciSayisi,
        RezervasyonDurum durum,
        boolean gecmisMi,
        LocalDateTime olusturmaZamani,
        LocalDateTime iptalZamani,
        RezervasyonOlusturanResponse iptalEden,
        String iptalNedeni
) {
}
