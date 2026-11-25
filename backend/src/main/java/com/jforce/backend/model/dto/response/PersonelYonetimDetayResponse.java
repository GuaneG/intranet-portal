package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RolAdi;

import java.time.LocalDate;
import java.util.List;

public record PersonelYonetimDetayResponse(
        String personelId,
        String kullaniciAdi,
        String ad,
        String soyad,
        String profilFoto,
        LocalDate dogumTarihi,
        String ePosta,
        Integer departmanId,
        String yoneticiId,
        LocalDate isGirisTarihi,
        String telNo,
        String mezunOkul,
        String mezunBolum,
        Integer mezuniyetYili,
        RolAdi rol,
        Boolean aktif,
        List<LookUpResponse> yetenekler
) {
}
