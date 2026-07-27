package com.jforce.backend.controller;

import com.jforce.backend.model.dto.response.DashboardOzetResponse;
import com.jforce.backend.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }
                                                    //bu obje o an giriş yapmış kullanıcıyı temsil eden Spring Security objesi
    @GetMapping("/ozet")                          //bu kodla "SecurityContextHolder.getContext().setAuthentication(authentication);" oluşturduğumuz objeyi alıyoruz
    public DashboardOzetResponse getDashboardOzet(Authentication authentication) {
        String personelId = authentication.getName(); // JWTFilter principal = sub = personelId
        return dashboardService.ozet(personelId);
    }
}
