package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.EkipmanCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanDurumUpdateRequest;
import com.jforce.backend.model.dto.request.EkipmanUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.EkipmanDurum;
import com.jforce.backend.service.EkipmanEnvanterService;
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
@RequestMapping("/api/admin/equipment")
public class AdminEkipmanController {

    private final EkipmanEnvanterService ekipmanEnvanterService;

    public AdminEkipmanController(EkipmanEnvanterService ekipmanEnvanterService) {
        this.ekipmanEnvanterService = ekipmanEnvanterService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipmanResponse>> ekipmanlariGetir(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer ekipmanTipiId,
            @RequestParam(required = false) EkipmanDurum durum,
            @RequestParam(required = false) String arama,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ekipmanEnvanterService.ekipmanlariGetir(
                authentication.getName(),
                ekipmanTipiId,
                durum,
                arama,
                page,
                size
        ));
    }

    @PostMapping
    public ResponseEntity<EkipmanResponse> ekipmanOlustur(
            @Valid @RequestBody EkipmanCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ekipmanEnvanterService.ekipmanOlustur(
                        authentication.getName(),
                        request
                )
        );
    }

    @PatchMapping("/{ekipmanId}")
    public ResponseEntity<Void> metadataGuncelle(
            @PathVariable String ekipmanId,
            @Valid @RequestBody EkipmanUpdateRequest request,
            Authentication authentication
    ) {
        ekipmanEnvanterService.metadataGuncelle(
                authentication.getName(),
                ekipmanId,
                request
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{ekipmanId}/status")
    public ResponseEntity<Void> durumGuncelle(
            @PathVariable String ekipmanId,
            @Valid @RequestBody EkipmanDurumUpdateRequest request,
            Authentication authentication
    ) {
        ekipmanEnvanterService.durumGuncelle(
                authentication.getName(),
                ekipmanId,
                request
        );
        return ResponseEntity.noContent().build();
    }
}
