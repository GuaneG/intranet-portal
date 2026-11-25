package com.jforce.backend.model.dto.request;

import com.jforce.backend.model.enums.EkipmanDurum;
import jakarta.validation.constraints.NotNull;

public record EkipmanDurumUpdateRequest(
        @NotNull
        EkipmanDurum durum,
        String neden
) {
}
