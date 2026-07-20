package com.jforce.backend;

import com.jforce.backend.service.JWTService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JWTServiceTest {
    JWTService jwtService = new JWTService("enAz32KarakterUzunlugundaTestSecret",10_000L);

    @Test
    void generateAndReadToken(){
        String token = jwtService.generateToken("test-id","ADMIN");
        Claims claims = jwtService.parseToken(token);
        System.out.println("1st Assertion: 'test-id' =?" +" "+claims.getSubject());
        assertEquals("test-id",claims.getSubject());
        System.out.println("2nd Assertion: 'ADMIN' =? " + claims.get("rol",String.class));
        assertEquals("ADMIN",claims.get("rol",String.class));
    }

    @Test
    void generateAndReadBadToken(){
        String token = jwtService.generateToken("test-user","ADMIN");
        assertThrows(JwtException.class,() -> jwtService.parseToken(token + "x"));
    }
}
