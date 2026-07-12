package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDate;


//(tarih,oda_id,baslangic_saati) indexini nasıl göstericeksin
@Entity
@Table(name = "rezervasyon", uniqueConstraints = @UniqueConstraint(
        columnNames = {"tarih", "oda_id", "baslangic_saat"}))
public class Rezervasyon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "rezervasyon_id",length = 36,nullable = false)
    private String rezervasyonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "olusturan_id",nullable = false)
    private Personel olusturanId;

    @Column(name = "baslik",nullable = false,length = 50)
    private String baslik;

    @Column(name = "tarih",nullable = false)
    private LocalDate tarih;

    @Column(name = "baslangic_saat",nullable = false)
    private Integer baslangicSaat;

    @Column(name = "bitis_saat",nullable = false)
    private Integer bitisSaat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oda_id",nullable = false)
    private Oda odaId;

    public Rezervasyon() {
    }

    public String getRezervasyonId() {
        return rezervasyonId;
    }

    public void setRezervasyonId(String rezervasyonId) {
        this.rezervasyonId = rezervasyonId;
    }

    public Personel getOlusturanId() {
        return olusturanId;
    }

    public void setOlusturanId(Personel olusturanId) {
        this.olusturanId = olusturanId;
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

    public Oda getOdaId() {
        return odaId;
    }

    public void setOdaId(Oda odaId) {
        this.odaId = odaId;
    }
}
