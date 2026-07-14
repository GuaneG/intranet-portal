package com.jforce.backend.model.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record DuyuruCreateRequest(
        @NotBlank @Size(max = 100)
        String baslik,
        @NotBlank
        String icerik

) {
}
