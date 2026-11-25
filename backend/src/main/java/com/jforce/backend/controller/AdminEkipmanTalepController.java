package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.EkipmanTalepKararItemResponse;
import com.jforce.backend.model.dto.response.SayfaliResponse;
import com.jforce.backend.model.enums.TalepDurum;
import com.jforce.backend.service.EkipmanTalepKararService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/equipment-requests")
public class AdminEkipmanTalepController {

    private final EkipmanTalepKararService service;

    public AdminEkipmanTalepController(EkipmanTalepKararService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SayfaliResponse<EkipmanTalepKararItemResponse>> adminTalepleri(
            @RequestParam(required = false) TalepDurum durum,
            @RequestParam(required = false) Integer ekipmanTipiId,
            @RequestParam(required = false) String personelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(service.adminTalepleriniGetir(
                authentication.getName(), durum, ekipmanTipiId, personelId, page, size
        ));
    }
}
