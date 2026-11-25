package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.PersonelGuncelleRequest;
import com.jforce.backend.model.dto.request.PersonelOlusturRequest;
import com.jforce.backend.model.dto.request.YetenekUpdateRequest;
import com.jforce.backend.model.dto.response.PersonelOlusturResponse;
import com.jforce.backend.service.PersonelService;
import com.jforce.backend.service.ProfilService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.jforce.backend.model.dto.response.PersonelYonetimListeResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.service.PersonelYonetimSorguService;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.model.dto.response.PersonelYonetimDetayResponse;
import com.jforce.backend.model.dto.response.AtanabilirYoneticiResponse;

import java.util.List;

@RestController
@RequestMapping("/api/hr/personel")
public class HrPersonelController {

    private final PersonelService personelService;
    private final PersonelYonetimSorguService personelYonetimSorguService;
    private final ProfilService profilService;

    public HrPersonelController(
            PersonelService personelService,
            PersonelYonetimSorguService personelYonetimSorguService,
            ProfilService profilService
    ) {
        this.personelService = personelService;
        this.personelYonetimSorguService = personelYonetimSorguService;
        this.profilService = profilService;
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
    @PatchMapping("/{personelId}/pasiflestir")
    public ResponseEntity<Void> personelPasiflestir(@PathVariable String personelId, Authentication authentication) {
        personelService.personelPasiflestir(authentication.getName(), personelId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{personelId}/aktiflestir")
    public ResponseEntity<Void> personelAktiflestir(
            @PathVariable String personelId,
            Authentication authentication
    ) {
        personelService.personelAktiflestir(
                authentication.getName(),
                personelId
        );

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{personelId}/yetenekler")
    public ResponseEntity<Void> personelYetenekleriniGuncelle(
            @PathVariable String personelId,
            @Valid @RequestBody YetenekUpdateRequest request,
            Authentication authentication
    ) {
        profilService.personelYetenekleriniAdminOlarakGuncelle(
                authentication.getName(),
                personelId,
                request
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{personelId}")
    public ResponseEntity<PersonelYonetimDetayResponse> personelDetayiniGetir(@PathVariable String personelId, Authentication authentication) {
        PersonelYonetimDetayResponse response = personelYonetimSorguService.personelDetayiniGetir(authentication.getName(), personelId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/atanabilir-yoneticiler")
    public ResponseEntity<List<AtanabilirYoneticiResponse>> atanabilirYoneticileriGetir(
            @RequestParam
            Integer departmanId,
            @RequestParam(required = false)
            String haricPersonelId,
            Authentication authentication
    ) {
        List<AtanabilirYoneticiResponse> response = personelYonetimSorguService.atanabilirYoneticileriGetir(authentication.getName(), departmanId, haricPersonelId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<PersonelYonetimListeResponse>> personelleriGetir(
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "20")
            int size,
            @RequestParam(required = false)
            String arama,
            @RequestParam(required = false)
            Integer departmanId,
            @RequestParam(required = false)
            RolAdi rol,
            @RequestParam(required = false)
            Boolean aktif,
            Authentication authentication
    ) {
        SayfaliResponse<PersonelYonetimListeResponse> response =
                personelYonetimSorguService.personelleriGetir(
                        authentication.getName(),
                        page,
                        size,
                        arama,
                        departmanId,
                        rol,
                        aktif
                );

        return ResponseEntity.ok(response);
    }
}
