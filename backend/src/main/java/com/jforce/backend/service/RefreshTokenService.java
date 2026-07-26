package com.jforce.backend.service;


import com.jforce.backend.exception.InvalidRefreshTokenException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.RefreshToken;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.RefreshTokenRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

        private final RefreshTokenRepository refreshTokenRepository;
        private final long expirationMs;
        private final SecureRandom random = new SecureRandom();
        private final AuditService auditService;

    public RefreshTokenService(@Value("${jwt.refresh-expiration}")long expirationMs, RefreshTokenRepository refreshTokenRepository, AuditService auditService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
        this.auditService = auditService;
    }

     private String hashRawToken(String rawToken){
        try {
            //algoritma adına göre algoritmayı çalıştıran motor üretir
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            //rawToken'ı byte'a çeviriyoruz md ile digest etmeden önce çünkü hash fonksiyonları byte ister
            //md.digest() metoduda asıl SHA-256 'yı çalıştırır, ne olursa olsun çıktı 32byte'dır
            byte[] hashedBytes = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            //her byte'ı bir hex formatında yazdık(1 byte -> 2 hex ) DB'deki token_hash column'un lenght=64 constrainti tutmuş oldu
            return HexFormat.of().formatHex(hashedBytes);
        }catch (NoSuchAlgorithmException e){
            throw new IllegalStateException("Ham token hash'lenemedi.",e);
        }
    }

    //hem DB'ye hashli token'ı yazar birde ham rt döner.
    public String createToken(Personel personel) {

        //boş bir 32 byte'lık array
        byte[] randomBytes = new byte[32];

        //rastgele byte'larla içini doldur; math.random kullanmadık çünkü tahmin edilebilir bir algoritma onun yerine SecureRandom -> nextBytes()
        random.nextBytes(randomBytes);

        //rastgele byte'ları taşınabilir metne çeviriyoruz,ağ üzerinde birşey taşıncaksa genelde Base64 kullanılır.
        //getUrlEncoder metodu URL/cookie'lerde sorun çıkaran + veya / karakterleri yerine - ve _ kullanır
        //withoutPadding ise sondaki "=" karakterini atar, buda cookie syntax'ında isim değer ayracıdır (isim=değer)
        String generatedRawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        //DB'ye refreshtoken'ın hashli halini save ediyoruz
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setExpiresAt(Instant.now().plusMillis(expirationMs));
        refreshToken.setPersonel(personel);
        refreshToken.setTokenHash(hashRawToken(generatedRawToken));

        refreshTokenRepository.save(refreshToken);

        //Client'ın kullanabilceği rawToken'ı dönüyoruz.
        return generatedRawToken;
    }

    //hem personel hemde yeni rt dönebilmek için oluşturduk
    public record RotationResult(Personel personel, String newRefreshToken) {}

    //Refresh token validasyon ve yenileme client 401 yediğinde bu metod çalışır
    //client 401 yiyince frontend wrapper /api/auth/refresh'i çağırır, o istek de bu metodu çalıştırır"
    //transactional -> metoda giriş BEGIN, normal çıkış = COMMIT,exception = ROLLBACK; bu yüzden silme işleminden sonra rollback olmasın diye anotasyona noRollBack ekledik.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotationResult rotate (String rawToken) {
        //gelen token'ı hashliyoruz çünkü DB'de token'ları hash'li tutuyoruz.
        String hashedToken = hashRawToken(rawToken);

        //hashlenmiş token'a göre DB'de arama yapıp bulduğumuz değeri bir RefreshToken Entity'sinde saklıyoruz, eğer yoksa Exception atıyoruz.
        RefreshToken rt = refreshTokenRepository.findByTokenHash(hashedToken).orElseThrow(()->new InvalidRefreshTokenException("Refresh token yok yada bulunamadı."));

        //SIZMA KONTROLU(eğer kullanıcı Revoked flag'ı true olan bir Refresh Token ile access token almaya çalışırsa refresh token kaydını sil)
        if(rt.isRevoked()){
            long rowCount = refreshTokenRepository.deleteAllByPersonel(rt.getPersonel());
            auditService.logKaydet(AuditEylem.TOKEN_REUSE_ALARM,rt.getPersonel(),rt.getPersonel().getKullaniciAdi(),"Refresh token reuse tespit edildi,"+ rowCount + " oturum kapatıldı");
            throw new InvalidRefreshTokenException("Oturum güvenliği nedeniyle sonlandırıldı, lütfen tekrar giriş yapın.");
        }

        //Eğer Refresh Token expire olmuşssa expired token'ı sil.
        if(rt.getExpiresAt().isBefore(Instant.now())){
            refreshTokenRepository.delete(rt);
            throw new InvalidRefreshTokenException("Bu oturumunuz sona ermiştir lütfen tekrar giriş yapınız.");
        }

        //token'ın rotasyonu bitti token artık işi bitmiş olarak işaretlenildi.
        rt.setRevoked(true);

        //yeni token oluşturulup personel için yollanıldı.
        String yeniHamToken = createToken(rt.getPersonel());
        return new RotationResult(rt.getPersonel(),yeniHamToken);
    }

    //Refresh token silme metodu (logout yapılınca çalışcak)
    @Transactional
    public Personel revokeToken(String rawToken){
        String hashedToken = hashRawToken(rawToken);
        return refreshTokenRepository.findByTokenHash(hashedToken)
                .map(rt -> {                 // token bulunduysa: rt elimizde
                    refreshTokenRepository.delete(rt);    // sil
                    return rt.getPersonel();              // ve sahibini döndür
                })
                .orElse(null);                      // token bulunmadıysa: (null idomponentlık için)
    }

    @Scheduled(cron = "0 0 3 * * *") // her gece saat 3 te bu metodu çalıştır
    @Transactional //toplu silme işlemleri transaction ister
    public void deleteExpiredTokens(){
        long deletedRowCount = refreshTokenRepository.deleteAllByExpiresAtBefore(Instant.now());
    }

}
