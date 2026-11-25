package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.EkipDiziniResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.service.EkipDiziniService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ekip-dizini")
public class EkipDiziniController {

    private final EkipDiziniService ekipDiziniService;

    public EkipDiziniController(EkipDiziniService ekipDiziniService) {
        this.ekipDiziniService = ekipDiziniService;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipDiziniResponse>> ekipDizininiGetir(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer departmanId, @RequestParam(required = false) Integer yetenekId,
            @RequestParam(required = false) String arama, Authentication authentication
    ) {
        SayfaliResponse<EkipDiziniResponse> response = ekipDiziniService.ekipDizininiGetir(
                        authentication.getName(),
                        page,
                        size,
                        departmanId,
                        yetenekId,
                        arama
                );
        return ResponseEntity.ok(response);
    }
}