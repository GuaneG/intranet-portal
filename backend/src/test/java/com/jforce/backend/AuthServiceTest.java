package com.jforce.backend;

import com.jforce.backend.exception.BadCredentialsException;
import com.jforce.backend.model.dto.request.LoginRequest;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.PersonelRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.AuthService;
import com.jforce.backend.service.JWTService;
import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class) //mockito anotsayonlarını getir
public class AuthServiceTest {
    @Mock //bir sınıfın içi boş taklididir, jwtservice var ama içi boş ne yapabilceğini sen öğretirsin
    JWTService jwtService;
    @Mock
    PersonelRepository personelRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    RefreshTokenService refreshTokenService;
    @Mock
    AuditService auditService;

    @InjectMocks //gerçek authservice oluşturur ama constructor'ına bu 4 mock değeri verir
    AuthService authService;

    private Personel personel;

    @BeforeEach
    void setUp() {
        Rol rol = new Rol();
        rol.setRolAdi("ADMIN");

        personel = new Personel();
        personel.setPersonelId("uuid-123");
        personel.setKullaniciAdi("test.kullanici");
        personel.setParolaHash("sahtehash");
        personel.setAdi("Test");
        personel.setSoyadi("Kullanici");
        personel.setRol(rol);
    }

    @Test
    //burda stubbing yapıyoruz stubbing ise:
    //mock'a belirli bir çağrı için önceden cevap ezberletmek. Kalıbı:
    //when(personelRepository.findByKullaniciAdi("yok")).thenReturn(Optional.empty());
    //yani stubbing test edilcek durumu yaratma aracıdır.
    void throwBadCredentialsExceptionWhenNoUname(){
        //arrange: stub + girdi hazırlığı
        when(personelRepository.findByKullaniciAdi("yok"))
                .thenReturn(Optional.empty());
        LoginRequest loginRequest = new LoginRequest("yok", "Test1234.");
        //act + assert: login çağrısı act, assertThrows içi excp beklentisi assert
        assertThrows(BadCredentialsException.class , () -> authService.login(loginRequest));
        verify(auditService).logKaydet(eq(AuditEylem.LOGIN_BASARISIZ), isNull(), anyString(), anyString());
    }

    @Test
    void throwBadCredentialsWhenBadPassword() {
        when(personelRepository.findByKullaniciAdi("test.kullanici"))
                .thenReturn(Optional.of(personel));
        when(passwordEncoder.matches("yanlisParola", "sahtehash"))
                .thenReturn(false);

        LoginRequest request = new LoginRequest("test.kullanici", "yanlisParola");

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(auditService).logKaydet(eq(AuditEylem.LOGIN_BASARISIZ), isNull(), anyString(), anyString());
    }

    @Test
    void successFullLogin() {
        when(personelRepository.findByKullaniciAdi("test.kullanici"))
                .thenReturn(Optional.of(personel));
        when(passwordEncoder.matches("Test1234.", "sahtehash"))
                .thenReturn(true);
        when(jwtService.generateToken("uuid-123", "ADMIN"))
                .thenReturn("sahte-token");
        when(refreshTokenService.createToken(personel))
                .thenReturn("sahte-refresh-token");

        LoginRequest request = new LoginRequest("test.kullanici", "Test1234.");

        AuthService.LoginResult loginResult = authService.login(request);

        assertEquals("sahte-token", loginResult.loginResponse().token());
        assertEquals("Test", loginResult.loginResponse().ad());
        assertEquals("Kullanici", loginResult.loginResponse().soyad());
        assertEquals("ADMIN", loginResult.loginResponse().rol());
        assertEquals("sahte-refresh-token",loginResult.refreshToken());
    }

}
