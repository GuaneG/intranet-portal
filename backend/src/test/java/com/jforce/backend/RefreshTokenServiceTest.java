package com.jforce.backend;

import com.jforce.backend.exception.InvalidRefreshTokenException;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.RefreshToken;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.RefreshTokenRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.AuthService;
import com.jforce.backend.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    private RefreshTokenService refreshTokenService;
    private Personel personel;
    @Mock
    private AuditService auditService;
    @BeforeEach
    public void setup() {
        refreshTokenService = new RefreshTokenService(1209600000L, refreshTokenRepository,auditService);

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
    void createTokenDbyeHashYazarHamTokenDoner() {
        //act: metodu çağır, dönen ham token artık elimizde
        String hamToken = refreshTokenService.createToken(personel);

        //save'e giden entity'yi yakala: metodun İÇİNDE new'lendiği için referansımız yok,
        //captor "save neyle çağrıldıysa onu bana ver" der
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken kaydedilen = captor.getValue();

        //assert: sistemin EN kritik sözleşmesi -> DB'ye ham token asla yazılmaz
        assertNotNull(hamToken);
        assertNotEquals(hamToken, kaydedilen.getTokenHash());
        assertEquals(personel, kaydedilen.getPersonel());
        assertFalse(kaydedilen.isRevoked()); //yeni token revoked doğmaz
    }

    @Test
    void rotateRevokedTokenGelirseAlarmVerirVeTumOturumlariSiler() {
        //arrange: DB'de revoked bir satır "varmış" gibi stubla
        RefreshToken calinti = new RefreshToken();
        calinti.setPersonel(personel);
        calinti.setRevoked(true);
        calinti.setExpiresAt(Instant.now().plusSeconds(1000)); //süresi geçmemiş olsun ki Dal 3'e değil Dal 2'ye düşsün

        //anyString(): "hangi string'le çağrılırsa çağrılsın" eşleştiricisi.
        //neden? aranan şey hash'lenmiş token; hash metodu private, testten hesaplayamayız -> eşleşmeyi gevşetiyoruz
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(calinti));

        //TUZAK: bunu stub'lamazsan test InvalidRefreshTokenException yerine NullPointerException'la kırılır!
        //sebep: deleteAllByPersonel'in dönüş tipi Long (kutulu); mock, ezber verilmemiş metodlarda null döner,
        //service'teki "long rowCount = ..." satırı null'ı primitive'e açarken NPE patlatır
        when(refreshTokenRepository.deleteAllByPersonel(personel)).thenReturn(2L);

        //act + assert: doğru exception fırlamalı
        assertThrows(InvalidRefreshTokenException.class,
                () -> refreshTokenService.rotate("calinti-ham-token"));

        //assert:silme GERÇEKTEN çağrılmış mı?
        //when geçmişe cevap ezberletir, verify geçmişi sorgular
        //burdaki verify metodunun işi -> refreshTokenRepository deleteAllbyPersonel(personel) metodunu çağırdı mı? diye bakar
        verify(refreshTokenRepository).deleteAllByPersonel(personel);

        verify(auditService).logKaydet(eq(AuditEylem.TOKEN_REUSE_ALARM), eq(personel), anyString(), anyString());
    }

    @Test
     void rotateGecerliTokenGelinceYeniOlustururVeRevokedIsaretle() {
        //arrange
        RefreshToken gecerliToken = new RefreshToken();
        gecerliToken.setPersonel(personel);
        gecerliToken.setRevoked(false);
        gecerliToken.setExpiresAt(Instant.now().plusSeconds(1000));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(gecerliToken));

        //act
        RefreshTokenService.RotationResult result =  refreshTokenService.rotate("eski-ham-token");

        //assert
        assertNotEquals("eski-ham-token", result.newRefreshToken());
        assertTrue(gecerliToken.isRevoked());
        assertEquals(personel,result.personel());
    }

    @Test
    void rotateBulunamayanTokendaExceptionFirlatir(){
        //arrange: DB'de böyle bir hash yok (Dal 1 - uydurma ya da logout'lanmış token)
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        //act + assert
        assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate("uydurma-token"));
    }

    @Test
    void rotateSuresiDolmusTokeniSilerVeExceptionFirlatir(){
        RefreshToken suresiDolmusToken = new RefreshToken();
        suresiDolmusToken.setPersonel(personel);
        suresiDolmusToken.setRevoked(false);
        suresiDolmusToken.setExpiresAt(Instant.now().minusSeconds(1000));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(suresiDolmusToken));

        assertThrows(InvalidRefreshTokenException.class, () -> refreshTokenService.rotate("bayat-token"));

        verify(refreshTokenRepository).delete(suresiDolmusToken);
    }

    @Test
    void revokeTokenBulamayincadaCalisir(){
        //arrange: logout'a gelen token'ın DB'de karşılığı yok (çifte logout senaryosu)
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.empty());

        //act + assert: logout idempotent -> asla exception fırlatmaz
        assertDoesNotThrow(() -> refreshTokenService.revokeToken("olmayan-token"));
    }

}
