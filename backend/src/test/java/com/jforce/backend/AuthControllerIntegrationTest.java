package com.jforce.backend;


import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.*;
import com.jforce.backend.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
public class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired //hata vermesini aldırma yukardaki anotasyonla bu sorunu çözüyoruz.
    private MockMvc mockMvc;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private DepartmanRepository departmanRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Personel personel;
    @BeforeEach
    public void cleanUpAndSetupDb() {
        //fk hatası almayalım diye ilk en dıştan başlayarak tablo içi silinir.
        refreshTokenRepository.deleteAll();
        auditLogRepository.deleteAll();
        personelRepository.deleteAll();
        departmanRepository.deleteAll();
        rolRepository.deleteAll();
        //rol ve departmanın db'de olması lazımki personel oluşturabilelim
        Rol rol = new Rol();
        rol.setRolId(1);
        rol.setRolAdi(RolAdi.ADMIN);

        rol = rolRepository.save(rol);

        Departman departman = new Departman();
        departman.setDepartmanAdi("departmanAdi");

        departman = departmanRepository.save(departman);

        //personel oluştur
        personel = new Personel();
        personel.setKullaniciAdi("test_kullanici");
        personel.setParolaHash("sahtehash");
        personel.setAdi("Test");
        personel.setSoyadi("Kullanici");
        personel.setDogumTarihi(LocalDate.of(1990, 1, 1));
        personel.setePosta("cangere@hotmail.com");
        personel.setSicilNumarasi(5);
        personel.setDepartman(departman);
        personel.setRol(rol);
        personel.setCalisiyorMu(true);
        personel.setIsGirisTarihi(LocalDate.of(1990, 1, 1));


        //db ye yaz
        personel = personelRepository.save(personel);
    }

    @Test
    void pasifPersonelDogruParolaylaLoginDeneyince401Doner(){
        personel.setParolaHash(passwordEncoder.encode("123456"));
        personel.setCalisiyorMu(false);
        personelRepository.save(personel);

        try {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                        "kullaniciAdi" : "test_kullanici",
                        "parola" : "123456"
                        }
                           \s""")).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Kullanıcı adı veya parola hatalı."))
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void pasifPersonelRefreshDeneyince401DonerVeYeniCookieUretilmez() throws Exception{
        // 1. Personel başlangıçta aktifken geçerli refresh token üret
        String rawRefreshToken = refreshTokenService.createToken(personel);

        // 2. Personeli pasif yap
        personel.setCalisiyorMu(false);
        personelRepository.save(personel);

        // 3. Ham tokenı refreshToken cookie'si olarak gönder
        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("refreshToken", rawRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message")
                        .value("Bu oturumunuz sona ermiştir lütfen tekrar giriş yapınız."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // 4. Pasif personele ait tokenların temizlendiğini doğrula
        assertEquals(0L, refreshTokenRepository.count());
    }
}
