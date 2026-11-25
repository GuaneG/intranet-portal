package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.IzinTalepRedRequest;
import com.jforce.backend.model.dto.response.IzinTalepKararItemResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.IzinTalepKararService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/leave-requests")
public class IzinTalepKararController {

    private final IzinTalepKararService izinTalepKararService;

    public IzinTalepKararController(
            IzinTalepKararService izinTalepKararService
    ) {
        this.izinTalepKararService = izinTalepKararService;
    }

    @GetMapping("/team")
    public ResponseEntity<SayfaliResponse<IzinTalepKararItemResponse>>
    takimTalepleriniGetir(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(required = false) TalepDurum durum,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                izinTalepKararService.takimTalepleriniGetir(
                        authentication.getName(),
                        page,
                        size,
                        durum
                )
        );
    }

    @PatchMapping("/{talepId}/approve")
    public ResponseEntity<Void> talebiOnayla(
            @PathVariable String talepId,
            Authentication authentication
    ) {
        izinTalepKararService.talebiOnayla(
                authentication.getName(),
                talepId
        );
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{talepId}/reject")
    public ResponseEntity<Void> talebiReddet(
            @PathVariable String talepId,
            @Valid @RequestBody IzinTalepRedRequest request,
            Authentication authentication
    ) {
        izinTalepKararService.talebiReddet(
                authentication.getName(),
                talepId,
                request
        );
        return ResponseEntity.noContent().build();
    }
}
