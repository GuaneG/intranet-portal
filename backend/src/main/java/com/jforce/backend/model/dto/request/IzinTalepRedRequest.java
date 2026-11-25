package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;

public record IzinTalepRedRequest(
        @NotNull String kararNotu
) {
}
