package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.dto.response.ZimmetResponse;
import com.jforce.backend.service.ZimmetService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class ZimmetController {

    private final ZimmetService service;

    public ZimmetController(ZimmetService service) {
        this.service = service;
    }

    @GetMapping("/my")
    public ResponseEntity<SayfaliResponse<ZimmetResponse>> kendiZimmetlerim(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.kendiZimmetleriniGetir(
                authentication.getName(), page, size
        ));
    }
}
