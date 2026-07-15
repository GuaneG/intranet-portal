package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record YetenekUpdateRequest(
        @NotNull
        List<Integer> yetenekIdleri
) {
}
