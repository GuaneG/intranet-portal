package com.jforce.backend.controller;

import com.jforce.backend.exception.InvalidRefreshTokenException;
import com.jforce.backend.model.dto.request.LoginRequest;
import com.jforce.backend.model.dto.response.LoginResponse;

import com.jforce.backend.model.dto.response.RefreshResponse;
import com.jforce.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    //cookie'nin maxAge'ini hesaplamak için süre lazım; cookie HTTP detayı olduğu için süresi de burada (service'te değil)
    private final long refreshExpirationMs;

    public AuthController(AuthService authService, @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        this.authService = authService;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    //cookie fabrikamız: 3 endpoint de cookie kuruyor
    //value = cookie'nin İÇERİĞİ; login/refresh'te ham refresh token, logout'ta "" (boş = imha)
    private ResponseCookie refreshCookie(String value, long maxAgeSeconds) {
        //cookie bir isim=değer çiftidir; "refreshToken" bizim seçtiğimiz ETİKET (localstorage'daki setItem("token", ...) daki "token" gibi)
        //aynı etiket @CookieValue(name="refreshToken") tarafında okuma için kullanılır, ikisi eşleşmek ZORUNDA
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true) //js bu cookie'yi OKUYAMAZ -> xss ile çalınamaz
                .maxAge(maxAgeSeconds) //tarayıcı bu süre sonra cookie'yi kendisi imha eder; DİKKAT: saniye ister, ms değil
                .secure(false) //true olursa cookie SADECE https'te taşınır; localhost http olduğu için false
                .sameSite("Lax") //başka siteden tetiklenen isteklere cookie eklenmez -> csrf kalkanı
                .path("/api/auth") //cookie sadece bu path'le başlayan isteklerde taşınır
                .build();
    }


    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        //service'ten iki parçalı koli: body'lik LoginResponse + cookie'lik ham refresh token
        //ham token'ı body'ye KOYMUYORUZ; body'yi js okuyabilir, httpOnly cookie'nin tüm amacı çökerdi
        AuthService.LoginResult loginResult = authService.login(loginRequest);
        //ms -> saniye çevirisi (maxAge saniye istiyor)
        ResponseCookie cookie = refreshCookie(loginResult.refreshToken(),refreshExpirationMs / 1000);
        //ResponseEntity = komple HTTP cevabının Java hali: ok()=200, header()=Set-Cookie satırı, body()=JSON'a çevrilecek nesne
        return ResponseEntity.ok()
                .header("Set-Cookie",cookie.toString())
                .body(loginResult.loginResponse());
    }

    //frontend wrapper, normal bir istek 401 yiyince burayı çağırır (sessiz yenileme akışının backend kapısı)
    //@CookieValue: Spring, isteğin Cookie header'ından ADI "refreshToken" olanı bulur, DEĞERİNİ parametreye koyar
    //required=false: cookie yoksa Spring kendisi reddetmesin (500/400 dönerdi), null versin -> kararı BİZ veriyoruz
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@CookieValue(name = "refreshToken", required = false) String refreshToken) {

        //cookie'siz istek (senaryo: logout'lanmış / cookie süresi dolmuş / tarayıcı verisi silinmiş)
        //-> handler bunu 401'e çevirir, frontend 401 görünce login'e yönlendirir
        if (refreshToken == null) {
            throw new InvalidRefreshTokenException("Oturum bulunamadı, lütfen giriş yapın.");
        }

        //service rotate zincirini döndürür: bul -> revoked mu (alarm!) -> süresi dolmuş mu -> eskiyi emekli et, yenisini üret
        AuthService.NewRefreshAndAccessToken result = authService.refresh(refreshToken);
        //rotation'ın client ayağı: YENİ refresh token cookie olur, tarayıcı aynı isimli cookie'yi görünce eskisinin üstüne yazar
        ResponseCookie cookie = refreshCookie(result.rawRefreshToken(), refreshExpirationMs / 1000);
        //access token'ın body'de (JSON'da) olması sızıntı DEĞİL, bilinçli tasarım:
        //js onu okuyup her isteğin Authorization header'ına koymak zorunda; riski kısa ömürle (15dk) sınırladık
        //gizli kalması gereken uzun ömürlü anahtar refresh token'dı, o cookie'de
        return ResponseEntity.ok()
                .header("Set-Cookie", cookie.toString())
                .body(new RefreshResponse(result.accessToken()));
    }

    //logout'un iki ayağı: DB kaydını sil (service) + tarayıcıdaki cookie'yi sil (aşağıdaki maxAge=0 emri)
    //httpOnly cookie'yi js SİLEMEZ, silme emrini de sunucu verir
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken) {

        //null'da exception YOK (refresh'in tersi): logout idempotent olmalı,
        //cookie'siz gelen zaten çıkmış, ona hata göstermek anlamsız
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        //noContent()=204 "yaptım, anlatacak bir şey yok" (body yok, o yüzden tip ResponseEntity<Void>)
        //aynı isimli cookie + boş değer + maxAge(0) = tarayıcıya "bu cookie'yi HEMEN imha et" emri
        return ResponseEntity.noContent()
                .header("Set-Cookie", refreshCookie("", 0).toString())
                .build();
    }

}
