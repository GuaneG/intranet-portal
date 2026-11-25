package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.EkipmanTipiCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanTipiUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanTipiResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.service.EkipmanTipiService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/equipment-types")
public class AdminEkipmanTipiController {

    private final EkipmanTipiService ekipmanTipiService;

    public AdminEkipmanTipiController(EkipmanTipiService ekipmanTipiService) {
        this.ekipmanTipiService = ekipmanTipiService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipmanTipiResponse>> tipleriGetir(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) String arama,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ekipmanTipiService.adminTipleriGetir(
                authentication.getName(),
                arama,
                aktif,
                page,
                size
        ));
    }

    @PostMapping
    public ResponseEntity<EkipmanTipiResponse> tipOlustur(
            @Valid @RequestBody EkipmanTipiCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ekipmanTipiService.tipOlustur(
                        authentication.getName(),
                        request
                )
        );
    }

    @PatchMapping("/{ekipmanTipiId}")
    public ResponseEntity<Void> tipGuncelle(
            @PathVariable Integer ekipmanTipiId,
            @Valid @RequestBody EkipmanTipiUpdateRequest request,
            Authentication authentication
    ) {
        ekipmanTipiService.tipGuncelle(
                authentication.getName(),
                ekipmanTipiId,
                request
        );
        return ResponseEntity.noContent().build();
    }
}
