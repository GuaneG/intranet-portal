package com.jforce.backend.model.entity;

import com.jforce.backend.model.enums.AuditEylem;
import jakarta.persistence.*;


import java.time.Instant;


@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "log_id",length = 36,nullable = false)
    private String logId;

    @Column(name = "eylem_tipi",nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditEylem eylemTipi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personel_id")
    private Personel personel;

    @Column(name = "kullanici_adi",length = 50)
    private String kullaniciAdi;

    @Column(name = "detay")
    private String detay;

    @Column(name = "olusma_zamani",nullable = false)
    private Instant olusmaZamani = Instant.now();

    public AuditLog() {
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }

    public AuditEylem getEylemTipi() {
        return eylemTipi;
    }

    public void setEylemTipi(AuditEylem eylemTipi) {
        this.eylemTipi = eylemTipi;
    }

    public Personel getPersonel() {
        return personel;
    }

    public void setPersonel(Personel personel) {
        this.personel = personel;
    }

    public String getKullaniciAdi() {
        return kullaniciAdi;
    }

    public void setKullaniciAdi(String kullaniciAdi) {
        this.kullaniciAdi = kullaniciAdi;
    }

    public String getDetay() {
        return detay;
    }

    public void setDetay(String detay) {
        this.detay = detay;
    }

    public Instant getOlusmaZamani() {
        return olusmaZamani;
    }

    public void setOlusmaZamani(Instant olusmaZamani) {
        this.olusmaZamani = olusmaZamani;
    }
}
