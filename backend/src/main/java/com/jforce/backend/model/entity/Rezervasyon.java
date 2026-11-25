package com.jforce.backend.model.entity;

import com.jforce.backend.model.enums.RezervasyonDurum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "rezervasyon")
public class Rezervasyon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rezervasyon_id",length = 36,nullable = false)
    private String rezervasyonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "olusturan_id", nullable = false)
    private Personel olusturan;

    @Column(name = "baslik", nullable = false, length = 50)
    private String baslik;

    @Column(name = "tarih", nullable = false)
    private LocalDate tarih;

    @Column(name = "baslangic_saat", nullable = false)
    private Integer baslangicSaat;

    @Column(name = "bitis_saat", nullable = false)
    private Integer bitisSaat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oda_id", nullable = false)
    private Oda oda;

    @Column(name = "katilimci_sayisi", nullable = false)
    private Integer katilimciSayisi;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 30)
    private RezervasyonDurum durum;

    @Column(name = "olusturma_zamani", nullable = false)
    private LocalDateTime olusturmaZamani;

    @Column(name = "iptal_zamani")
    private LocalDateTime iptalZamani;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "iptal_eden_id")
    private Personel iptalEden;

    @Column(name = "iptal_nedeni", length = 500)
    private String iptalNedeni;

    public Rezervasyon() {
    }

    public String getRezervasyonId() {
        return rezervasyonId;
    }

    public void setRezervasyonId(String rezervasyonId) {
        this.rezervasyonId = rezervasyonId;
    }

    public Personel getOlusturan() {
        return olusturan;
    }

    public void setOlusturan(Personel olusturan) {
        this.olusturan = olusturan;
    }

    public LocalDate getTarih() {
        return tarih;
    }

    public void setTarih(LocalDate tarih) {
        this.tarih = tarih;
    }

    public String getBaslik() {
        return baslik;
    }

    public void setBaslik(String baslik) {
        this.baslik = baslik;
    }

    public Integer getBaslangicSaat() {
        return baslangicSaat;
    }

    public void setBaslangicSaat(Integer baslangicSaat) {
        this.baslangicSaat = baslangicSaat;
    }

    public Integer getBitisSaat() {
        return bitisSaat;
    }

    public void setBitisSaat(Integer bitisSaat) {
        this.bitisSaat = bitisSaat;
    }

    public Oda getOda() {
        return oda;
    }

    public void setOda(Oda oda) {
        this.oda = oda;
    }

    public Integer getKatilimciSayisi() {
        return katilimciSayisi;
    }

    public void setKatilimciSayisi(Integer katilimciSayisi) {
        this.katilimciSayisi = katilimciSayisi;
    }

    public RezervasyonDurum getDurum() {
        return durum;
    }

    public void setDurum(RezervasyonDurum durum) {
        this.durum = durum;
    }

    public LocalDateTime getOlusturmaZamani() {
        return olusturmaZamani;
    }

    public void setOlusturmaZamani(LocalDateTime olusturmaZamani) {
        this.olusturmaZamani = olusturmaZamani;
    }

    public LocalDateTime getIptalZamani() {
        return iptalZamani;
    }

    public void setIptalZamani(LocalDateTime iptalZamani) {
        this.iptalZamani = iptalZamani;
    }

    public Personel getIptalEden() {
        return iptalEden;
    }

    public void setIptalEden(Personel iptalEden) {
        this.iptalEden = iptalEden;
    }

    public String getIptalNedeni() {
        return iptalNedeni;
    }

    public void setIptalNedeni(String iptalNedeni) {
        this.iptalNedeni = iptalNedeni;
    }
}
