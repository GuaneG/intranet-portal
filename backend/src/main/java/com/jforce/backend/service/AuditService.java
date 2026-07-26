package com.jforce.backend.service;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.enums.AuditEylem;
import com.jforce.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    //propagation = yayılım; bu metod çağrıldığında çağıranın transaction'ına ne olucak? ayarını yapmamızı sağlar.
    //varsayılan'ı required'dır buda çağıran bir transaction içindeyse ona katıl demektir
    //bu durumda bizim işimize gelmez çünkü login failed olduktan sonra exception atıyoruz (login üstünde transaction anotasyonu olmasada her işlem mini bir transactiondur,eğer @transactional varsa bütün işlemlerin yapılması beklenir teker teker bütün işlemler beklenilmez YA HEPSi YA HİÇ)
    //exception atmamızda bir rollback'tir auditlog'a yazdığımız kayıt gider başarısız kayıt hiç yazılmaz
    //REQUIRES_NEW da bu durumu atlamamızı sağlar çağıranın transaction'u önemsemez
    //kendi transaction'unu oluşturur. işini yapar hemen commit eder.                          //personel yoksa kullaniciAdi null olup NPE atmasın diye onuda parametre ile veriyoruz
    @Transactional(propagation = Propagation.REQUIRES_NEW)                                     //yoksa onu direkt auditLog.setKullaniciAdi(personel.getKullaniciAdi()) ile yapabilirdik
    public void logKaydet(AuditEylem auditEylem,Personel personel, String kullaniciAdi, String detayMsg) {
        AuditLog auditLog = new AuditLog();
        auditLog.setPersonel(personel);
        auditLog.setDetay(detayMsg);
        auditLog.setKullaniciAdi(kullaniciAdi);
        auditLog.setEylemTipi(auditEylem);

        auditLogRepository.save(auditLog);
    }
}
