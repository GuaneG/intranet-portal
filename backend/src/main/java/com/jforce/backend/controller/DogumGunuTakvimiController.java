package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.DogumGunuTakvimResponse;
import com.jforce.backend.service.DogumGunuTakvimiService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/birthdays")
public class DogumGunuTakvimiController {

    private final DogumGunuTakvimiService dogumGunuTakvimiService;

    public DogumGunuTakvimiController(
            DogumGunuTakvimiService dogumGunuTakvimiService
    ) {
        this.dogumGunuTakvimiService = dogumGunuTakvimiService;
    }

    @GetMapping
    public List<DogumGunuTakvimResponse> dogumGunleriniGetir(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate baslangicTarihi,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate bitisTarihi,
            Authentication authentication
    ) {
        return dogumGunuTakvimiService.dogumGunleriniGetir(
                authentication.getName(),
                baslangicTarihi,
                bitisTarihi
        );
    }
}
