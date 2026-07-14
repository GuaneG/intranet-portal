package com.jforce.backend.model.dto.response;

import java.time.LocalDate;

public record IzinTalepResponse(
        String id,
        String izinTuru,
        LocalDate baslangicTarihi,
        LocalDate bitisTarihi,
        Integer isGunuSayisi,
        String yoneticiyeNot,
        String durum
) {
}

