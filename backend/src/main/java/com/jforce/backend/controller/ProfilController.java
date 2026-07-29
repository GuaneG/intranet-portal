package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.ParolaDegistirRequest;
import com.jforce.backend.service.ParolaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profil")
public class ProfilController {

    private final ParolaService parolaService;

    public ProfilController(ParolaService parolaService) {
        this.parolaService = parolaService;
    }
    //authentication objesi isteği yapan kullanıcının kimliğine ihtiyac duyunca lazım olur
    @PatchMapping("/parola")
    public ResponseEntity<Void> parolaDegistir(@Valid @RequestBody ParolaDegistirRequest request, Authentication authentication) {
        parolaService.parolaDegistir(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}