package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.HiyerarsiResponse;
import com.jforce.backend.model.dto.response.YoneticiHiyerarsisiResponse;
import com.jforce.backend.model.dto.response.YoneticiZinciriResponse;
import com.jforce.backend.service.YoneticiHiyerarsisiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ekip-dizini")
public class YoneticiHiyerarsisiController {

    private final YoneticiHiyerarsisiService yoneticiHiyerarsisiService;

    public YoneticiHiyerarsisiController(
            YoneticiHiyerarsisiService yoneticiHiyerarsisiService
    ) {
        this.yoneticiHiyerarsisiService = yoneticiHiyerarsisiService;
    }

    @GetMapping("/hiyerarsi")
    public ResponseEntity<HiyerarsiResponse> hiyerarsiyiGetir(
            @RequestParam(required = false) String arama,
            @RequestParam(required = false) Integer departmanId,
            @RequestParam(required = false) Integer yetenekId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                yoneticiHiyerarsisiService.tumHiyerarsiyiGetir(
                        authentication.getName(),
                        departmanId,
                        yetenekId,
                        arama
                )
        );
    }

    @GetMapping("/yonetici-zincirim")
    public ResponseEntity<YoneticiZinciriResponse> yoneticiZincirimiGetir(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                yoneticiHiyerarsisiService.yoneticiZinciriniGetir(
                        authentication.getName()
                )
        );
    }

    @GetMapping("/yonetici-hiyerarsim")
    public ResponseEntity<YoneticiHiyerarsisiResponse> yoneticiHiyerarsimiGetir(
            @RequestParam(required = false) String arama,
            @RequestParam(required = false) Integer departmanId,
            @RequestParam(required = false) Integer yetenekId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                yoneticiHiyerarsisiService.yoneticiHiyerarsisiniGetir(
                        authentication.getName(),
                        departmanId,
                        yetenekId,
                        arama
                )
        );
    }
}
