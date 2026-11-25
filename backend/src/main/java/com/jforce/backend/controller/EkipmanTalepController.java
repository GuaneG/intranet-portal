package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.EkipmanTalepCreateRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepUpdateRequest;
import com.jforce.backend.model.dto.response.EkipmanTalepResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.EkipmanTalepService;
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
@RequestMapping("/api/equipment-requests")
public class EkipmanTalepController {

    private final EkipmanTalepService ekipmanTalepService;

    public EkipmanTalepController(EkipmanTalepService ekipmanTalepService) {
        this.ekipmanTalepService = ekipmanTalepService;
    }

    @PostMapping
    public ResponseEntity<EkipmanTalepResponse> talepOlustur(
            @Valid @RequestBody EkipmanTalepCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ekipmanTalepService.talepOlustur(authentication.getName(), request)
        );
    }

    @GetMapping("/my")
    public ResponseEntity<SayfaliResponse<EkipmanTalepResponse>> kendiTalepleri(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TalepDurum durum,
            @RequestParam(required = false) Integer ekipmanTipiId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(ekipmanTalepService.kendiTalepleriniGetir(
                authentication.getName(), durum, ekipmanTipiId, page, size
        ));
    }

    @PatchMapping("/{talepId}")
    public ResponseEntity<Void> talepGuncelle(
            @PathVariable String talepId,
            @Valid @RequestBody EkipmanTalepUpdateRequest request,
            Authentication authentication
    ) {
        ekipmanTalepService.talepGuncelle(authentication.getName(), talepId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{talepId}/cancel")
    public ResponseEntity<Void> talepIptalEt(
            @PathVariable String talepId,
            Authentication authentication
    ) {
        ekipmanTalepService.talepIptalEt(authentication.getName(), talepId);
        return ResponseEntity.noContent().build();
    }
}
