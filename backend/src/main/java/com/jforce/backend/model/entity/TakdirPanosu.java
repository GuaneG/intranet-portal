package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "takdir_panosu")
public class TakdirPanosu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer takdirId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gonderen_personel_id",nullable = false)
    private Personel gonderenPersonelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alici_personel_id",nullable = false)
    private Personel aliciPersonelId;

    @Column(name = "mesaj", nullable = false,length = 300)
    private String mesaj;

    @Column(name = "olusturulma_tarihi", nullable = false)
    private LocalDateTime olusturulmaTarih;

    public TakdirPanosu() {
    }

    public Integer getTakdirId() {
        return takdirId;
    }

    public void setTakdirId(Integer takdirId) {
        this.takdirId = takdirId;
    }

    public Personel getGonderenPersonelId() {
        return gonderenPersonelId;
    }

    public void setGonderenPersonelId(Personel gonderenPersonelId) {
        this.gonderenPersonelId = gonderenPersonelId;
    }

    public Personel getAliciPersonelId() {
        return aliciPersonelId;
    }

    public void setAliciPersonelId(Personel aliciPersonelId) {
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
