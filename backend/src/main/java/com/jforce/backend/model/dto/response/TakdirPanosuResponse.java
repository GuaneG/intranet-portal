package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record TakdirPanosuResponse(
        Integer id,
        String gonderenAd,
        String aliciAd,
        String mesaj,
        LocalDateTime olusturmaTarihi
) {
}
