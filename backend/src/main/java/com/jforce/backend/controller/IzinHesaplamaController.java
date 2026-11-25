package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.IsGunuOnizlemeResponse;
import com.jforce.backend.service.IzinOnizlemeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/leave-requests")
public class IzinHesaplamaController {

    private final IzinOnizlemeService izinOnizlemeService;

    public IzinHesaplamaController(
            IzinOnizlemeService izinOnizlemeService
    ) {
        this.izinOnizlemeService = izinOnizlemeService;
    }

    @GetMapping("/workday-preview")
    public ResponseEntity<IsGunuOnizlemeResponse>
    isGunuOnizle(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate baslangicTarihi,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate bitisTarihi,

            Authentication authentication
    ) {
        IsGunuOnizlemeResponse response =
                izinOnizlemeService.isGunuOnizle(
                        authentication.getName(),
                        baslangicTarihi,
                        bitisTarihi
                );

        return ResponseEntity.ok(response);
    }
}