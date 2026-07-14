package com.jforce.backend.model.dto.response;

public record LoginResponse(
        String token,
        String ad,
        String soyad,
        String rol
) {

}
