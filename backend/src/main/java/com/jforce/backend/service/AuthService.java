package com.jforce.backend.service;

import com.jforce.backend.exception.BadCredentialsException;
import com.jforce.backend.exception.GecersizIstekException;
import com.jforce.backend.exception.ParolaDegisikligiGerekliException;
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
    private final ParolaService parolaService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    public AuthService(JWTService jwtService, PersonelRepository personelRepository, ParolaService parolaService, RefreshTokenService refreshTokenService, AuditService auditService) {
        this.jwtService = jwtService;
        this.personelRepository = personelRepository;
        this.parolaService = parolaService;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
    }


    //hem token hemde refreshtoken'ı dönebilmek için
    public record LoginResult(LoginResponse loginResponse, String refreshToken) {}


    public LoginResult login(LoginRequest loginRequest) {
            String kullaniciAdi;
            try {
                kullaniciAdi = parolaService.kullaniciAdiniNormalizeEt(loginRequest.kullaniciAdi());
            } catch (GecersizIstekException exception) {
                auditService.logKaydet(AuditEylem.LOGIN_BASARISIZ, null, loginRequest.kullaniciAdi(), "Hatalı kullanıcı adı/parola.", null);
                throw new BadCredentialsException("Kullanıcı adı veya parola hatalı.");
            }

            Personel personel = personelRepository.findByKullaniciAdi(kullaniciAdi).orElse(null);

            if (personel == null || !parolaService.eslesiyorMu(loginRequest.parola(), personel.getParolaHash())) {
                auditService.logKaydet(
                        AuditEylem.LOGIN_BASARISIZ,
                        null,
                        kullaniciAdi,
                        "Hatalı kullanıcı adı/parola.",
                        null
                );

                throw new BadCredentialsException("Kullanıcı adı veya parola hatalı.");
            }

            //kullanıcı aktif mi
            if (!Boolean.TRUE.equals(personel.getCalisiyorMu())) {
                auditService.logKaydet(AuditEylem.LOGIN_BASARISIZ, personel, personel.getKullaniciAdi(), "İnaktif kullanıcı giriş yapmaya çalıştı.", null);

                throw new BadCredentialsException("Kullanıcı adı veya parola hatalı.");
            }

            //ilk girişte parolasını değiştirdimi
            if (personel.getParolaDegistirilmeliMi()) {
                throw new ParolaDegisikligiGerekliException("İlk girişte parola değişikliği gerekli.");
            }

            // Mevcut refresh token, başarılı audit ve response kodu
            // bundan sonra aynı şekilde devam eder.
        String rawRefreshToken = refreshTokenService.createToken(personel);
        auditService.logKaydet(AuditEylem.LOGIN_BASARILI,personel, personel.getKullaniciAdi(), "Login başarılı.",null);
        return new LoginResult(
                new LoginResponse(jwtService.generateToken(personel.getPersonelId(), personel.getRol().getRolAdi())
                ,personel.getAdi()
                ,personel.getSoyadi()
                ,personel.getRol().getRolAdi()),rawRefreshToken);
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
        auditService.logKaydet(AuditEylem.LOGOUT,p,p != null ? p.getKullaniciAdi() : null,"Çıkış yapıldı",null);
    }
}
