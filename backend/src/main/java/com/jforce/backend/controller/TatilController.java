package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.TatilKaydetRequest;
import com.jforce.backend.model.dto.response.TatilResponse;
import com.jforce.backend.service.TatilTakvimiService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/holidays")
public class TatilController {

    private final TatilTakvimiService tatilTakvimiService;

    public TatilController(
            TatilTakvimiService tatilTakvimiService
    ) {
        this.tatilTakvimiService = tatilTakvimiService;
    }

    @DeleteMapping("/{tatilId}")
    public ResponseEntity<Void> tatilSil(
            @PathVariable
            Integer tatilId,

            Authentication authentication
    ) {
        tatilTakvimiService.tatilSil(
                authentication.getName(),
                tatilId
        );

        return ResponseEntity
                .noContent()
                .build();
    }

    @GetMapping
    public ResponseEntity<List<TatilResponse>> tatilleriListele(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate baslangicTarihi,

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate bitisTarihi,

            Authentication authentication
    ) {
        List<TatilResponse> response =
                tatilTakvimiService.tatilleriListele(
                        authentication.getName(),
                        baslangicTarihi,
                        bitisTarihi
                );

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{tatilId}")
    public ResponseEntity<Void> tatilGuncelle(
            @PathVariable
            Integer tatilId,

            @Valid
            @RequestBody
            TatilKaydetRequest request,

            Authentication authentication
    ) {
        tatilTakvimiService.tatilGuncelle(
                authentication.getName(),
                tatilId,
                request
        );

        return ResponseEntity
                .noContent()
                .build();
    }

    @PostMapping
    public ResponseEntity<TatilResponse> tatilOlustur(
            @Valid
            @RequestBody
            TatilKaydetRequest request,

            Authentication authentication
    ) {
        TatilResponse response =
                tatilTakvimiService.tatilOlustur(
                        authentication.getName(),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}