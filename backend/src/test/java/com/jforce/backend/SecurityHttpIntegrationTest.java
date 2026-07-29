package com.jforce.backend;

import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.service.JWTService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import(SecurityHttpIntegrationTest.TestController.class)
@AutoConfigureMockMvc
public class SecurityHttpIntegrationTest extends AbstractIntegrationTest{
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JWTService jwtService;

    @Test
    void tokenOlmadanKorumaliEndpointeIstekAtildigindaOrtakErrorResponseIle401Doner()
            throws Exception {

        mockMvc.perform(get("/api/dashboard/ozet"))
                .andExpect(status().isUnauthorized())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Kimlik doğrulaması gerekli."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/api/dashboard/ozet"));
    }
    @RestController
    static class TestController {
        @GetMapping("/test/security/admin")
        @PreAuthorize("hasRole('ADMIN')")
        public String sadeceAdmin() {
            return "ok";
        }
    }

    @Test
    void personelRoluAdminEndpointineIstekAtincaOrtakErrorResponseIle403Doner()
            throws Exception {

        String accessToken = jwtService.generateToken(
                "test-personel-id",
                RolAdi.PERSONEL
        );

        mockMvc.perform(get("/test/security/admin")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + accessToken
                        ))
                .andExpect(status().isForbidden())
                .andExpect(content()
                        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Bu işlem için yetkiniz bulunmuyor."))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path")
                        .value("/test/security/admin"));
    }
}
