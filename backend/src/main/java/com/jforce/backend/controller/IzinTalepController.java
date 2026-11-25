package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.IzinTalepCreateRequest;
import com.jforce.backend.model.dto.response.IzinTalepResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.IzinTalepService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/leave-requests")
public class IzinTalepController {

    private final IzinTalepService izinTalepService;

    public IzinTalepController(IzinTalepService izinTalepService) {
        this.izinTalepService = izinTalepService;
    }

    @PostMapping
    public ResponseEntity<IzinTalepResponse> talepOlustur(
            @Valid @RequestBody IzinTalepCreateRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(izinTalepService.talepOlustur(
                        authentication.getName(),
                        request
                ));
    }

    @GetMapping("/my")
    public ResponseEntity<SayfaliResponse<IzinTalepResponse>>
    kendiTalepleriniGetir(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) TalepDurum durum,
            Authentication authentication
    ) {
        return ResponseEntity.ok(izinTalepService.kendiTalepleriniGetir(
                authentication.getName(),
                page,
                size,
                durum
        ));
    }

    @PatchMapping("/{talepId}/cancel")
    public ResponseEntity<Void> talebiIptalEt(
            @PathVariable String talepId,
            Authentication authentication
    ) {
        izinTalepService.talebiIptalEt(
                authentication.getName(),
                talepId
        );
        return ResponseEntity.noContent().build();
    }
}
