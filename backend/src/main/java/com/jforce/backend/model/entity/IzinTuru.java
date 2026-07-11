package com.jforce.backend.model.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "izin_turu")
public class IzinTuru {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "izin_turu_id")
    private Integer izinTuruId;

    @Column(name = "izin_turu_adi",length = 50,unique = true,nullable = false)
    private String izinTuruAdi;

    public IzinTuru() {
    }

    public Integer getIzinTuruId() {
        return izinTuruId;
    }

    public void setIzinTuruId(Integer izinTuruId) {
        this.izinTuruId = izinTuruId;
    }

    public String getIzinTuruAdi() {
        return izinTuruAdi;
    }

    public void setIzinTuruAdi(String izinTuruAdi) {
        this.izinTuruAdi = izinTuruAdi;
    }
}
