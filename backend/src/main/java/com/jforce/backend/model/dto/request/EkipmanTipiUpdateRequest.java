package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.Size;

public record EkipmanTipiUpdateRequest(
        @Size(max = 50)
        String ad,
        Boolean aktifMi
) {
}
