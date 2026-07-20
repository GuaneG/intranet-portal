package com.jforce.backend.model.dto.response;

import java.time.LocalDateTime;

public record ErrorResponse(
        Integer status,
        String message,
        LocalDateTime timestamp,
        String path


) {
}
