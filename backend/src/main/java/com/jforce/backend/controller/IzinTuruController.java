package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.IzinTuruLookupResponse;
import com.jforce.backend.service.IzinTuruService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leave-types")
public class IzinTuruController {

    private final IzinTuruService izinTuruService;

    public IzinTuruController(IzinTuruService izinTuruService) {
        this.izinTuruService = izinTuruService;
    }

    @GetMapping
    public ResponseEntity<List<IzinTuruLookupResponse>> izinTurleriniGetir(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                izinTuruService.izinTurleriniGetir(authentication.getName())
        );
    }
}
