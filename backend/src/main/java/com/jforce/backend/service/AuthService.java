package com.jforce.backend.service;

import com.jforce.backend.exception.BadCredentialsException;
import com.jforce.backend.model.dto.request.LoginRequest;
import com.jforce.backend.model.dto.response.LoginResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JWTService jwtService;
    private final PersonelRepository personelRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JWTService jwtService, PersonelRepository personelRepository, PasswordEncoder passwordEncoder) {
        this.jwtService = jwtService;
        this.personelRepository = personelRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResponse login(LoginRequest loginRequest) {
        Personel personel = personelRepository.findByKullaniciAdi(loginRequest.kullaniciAdi()).orElseThrow(() -> new BadCredentialsException("Kullanıcı adı veya parola hatalı"));
        if (!passwordEncoder.matches(loginRequest.parola(), personel.getParolaHash())){
            throw new BadCredentialsException("Kullanıcı adı veya parola hatalı");
        }
        return new LoginResponse(
                jwtService.generateToken(personel.getPersonelId(), personel.getRol().getRolAdi())
                ,personel.getAdi()
                ,personel.getSoyadi()
                ,personel.getRol().getRolAdi()
        );
    }
}
