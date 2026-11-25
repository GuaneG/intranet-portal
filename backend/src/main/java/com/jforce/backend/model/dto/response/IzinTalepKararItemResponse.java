package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.TalepDurum;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record IzinTalepKararItemResponse(
        String id,
        IzinTalepSahibiResponse talepSahibi,
        String izinTuru,
        LocalDate baslangicTarihi,
        LocalDate bitisTarihi,
        BigDecimal isGunuSayisi,
        String yoneticiyeNot,
        TalepDurum durum,
        String durumAdi,
        LocalDateTime talepTarihi,
        String kararNotu,
        LocalDateTime islemTarihi,
        IzinTalepKararVerenResponse kararVeren
) {
}
