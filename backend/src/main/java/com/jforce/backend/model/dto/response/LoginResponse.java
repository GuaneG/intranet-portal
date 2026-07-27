package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.RolAdi;

public record LoginResponse(
        String token,
        String ad,
        String soyad,
        RolAdi rol
) {

}
