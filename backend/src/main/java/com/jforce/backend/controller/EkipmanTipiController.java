package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.EkipmanTipiLookupResponse;
import com.jforce.backend.service.EkipmanTipiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/equipment-types")
public class EkipmanTipiController {

    private final EkipmanTipiService ekipmanTipiService;

    public EkipmanTipiController(EkipmanTipiService ekipmanTipiService) {
        this.ekipmanTipiService = ekipmanTipiService;
    }

    @GetMapping
    public ResponseEntity<List<EkipmanTipiLookupResponse>> aktifTipleriGetir(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                ekipmanTipiService.aktifTipleriGetir(authentication.getName())
        );
    }
}
