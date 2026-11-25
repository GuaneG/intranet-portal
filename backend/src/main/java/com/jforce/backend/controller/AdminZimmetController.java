package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.EkipmanZimmetAdminResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.service.ZimmetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/assignments")
public class AdminZimmetController {

    private final ZimmetService service;

    public AdminZimmetController(ZimmetService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipmanZimmetAdminResponse>> zimmetler(
            @RequestParam(required = false) Boolean aktif,
            @RequestParam(required = false) String personelId,
            @RequestParam(required = false) Integer ekipmanTipiId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.adminZimmetleriniGetir(
                authentication.getName(),
                aktif,
                personelId,
                ekipmanTipiId,
                page,
                size
        ));
    }

    @PatchMapping("/{zimmetId}/return")
    public ResponseEntity<Void> zimmetiIadeEt(
            @PathVariable String zimmetId,
            Authentication authentication
    ) {
        service.zimmetiIadeEt(authentication.getName(), zimmetId);
        return ResponseEntity.noContent().build();
    }
}
