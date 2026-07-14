package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;
import java.util.List;

//get /api/announcements/{id}
public record DuyuruDetayResponse(
        Integer id,
        String olusturan,
        LocalDateTime olusturmaTarihi,
        String baslik,
        String icerik,
        Integer begeniSayisi,
        boolean benBegendimMi,
        List<YorumResponse> yorumlar
) {
}
