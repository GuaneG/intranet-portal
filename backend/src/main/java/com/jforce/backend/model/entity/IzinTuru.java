package com.jforce.backend.model.entity;

import org.hibernate.annotations.ColumnDefault;
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

    @Column(name = "yillik_haktan_duser_mi", nullable = false)
    @ColumnDefault("false")
    private boolean yillikHaktanDuserMi = false;

    public IzinTuru() {
    }

    public boolean isYillikHaktanDuserMi() {return yillikHaktanDuserMi;}

    public void setYillikHaktanDuserMi(boolean yillikHaktanDuserMi) {this.yillikHaktanDuserMi = yillikHaktanDuserMi;}

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
