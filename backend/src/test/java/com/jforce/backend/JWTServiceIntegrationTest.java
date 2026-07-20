package com.jforce.backend;

import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class JWTServiceIntegrationTest {

    @Autowired
    JWTService jwtService;

    @Test
    void createToken(){
        String token = jwtService.generateToken("test-user-id","ADMIN");
        System.out.println(token);
        assertEquals("test-user-id",jwtService.parseToken(token).getSubject());
    }


}
