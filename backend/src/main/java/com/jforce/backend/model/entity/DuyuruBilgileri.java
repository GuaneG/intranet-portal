package com.jforce.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "duyuru_bilgileri")
public class DuyuruBilgileri {

    private Integer duyuruId;
    private String olusturanKullaniciId;
    private LocalDateTime olusturmaTarihi;
    private String baslik;
    @Column(name = "icerik", columnDefinition = "TEXT", nullable = false)
    private String icerik;

    public DuyuruBilgileri() {
    }

    public Integer getDuyuruId() {
        return duyuruId;
    }

    public void setDuyuruId(Integer duyuruId) {
        this.duyuruId = duyuruId;
    }

    public String getOlusturanKullaniciId() {
        return olusturanKullaniciId;
    }

    public void setOlusturanKullaniciId(String olusturanKullaniciId) {
        this.olusturanKullaniciId = olusturanKullaniciId;
    }

    public LocalDateTime getOlusturmaTarihi() {
        return olusturmaTarihi;
    }

    public void setOlusturmaTarihi(LocalDateTime olusturmaTarihi) {
        this.olusturmaTarihi = olusturmaTarihi;
    }

    public String getBaslik() {
        return baslik;
    }

    public void setBaslik(String baslik) {
        this.baslik = baslik;
    }

    public String getIcerik() {
        return icerik;
    }

    public void setIcerik(String icerik) {
        this.icerik = icerik;
    }
}
