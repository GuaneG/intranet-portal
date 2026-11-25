package com.jforce.backend.model.dto.response;

import java.util.List;

public record SayfaliResponse<T>(
        List<T> icerik,
        int sayfa,
        int sayfaBoyutu,
        long toplamKayit,
        int toplamSayfa
) {
}