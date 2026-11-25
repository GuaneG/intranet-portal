package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.YetenekAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.service.YetenekYonetimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
public class YetenekYonetimController {

    private final YetenekYonetimService yetenekYonetimService;
    public YetenekYonetimController(YetenekYonetimService yetenekYonetimService) {
        this.yetenekYonetimService = yetenekYonetimService;
    }

    @PostMapping
    public ResponseEntity<LookUpResponse> yetenekOlustur(@Valid @RequestBody YetenekAdiRequest request, Authentication authentication) {
        LookUpResponse response = yetenekYonetimService.yetenekOlustur(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{yetenekId}")
    public ResponseEntity<Void> yetenekGuncelle(@PathVariable Integer yetenekId, @Valid @RequestBody YetenekAdiRequest request, Authentication authentication) {
        yetenekYonetimService.yetenekGuncelle(authentication.getName(), yetenekId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{yetenekId}")
    public ResponseEntity<Void> yetenekSil(
            @PathVariable Integer yetenekId,
            Authentication authentication
    ) {
        yetenekYonetimService.yetenekSil(
                authentication.getName(),
                yetenekId
        );
        return ResponseEntity.noContent().build();
    }
}
