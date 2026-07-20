package com.jforce.backend.model.dto.request;

import com.jforce.backend.model.enums.EkipmanDurum;
import jakarta.validation.constraints.Size;

public record EkipmanUpdateRequest(
        @Size(max = 50)
        String marka,
        @Size(max = 50)
        String model,
        @Size(max = 100)
        String seriNo,
        EkipmanDurum durum
) {
}
