package com.jforce.backend.controller;

import com.jforce.backend.service.ProfilFotografiDosyasi;
import com.jforce.backend.service.ProfilFotografiService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/profil-fotograflari")
public class ProfilFotografiController {

    private final ProfilFotografiService profilFotografiService;

    public ProfilFotografiController(ProfilFotografiService profilFotografiService) {
        this.profilFotografiService = profilFotografiService;
    }

    @GetMapping("/{dosyaAnahtari}")
    public ResponseEntity<Resource> profilFotografiniGetir(
            @PathVariable String dosyaAnahtari,
            Authentication authentication
    ) {
        ProfilFotografiDosyasi dosya = profilFotografiService.profilFotografiniGetir(
                authentication.getName(),
                dosyaAnahtari
        );

        return ResponseEntity.ok()
                .contentType(dosya.mediaType())
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(dosya.resource());
    }
}
