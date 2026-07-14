package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record DuyuruBaslikItem(
        Integer id,     //duyuru detayına gidebilmek için
        String baslik,
        LocalDateTime tarih
) {
}
