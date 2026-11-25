package com.jforce.backend.model.entity;

import com.jforce.backend.model.enums.TatilGunSuresi;
import com.jforce.backend.model.enums.TatilKategori;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDate;

@Entity
@Table(
        name = "tatil",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tatil_tarih",
                columnNames = "tarih"
        )
)
public class Tatil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tatil_id")
    private Integer tatilId;

    @Column(name = "ad", nullable = false, length = 100)
    private String ad;

    @Column(name = "tarih", nullable = false)
    private LocalDate tarih;

    @Column(name = "bitis_tarihi", nullable = false)
    private LocalDate bitisTarihi;

    @Enumerated(EnumType.STRING)
    @Column(name = "kategori", nullable = false, length = 30)
    private TatilKategori kategori;

    @Enumerated(EnumType.STRING)
    @Column(name = "gun_suresi", nullable = false, length = 20)
    private TatilGunSuresi gunSuresi;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Tatil() {
    }

    public Integer getTatilId() {
        return tatilId;
    }

    public void setTatilId(Integer tatilId) {
        this.tatilId = tatilId;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public LocalDate getTarih() {
        return tarih;
    }

    public void setTarih(LocalDate tarih) {
        this.tarih = tarih;
    }

    public LocalDate getBitisTarihi() {
        return bitisTarihi;
    }

    public void setBitisTarihi(LocalDate bitisTarihi) {
        this.bitisTarihi = bitisTarihi;
    }

    public TatilKategori getKategori() {
        return kategori;
    }

    public void setKategori(TatilKategori kategori) {
        this.kategori = kategori;
    }

    public TatilGunSuresi getGunSuresi() {
        return gunSuresi;
    }

    public void setGunSuresi(TatilGunSuresi gunSuresi) {
        this.gunSuresi = gunSuresi;
    }

    public Long getVersion() {
        return version;
    }
}
