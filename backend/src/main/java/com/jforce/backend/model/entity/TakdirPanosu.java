package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "takdir_panosu")
public class TakdirPanosu {

    private Integer takdirId;
    private String gonderenPersonelId;
    private String aliciPersonelId;
    private String mesaj;
    private LocalDateTime olusturulmaTarih;

    public TakdirPanosu() {
    }

    public Integer getTakdirId() {
        return takdirId;
    }

    public void setTakdirId(Integer takdirId) {
        this.takdirId = takdirId;
    }

    public String getGonderenPersonelId() {
        return gonderenPersonelId;
    }

    public void setGonderenPersonelId(String gonderenPersonelId) {
        this.gonderenPersonelId = gonderenPersonelId;
    }

    public String getAliciPersonelId() {
        return aliciPersonelId;
    }

    public void setAliciPersonelId(String aliciPersonelId) {
        this.aliciPersonelId = aliciPersonelId;
    }

    public String getMesaj() {
        return mesaj;
    }

    public void setMesaj(String mesaj) {
        this.mesaj = mesaj;
    }

    public LocalDateTime getOlusturulmaTarih() {
        return olusturulmaTarih;
    }

    public void setOlusturulmaTarih(LocalDateTime olusturulmaTarih) {
        this.olusturulmaTarih = olusturulmaTarih;
    }
}
