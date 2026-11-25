package com.jforce.backend.model.dto.response;

public record AuditAktorResponse(
        String personelId,
        String adSoyad,
        String kullaniciAdi
) {
}
