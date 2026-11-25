package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.Size;

public record RezervasyonIptalRequest(
        @Size(max = 500) String iptalNedeni
) {
}
