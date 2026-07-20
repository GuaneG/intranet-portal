package com.jforce.backend.service;

import com.jforce.backend.model.entity.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTService {
    private final SecretKey secretKey;
    private final long expirationInMs;

    //value anotasyonu application.propertiesden değer almamızı sağlar
    public JWTService(@Value("${jwt.secret}") String secretKey, @Value("${jwt.expiration}") long expirationInMs) {
        //secretKey stringini byte'a çeviriyoruz çünkü hmacShaKeyFor() metodu byte ile çalışır,
        //hmacShaKeyFor metoduda bu bytelardan jjwtnin imza hesabında kullanacağı secret key nesnesini üretir
        this.secretKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.expirationInMs = expirationInMs;
    }

    //bu metodu login anında AuthService çağıracak
    public String generateToken(String userId,String rol){
        //token'ı parça parça tarif edip birleştirme
        return Jwts.builder()
                //payload'a userid yazdık
                .subject(userId)
                //kendi özel claimim token'dan rol okunabilsin diye
                .claim("rol",rol)
                //üretilme anı
                .issuedAt(new Date())
                //token'ın geçersiz olcağı vakit
                .expiration(new Date(System.currentTimeMillis() + expirationInMs))
                //oluşturduğumuz secret key ile imzaladık
                .signWith(secretKey)
                //jwt tamam herşeyi birletşirdik
                .compact();
    }


    //Çağıran filter'ın tek işi geçerli mi diye kontrol etmesi değil, geçerli ve kim bu diye bakar,
    //payload'daki verilerin taşıyıcı nesnesi claims'dir bu yüzden metod claims metodudur.


    //bu metodu her istekde JWTFilter çağırcak
    //jwt gelir "." lardan parçalara ayrılır, alanlarına erişilir, header+payload dan imza'yı yeniden hesapla
    //oluşan jwt imza ile uyuşuyormu kontrol et
    //payload'u oku
    public Claims parseToken(String token){

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                //buraya kadar parser oluşturma işlemleri,bir parser olsun ve secretkey ile verify etsin diyoruz
                .parseSignedClaims(token)
                //"." lardan parçalara ayırıp,alanlara erişip,header + payload'dan imza yı yeniden hesaplama,imza karşılaştırma tarzı herşey burda yapılır
                .getPayload();
                //sub + claim okuyabilmek için yani; kullanıcın rol ve id'sini okuyabilmek için
    }

}
