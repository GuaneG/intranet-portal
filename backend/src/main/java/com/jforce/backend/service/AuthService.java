package com.jforce.backend.service;

import com.jforce.backend.exception.BadCredentialsException;
import com.jforce.backend.model.dto.request.LoginRequest;
import com.jforce.backend.model.dto.response.LoginResponse;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JWTService jwtService;
    private final PersonelRepository personelRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public AuthService(JWTService jwtService, PersonelRepository personelRepository, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService, AuditService auditService) {
        this.jwtService = jwtService;
        this.personelRepository = personelRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }


    //hem token hemde refreshtoken'ı dönebilmek için
    public record LoginResult(LoginResponse loginResponse, String refreshToken) {}


    public LoginResult login(LoginRequest loginRequest) {
        Personel personel = personelRepository.findByKullaniciAdi(loginRequest.kullaniciAdi()).orElse(null);
        if ( personel == null || !passwordEncoder.matches(loginRequest.parola(), personel.getParolaHash())){
            auditService.logKaydet(AuditEylem.LOGIN_BASARISIZ,null, loginRequest.kullaniciAdi(),"hatalı uname/parola");
            throw new BadCredentialsException("Kullanıcı adı veya parola hatalı.");
        }
        String rawRefreshToken = refreshTokenService.createToken(personel);
        auditService.logKaydet(AuditEylem.LOGIN_BASARILI,personel, personel.getKullaniciAdi(), "Login başarılı.");
        return new LoginResult(
                new LoginResponse(jwtService.generateToken(personel.getPersonelId(), personel.getRol().getRolAdi())
                ,personel.getAdi()
                ,personel.getSoyadi()
                ,personel.getRol().getRolAdi()),rawRefreshToken)
        ;
    }

    //hem refresh token hem access token(jwt) dönebilmek için record
    public record NewRefreshAndAccessToken(String rawRefreshToken,String accessToken) {
    }


    public NewRefreshAndAccessToken refresh(String rawRefreshToken){
        RefreshTokenService.RotationResult rt = refreshTokenService.rotate(rawRefreshToken);
        String accessToken = jwtService.generateToken(rt.personel().getPersonelId(),rt.personel().getRol().getRolAdi());
        return new NewRefreshAndAccessToken(rt.newRefreshToken(), accessToken);
    }


    public void logout(String rawRefreshToken){
        Personel p = refreshTokenService.revokeToken(rawRefreshToken);
        auditService.logKaydet(AuditEylem.LOGOUT,p,p != null ? p.getKullaniciAdi() : null,"Çıkış yapıldı");
    }
}
