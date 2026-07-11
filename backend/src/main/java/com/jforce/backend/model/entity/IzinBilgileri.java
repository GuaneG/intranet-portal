package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "izin_bilgileri")
public class IzinBilgileri {
    private String izinId;
    private String personelId;
    private Integer izinTuruId;
    private LocalDate baslangicTarihi;
    private LocalDate bitisTarihi;
    private Integer isGunuSayisi;
    private String yoneticiyeNot;
    private Integer izinDurumId;
    private LocalDateTime talepTarihi;
    private String islemYapanId;
    private LocalDateTime islemTarihi;

    public IzinBilgileri() {
    }

    public String getIzinId() {
        return izinId;
    }

    public void setIzinId(String izinId) {
        this.izinId = izinId;
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setPersonelId(String personelId) {
        this.personelId = personelId;
    }

    public Integer getIzinTuruId() {
        return izinTuruId;
    }

    public void setIzinTuruId(Integer izinTuruId) {
        this.izinTuruId = izinTuruId;
    }

    public LocalDate getBaslangicTarihi() {
        return baslangicTarihi;
    }

    public void setBaslangicTarihi(LocalDate baslangicTarihi) {
        this.baslangicTarihi = baslangicTarihi;
    }

    public LocalDate getBitisTarihi() {
        return bitisTarihi;
    }

    public void setBitisTarihi(LocalDate bitisTarihi) {
        this.bitisTarihi = bitisTarihi;
    }

    public Integer getIsGunuSayisi() {
        return isGunuSayisi;
    }

    public void setIsGunuSayisi(Integer isGunuSayisi) {
        this.isGunuSayisi = isGunuSayisi;
    }

    public String getYoneticiyeNot() {
        return yoneticiyeNot;
    }

    public void setYoneticiyeNot(String yoneticiyeNot) {
        this.yoneticiyeNot = yoneticiyeNot;
    }

    public Integer getIzinDurumId() {
        return izinDurumId;
    }

    public void setIzinDurumId(Integer izinDurumId) {
        this.izinDurumId = izinDurumId;
    }

    public String getIslemYapanId() {
        return islemYapanId;
    }

    public void setIslemYapanId(String islemYapanId) {
        this.islemYapanId = islemYapanId;
    }

    public LocalDateTime getTalepTarihi() {
        return talepTarihi;
    }

    public void setTalepTarihi(LocalDateTime talepTarihi) {
        this.talepTarihi = talepTarihi;
    }
}
