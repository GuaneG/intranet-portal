package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.DepartmanAdiRequest;
import com.jforce.backend.model.dto.response.LookUpResponse;
import com.jforce.backend.service.DepartmanYonetimService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/departments")
public class DepartmanYonetimController {

    private final DepartmanYonetimService
            departmanYonetimService;

    public DepartmanYonetimController(
            DepartmanYonetimService departmanYonetimService
    ) {
        this.departmanYonetimService =
                departmanYonetimService;
    }

    @PostMapping
    public ResponseEntity<LookUpResponse> departmanOlustur(
            @Valid
            @RequestBody
            DepartmanAdiRequest request,
            Authentication authentication
    ) {
        LookUpResponse response =
                departmanYonetimService.departmanOlustur(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{departmanId}")
    public ResponseEntity<Void> departmanGuncelle(
            @PathVariable
            Integer departmanId,
            @Valid
            @RequestBody
            DepartmanAdiRequest request,
            Authentication authentication
    ) {
        departmanYonetimService.departmanGuncelle(
                authentication.getName(),
                departmanId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{departmanId}")
    public ResponseEntity<Void> departmanSil(
            @PathVariable Integer departmanId,
            Authentication authentication
    ) {
        departmanYonetimService.departmanSil(
                authentication.getName(),
                departmanId
        );

        return ResponseEntity.noContent().build();
    }
}
