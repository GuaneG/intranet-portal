package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.PersonelGuncelleRequest;
import com.jforce.backend.model.dto.request.PersonelOlusturRequest;
import com.jforce.backend.model.dto.response.PersonelOlusturResponse;
import com.jforce.backend.service.PersonelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/hr/personel")
public class HrPersonelController {

    private final PersonelService personelService;

    public HrPersonelController(PersonelService personelService) {
        this.personelService = personelService;
    }

    //authentication objesi isteği yapan kullanıcının kimliğine ihtiyac duyunca lazım olur
    @PostMapping
    public ResponseEntity<PersonelOlusturResponse> personelOlustur(@Valid @RequestBody PersonelOlusturRequest request, Authentication authentication) {
        PersonelOlusturResponse response = personelService.personelOlustur(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @PatchMapping("/{personelId}")
    public ResponseEntity<Void> personelGuncelle(@PathVariable String personelId, @Valid @RequestBody PersonelGuncelleRequest request, Authentication authentication) {
        personelService.personelGuncelle(authentication.getName(), personelId, request);
        return ResponseEntity.noContent().build();
    }
}