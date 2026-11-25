package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.RezervasyonRequest;
import com.jforce.backend.model.dto.request.RezervasyonIptalRequest;
import com.jforce.backend.model.dto.response.OdaResponse;
import com.jforce.backend.model.dto.response.RezervasyonResponse;
import com.jforce.backend.model.dto.response.RezervasyonGecmisResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.RezervasyonDurum;
import com.jforce.backend.service.RezervasyonService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
@RequestMapping("/api")
public class RezervasyonController {

    private final RezervasyonService rezervasyonService;

    public RezervasyonController(RezervasyonService rezervasyonService) {
        this.rezervasyonService = rezervasyonService;
    }

    @GetMapping("/rooms")
    public List<OdaResponse> odalariGetir(Authentication authentication) {
        return rezervasyonService.odalariGetir(authentication.getName());
    }

    @GetMapping("/reservations")
    public List<RezervasyonResponse> takvimiGetir(
            Authentication authentication,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate baslangicTarihi,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate bitisTarihi,
            @RequestParam(required = false)
            @Positive
            Integer odaId
    ) {
        return rezervasyonService.takvimiGetir(
                authentication.getName(),
                baslangicTarihi,
                bitisTarihi,
                odaId
        );
    }

    @PostMapping("/reservations")
    public ResponseEntity<RezervasyonResponse> rezervasyonOlustur(
            Authentication authentication,
            @Valid @RequestBody RezervasyonRequest request
    ) {
        RezervasyonResponse response = rezervasyonService.rezervasyonOlustur(
                authentication.getName(),
                request
        );
        return ResponseEntity
                .created(URI.create("/api/reservations/" + response.id()))
                .body(response);
    }

    @GetMapping("/reservations/my")
    public ResponseEntity<SayfaliResponse<RezervasyonGecmisResponse>>
    kendiGecmisiniGetir(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) RezervasyonDurum durum,
            @RequestParam(required = false) @Positive Integer odaId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baslangic,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bitis
    ) {
        return ResponseEntity.ok(rezervasyonService.kendiGecmisiniGetir(
                authentication.getName(), page, size, durum, odaId,
                baslangic, bitis
        ));
    }

    @GetMapping("/admin/reservations")
    public ResponseEntity<SayfaliResponse<RezervasyonGecmisResponse>>
    adminGecmisiniGetir(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) RezervasyonDurum durum,
            @RequestParam(required = false) @Positive Integer odaId,
            @RequestParam(required = false) String olusturanId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baslangic,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bitis
    ) {
        return ResponseEntity.ok(rezervasyonService.adminGecmisiniGetir(
                authentication.getName(), page, size, durum, odaId,
                olusturanId, baslangic, bitis
        ));
    }

    @PatchMapping("/reservations/{rezervasyonId}/cancel")
    public ResponseEntity<Void> rezervasyonIptalEt(
            Authentication authentication,
            @PathVariable String rezervasyonId,
            @Valid @RequestBody(required = false) RezervasyonIptalRequest request
    ) {
        rezervasyonService.rezervasyonIptalEt(
                authentication.getName(), rezervasyonId, request
        );
        return ResponseEntity.noContent().build();
    }
}
