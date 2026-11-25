package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.service.LookupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LookupController {

    private final LookupService lookupService;

    public LookupController(LookupService lookupService) {
        this.lookupService = lookupService;
    }

    @GetMapping("/departments")
    public ResponseEntity<List<LookUpResponse>> departmanlariGetir(Authentication authentication) {
        List<LookUpResponse> response = lookupService.departmanlariGetir(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/skills")
    public ResponseEntity<List<LookUpResponse>> yetenekleriGetir(Authentication authentication) {
        List<LookUpResponse> response = lookupService.yetenekleriGetir(authentication.getName());
        return ResponseEntity.ok(response);
    }
}