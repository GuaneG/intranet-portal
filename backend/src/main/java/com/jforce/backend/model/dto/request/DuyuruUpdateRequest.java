package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.Size;

public record DuyuruUpdateRequest(
        @Size(max = 100)
        String baslik,
        String icerik
) {
}
