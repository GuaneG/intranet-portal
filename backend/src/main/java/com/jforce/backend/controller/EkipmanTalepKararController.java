package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.EkipmanTalepOnayRequest;
import com.jforce.backend.model.dto.request.EkipmanTalepRedRequest;
import com.jforce.backend.model.dto.response.EkipmanTalepKararItemResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.dto.response.UygunEkipmanResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.EkipmanTalepKararService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment-requests")
public class EkipmanTalepKararController {

    private final EkipmanTalepKararService service;

    public EkipmanTalepKararController(EkipmanTalepKararService service) {
        this.service = service;
    }

    @GetMapping("/team")
    public ResponseEntity<SayfaliResponse<EkipmanTalepKararItemResponse>> takimTalepleri(
            @RequestParam(required = false) TalepDurum durum,
            @RequestParam(required = false) Integer ekipmanTipiId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.takimTalepleriniGetir(
                authentication.getName(), durum, ekipmanTipiId, page, size
        ));
    }

    @GetMapping("/{talepId}/available-equipment")
    public ResponseEntity<SayfaliResponse<UygunEkipmanResponse>> uygunEkipmanlar(
            @PathVariable String talepId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.uygunEkipmanlariGetir(
                authentication.getName(), talepId, page, size
        ));
    }

    @PatchMapping("/{talepId}/approve")
    public ResponseEntity<Void> talebiOnayla(
            @PathVariable String talepId,
            @Valid @RequestBody EkipmanTalepOnayRequest request,
            Authentication authentication
    ) {
        service.talebiOnayla(authentication.getName(), talepId, request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{talepId}/reject")
    public ResponseEntity<Void> talebiReddet(
            @PathVariable String talepId,
            @Valid @RequestBody EkipmanTalepRedRequest request,
            Authentication authentication
    ) {
        service.talebiReddet(authentication.getName(), talepId, request);
        return ResponseEntity.noContent().build();
    }
}
