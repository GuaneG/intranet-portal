package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "izin_bilgileri")
public class IzinBilgileri {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "izin_id")
    private String izinId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personel_id",nullable = false)
    private Personel personelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "izin_turu_id",nullable = false)
    private IzinTuru izinTuruId;

    @Column(name = "baslangic_tarihi",nullable = false)
    private LocalDate baslangicTarihi;

    @Column(name = "bitis_tarihi",nullable = false)
    private LocalDate bitisTarihi;

    @Column(name = "is_gunu_sayisi",nullable = false)
    private Integer isGunuSayisi;

    @Column(name = "yoneticiye_not",columnDefinition = "TEXT")
    private String yoneticiyeNot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "izin_durum_id", nullable = false)
    private DurumTuru izinDurumId;

    @Column(name = "talep_tarihi",nullable = false)
    private LocalDateTime talepTarihi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "islem_yapan_id")
    private Personel islemYapanId;

    @Column(name = "islem_tarihi")
    private LocalDateTime islemTarihi;

    public IzinBilgileri() {
    }

    public String getIzinId() {
        return izinId;
    }

    public void setIzinId(String izinId) {
        this.izinId = izinId;
    }

    public Personel getPersonelId() {
        return personelId;
    }

    public void setPersonelId(Personel personelId) {
        this.personelId = personelId;
    }

    public IzinTuru getIzinTuruId() {
        return izinTuruId;
    }

    public void setIzinTuruId(IzinTuru izinTuruId) {
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

    public DurumTuru getIzinDurumId() {
        return izinDurumId;
    }

    public void setIzinDurumId(DurumTuru izinDurumId) {
        this.izinDurumId = izinDurumId;
    }

    public Personel getIslemYapanId() {
        return islemYapanId;
    }

    public void setIslemYapanId(Personel islemYapanId) {
        this.islemYapanId = islemYapanId;
    }

    public LocalDateTime getTalepTarihi() {
        return talepTarihi;
    }

    public void setTalepTarihi(LocalDateTime talepTarihi) {
        this.talepTarihi = talepTarihi;
    }
}
