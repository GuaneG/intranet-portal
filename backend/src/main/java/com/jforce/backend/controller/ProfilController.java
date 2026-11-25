package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.ParolaDegistirRequest;
import com.jforce.backend.model.dto.response.ProfilBilgileriResponse;
import com.jforce.backend.model.dto.response.ProfilFotografiResponse;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.ProfilFotografiService;
import com.jforce.backend.service.ProfilService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.jforce.backend.model.dto.request.ProfilUpdateRequest;
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profil")
public class ProfilController {

    private final ParolaService parolaService;
    private final ProfilService profilService;
    private final ProfilFotografiService profilFotografiService;

    public ProfilController(
            ParolaService parolaService,
            ProfilService profilService,
            ProfilFotografiService profilFotografiService
    ) {
        this.parolaService = parolaService;
        this.profilService = profilService;
        this.profilFotografiService = profilFotografiService;
    }
    //authentication objesi isteği yapan kullanıcının kimliğine ihtiyac duyunca lazım olur
    @PatchMapping("/parola")
    public ResponseEntity<Void> parolaDegistir(@Valid @RequestBody ParolaDegistirRequest request, Authentication authentication) {
        parolaService.parolaDegistir(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/yetenekler")
    public ResponseEntity<Void> profilYeteneklerimiGuncelle(@Valid @RequestBody YetenekUpdateRequest request, Authentication authentication) {
        profilService.profilYeteneklerimiGuncelle(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping(value = "/fotograf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProfilFotografiResponse> profilFotografimiGuncelle(
            @RequestPart("dosya") MultipartFile dosya,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                profilFotografiService.profilFotografimiGuncelle(
                        authentication.getName(),
                        dosya
                )
        );
    }

    @DeleteMapping("/fotograf")
    public ResponseEntity<Void> profilFotografimiSil(Authentication authentication) {
        profilFotografiService.profilFotografimiSil(authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ProfilBilgileriResponse> profilimiGetir(Authentication authentication) {
        return ResponseEntity.ok(profilService.profilimiGetir(authentication.getName()));
    }

    @PatchMapping
    public ResponseEntity<Void> profilimiGuncelle(@Valid @RequestBody ProfilUpdateRequest request, Authentication authentication) {
        profilService.profilimiGuncelle(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
