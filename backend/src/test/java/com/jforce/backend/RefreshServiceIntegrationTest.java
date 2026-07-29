package com.jforce.backend;


import com.jforce.backend.exception.InvalidRefreshTokenException;
import com.jforce.backend.repository.DepartmanRepository;
import com.jforce.backend.repository.RolRepository;
import com.jforce.backend.model.entity.Departman;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.RefreshToken;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.repository.RefreshTokenRepository;

import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;


import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RefreshServiceIntegrationTest extends AbstractIntegrationTest{
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private  RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private PersonelRepository personelRepository;
    @Autowired
    private RolRepository rolRepository;
    @Autowired
    private DepartmanRepository departmanRepository;

    private Personel personel;
    @BeforeEach
    public void cleanUpAndSetupDb() {
        //fk hatası almayalım diye ilk en dıştaki silinir.
        refreshTokenRepository.deleteAll();
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
        personel.setKullaniciAdi("test.kullanici");
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
    void pasifPersonelRefreshDenemesindeTumTokenlariDbdenSilinir() {
        // 1. Gerçek DB'ye aktif personel, rol ve departman kaydet


        // 2. createToken ile bu personel için iki gerçek refresh token oluştur
        // Birinci metodun döndürdüğü ham tokenı sakla
        String rt1 = refreshTokenService.createToken(personel);
        String rt2 = refreshTokenService.createToken(personel);

        // 3. DB'de iki refresh token olduğunu doğrula
        assertEquals(2L, refreshTokenRepository.count());
        // 4. Personeli pasif yap ve DB'ye kaydet
        personel.setCalisiyorMu(false);
        personelRepository.save(personel);
        // 5. Saklanan ham tokenla rotate çağır
        // InvalidRefreshTokenException bekle
        assertThrows(InvalidRefreshTokenException.class,() -> refreshTokenService.rotate(rt1));
        // 6. rotate bittikten sonra DB'de sıfır refresh token olduğunu doğrula
        assertTrue(refreshTokenRepository.findAll().isEmpty());
        assertEquals(0L, refreshTokenRepository.count());
    }

    @Test
    void  deleteExpiredTokensSuresiDolmusTokeniSilerGecerliTokeniKorur(){
        RefreshToken expiredToken = new RefreshToken();
        expiredToken.setTokenHash("mock-hash");
        expiredToken.setRevoked(false);
        expiredToken.setPersonel(personel);
        expiredToken.setCreatedAt(Instant.now());
        expiredToken.setExpiresAt(Instant.now().minusSeconds(1000L));

        refreshTokenRepository.save(expiredToken);

        RefreshToken validToken = new RefreshToken();
        validToken.setTokenHash("mock-hash1");
        validToken.setRevoked(false);
        validToken.setPersonel(personel);
        validToken.setCreatedAt(Instant.now());
        validToken.setExpiresAt(Instant.now().plusSeconds(1000L));

        refreshTokenRepository.save(validToken);

        assertEquals(2L, refreshTokenRepository.count());

        refreshTokenService.deleteExpiredTokens();

        assertEquals("mock-hash1", refreshTokenRepository.findAll().getFirst().getTokenHash());
        assertEquals(1L, refreshTokenRepository.count());
    }

}
