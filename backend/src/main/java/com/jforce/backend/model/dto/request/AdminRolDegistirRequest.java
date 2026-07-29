package com.jforce.backend.model.dto.request;


import com.jforce.backend.model.enums.RolAdi;
import jakarta.validation.constraints.NotNull;

public record AdminRolDegistirRequest(
        @NotNull
        RolAdi rol
) {
}
