package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.IzinTalepKararItemResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.IzinTalepKararService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/leave-requests")
public class AdminIzinTalepKararController {

    private final IzinTalepKararService izinTalepKararService;

    public AdminIzinTalepKararController(
            IzinTalepKararService izinTalepKararService
    ) {
        this.izinTalepKararService = izinTalepKararService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<IzinTalepKararItemResponse>>
    adminTalepleriniGetir(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) TalepDurum durum,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                izinTalepKararService.adminTalepleriniGetir(
                        authentication.getName(),
                        page,
                        size,
                        durum
                )
        );
    }
}
