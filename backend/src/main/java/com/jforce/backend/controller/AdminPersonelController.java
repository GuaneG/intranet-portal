package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.AdminRolDegistirRequest;
import com.jforce.backend.model.dto.request.AdminParolaSifirlaRequest;
import com.jforce.backend.service.ParolaService;
import com.jforce.backend.service.PersonelService;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/personel")
public class AdminPersonelController {
    private final PersonelService personelService;
    private final ParolaService parolaService;

    public AdminPersonelController(PersonelService personelService, ParolaService parolaService) {
        this.personelService = personelService;
        this.parolaService = parolaService;
    }
    @PatchMapping("/{personelId}/rol")
    public ResponseEntity<Void> rolDegistir(@Valid @RequestBody AdminRolDegistirRequest req,
                                            @PathVariable("personelId") String hedefPersonelId,
                                            Authentication authentication) {
        String aktorId = authentication.getName();
        personelService.rolDegistir(aktorId,hedefPersonelId,req.rol());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{personelId}/parola-sifirla")
    public ResponseEntity<Void> parolaSifirla(
            @Valid @RequestBody AdminParolaSifirlaRequest request,
            @PathVariable("personelId") String hedefPersonelId,
            Authentication authentication
    ) {
        parolaService.adminParolaSifirla(
                authentication.getName(),
                hedefPersonelId,
                request.geciciParola()
        );
        return ResponseEntity.noContent().build();
    }
}
