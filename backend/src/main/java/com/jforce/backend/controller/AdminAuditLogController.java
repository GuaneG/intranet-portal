package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.AuditLogResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.service.AdminAuditLogSorguService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/admin/audit-logs")
public class AdminAuditLogController {

    private final AdminAuditLogSorguService adminAuditLogSorguService;

    public AdminAuditLogController(
            AdminAuditLogSorguService adminAuditLogSorguService
    ) {
        this.adminAuditLogSorguService = adminAuditLogSorguService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<AuditLogResponse>> auditKayitlariniGetir(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate baslangicTarihi,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate bitisTarihi,
            @RequestParam(required = false) AuditEylem eylemTipi,
            @RequestParam(required = false) String aktorPersonelId,
            @RequestParam(required = false) String hedefPersonelId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                adminAuditLogSorguService.auditKayitlariniGetir(
                        authentication.getName(),
                        page,
                        size,
                        baslangicTarihi,
                        bitisTarihi,
                        eylemTipi,
                        aktorPersonelId,
                        hedefPersonelId
                )
        );
    }
}
