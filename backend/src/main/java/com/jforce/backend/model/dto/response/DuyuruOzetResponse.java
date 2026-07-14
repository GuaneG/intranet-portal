package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

//get api/announcements
public record DuyuruOzetResponse(
        Integer id,
        String olusturan,
        LocalDateTime olusturmaTarihi,
        String baslik,
        String icerik,
        Integer begeniSayisi,
        boolean benBegendimMi
) {
}
