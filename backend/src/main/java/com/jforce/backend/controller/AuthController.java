package com.jforce.backend.controller;

import com.jforce.backend.model.dto.request.LoginRequest;
import com.jforce.backend.model.dto.response.LoginResponse;
import com.jforce.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest loginRequest) {
        return authService.login(loginRequest);
    }
}
