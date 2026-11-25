package com.jforce.backend.model.dto.response;

import com.jforce.backend.model.enums.AuditEylem;

import java.time.Instant;

public record AuditLogResponse(
        String auditId,
        AuditEylem eylemTipi,
        Instant olusmaZamani,
        AuditAktorResponse aktor,
        AuditHedefResponse hedef,
        String detay
) {
}
