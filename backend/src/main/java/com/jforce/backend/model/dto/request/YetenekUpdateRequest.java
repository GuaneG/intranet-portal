package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record YetenekUpdateRequest(
        @NotNull //listenin null olmasını engeller
        List<@NotNull @Positive Integer> yetenekIdleri
            //listenin içindeki her id null olmamasını ve positive olmasını sağlar
) {
}
