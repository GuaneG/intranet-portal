package com.jforce.backend.model.dto.request;

public record EkipmanUpdateRequest(
        String marka,
        String model,
        String seriNo
) {
}
