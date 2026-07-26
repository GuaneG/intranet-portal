package com.jforce.backend;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.AuditLogRepository;
import com.jforce.backend.service.AuditService;
import com.jforce.backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AuditServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    public void auditLogBasarisizLoginTest(){
        auditService.logKaydet(AuditEylem.LOGIN_BASARISIZ,null,"placeholder","placeholder");
        List<AuditLog> auditLogs = auditLogRepository.findAll();

            AuditLog testObj = auditLogs.getFirst();
            assertFalse(auditLogs.isEmpty());
            assertEquals(AuditEylem.LOGIN_BASARISIZ,testObj.getEylemTipi());
            assertNull(testObj.getPersonel());
            assertEquals("placeholder",testObj.getKullaniciAdi());
            assertEquals("placeholder",testObj.getDetay());
            assertNotNull(testObj.getOlusmaZamani());
            assertNotNull(testObj.getLogId());
    }

    //bu metodu yazma nedenimiz, logkaydet metodu propagation'unu requires_new'a çektik
    //buda logkaydet metodunu çağıran ve logKaydet metodunun transaction'larını ayrı tutmayı sağlar
    //yani onu çağıran metod transaction atarsa bizim logKaydet metodumuz yine başarılı şekilde çalışır.
    //bizde bunu test etmek için bu propagation'un kapsadığı durumu oluşturuyoruz bu metod ile
    //bu aşağıdaki metodda eğer logkaydet propagation = REQUIRES_NEW olmasaydı oluşturduğumuz log ROLLBACK ile silinirdi.
//    @Transactional
//    public void throwExceptionForTransactionPaginationTest(){
//        auditService.logKaydet(AuditEylem.LOGIN_BASARISIZ,null,"placeholder","placeholder");
//        throw new RuntimeException();
//    }
//    @Test
//    public void logKaydetTransactionPropagationTest(){
//        assertThrows(RuntimeException.class, this::throwExceptionForTransactionPaginationTest);
//        assertFalse(auditLogRepository.findAll().isEmpty());
//    }
    //bu şekilde çalışmıyor diye bunu comment satırına dönüştürdüm self invocation hatası alıyordum,
    //@Transactional Spring PROXY üzerinden çalışıyormuş ve proxy mimarisi gereği böyle bi durumda proxy atlanıyormuş
}
