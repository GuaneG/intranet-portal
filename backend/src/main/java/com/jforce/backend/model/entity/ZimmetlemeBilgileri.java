package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "zimmetleme_bilgileri")
public class ZimmetlemeBilgileri {
    private String zimmetId;
    private String ekipmanId;
    private String personelId;
    private LocalDateTime islemTarihi;
    private LocalDateTime iadeTarihi;

    public ZimmetlemeBilgileri() {
    }

    public String getZimmetId() {
        return zimmetId;
    }

    public void setZimmetId(String zimmetId) {
        this.zimmetId = zimmetId;
    }

    public String getEkipmanId() {
        return ekipmanId;
    }

    public void setEkipmanId(String ekipmanId) {
        this.ekipmanId = ekipmanId;
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setPersonelId(String personelId) {
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
