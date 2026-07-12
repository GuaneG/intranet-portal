package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "zimmetleme_bilgileri")
public class ZimmetlemeBilgileri {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "zimmet_id")
    private String zimmetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ekipman_id",nullable = false)
    private EkipmanBilgileri ekipmanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personel_id",nullable = false)
    private Personel personelId;

    @Column(name = "islem_tarihi",nullable = false)
    private LocalDateTime islemTarihi;

    @Column(name = "iade_tarihi")
    private LocalDateTime iadeTarihi;

    public ZimmetlemeBilgileri() {
    }

    public String getZimmetId() {
        return zimmetId;
    }

    public void setZimmetId(String zimmetId) {
        this.zimmetId = zimmetId;
    }

    public EkipmanBilgileri getEkipmanId() {
        return ekipmanId;
    }

    public void setEkipmanId(EkipmanBilgileri ekipmanId) {
        this.ekipmanId = ekipmanId;
    }

    public Personel getPersonelId() {
        return personelId;
    }

    public void setPersonelId(Personel personelId) {
        this.personelId = personelId;
    }

    public LocalDateTime getIslemTarihi() {
        return islemTarihi;
    }

    public void setIslemTarihi(LocalDateTime islemTarihi) {
        this.islemTarihi = islemTarihi;
    }

    public LocalDateTime getIadeTarihi() {
        return iadeTarihi;
    }

    public void setIadeTarihi(LocalDateTime iadeTarihi) {
        this.iadeTarihi = iadeTarihi;
    }
}
