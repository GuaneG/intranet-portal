package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "duyuru_bilgileri")
public class DuyuruBilgileri {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "duyuru_id")
    private Integer duyuruId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "olusturan_kullanici_id",nullable = false)
    private Personel olusturanKullaniciId;

    @Column(name = "olusturma_tarihi",nullable = false)
    private LocalDateTime olusturmaTarihi;

    @Column(name = "baslik",length = 100,nullable = false)
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

    public Personel getOlusturanKullaniciId() {
        return olusturanKullaniciId;
    }

    public void setOlusturanKullaniciId(Personel olusturanKullaniciId) {
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
