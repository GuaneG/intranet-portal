package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record EkipmanTalepResponse(
        String id,
        String ekipmanTipi,
        String aciklama,
        String durum,
        LocalDateTime talepTarihi,
        LocalDateTime islemTarihi //nullable
) {}
