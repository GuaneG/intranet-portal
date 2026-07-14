package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record YorumResponse(
        Integer id,
        String yazan,
        String icerik,
        LocalDateTime tarih,
        boolean benBegendimMi
) {
}
