package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {
    @Mock
    private AuditLogRepository auditLogRepository;
    private Personel personel;

    @InjectMocks
    private AuditService auditService;

    @BeforeEach
    //arrange
    public void setUp() {
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
        public void basariliLoginLoglama() {
            //act
            auditService.logKaydet(AuditEylem.LOGIN_BASARILI,personel, personel.getKullaniciAdi(),"");
            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);

            //assert
            verify(auditLogRepository).save(captor.capture());
            AuditLog capturedValue = captor.getValue();

            //assert
            assertEquals(AuditEylem.LOGIN_BASARILI, capturedValue.getEylemTipi());
            assertEquals(personel, capturedValue.getPersonel());
            assertEquals(personel.getKullaniciAdi(), capturedValue.getKullaniciAdi());
            assertNotNull(capturedValue.getOlusmaZamani());
        }

}
