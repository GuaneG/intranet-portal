package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "yorumlar")
public class Yorumlar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yorum_id")
    private Integer yorumId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duyuru_id",nullable = false)
    private DuyuruBilgileri duyuruId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yorum_yapan_id",nullable = false)
    private Personel yorumYapanId;

    @Column(name = "icerik",columnDefinition = "TEXT",nullable = false)
    private String icerik;

    @Column(name = "yorum_tarihi",nullable = false)
    private LocalDateTime yorumTarihi;

    public Yorumlar() {
    }

    public Integer getYorumId() {
        return yorumId;
    }

    public void setYorumId(Integer yorumId) {
        this.yorumId = yorumId;
    }

    public DuyuruBilgileri getDuyuruId() {
        return duyuruId;
    }

    public void setDuyuruId(DuyuruBilgileri duyuruId) {
        this.duyuruId = duyuruId;
    }

    public String getIcerik() {
        return icerik;
    }

    public void setIcerik(String icerik) {
        this.icerik = icerik;
    }

    public LocalDateTime getYorumTarihi() {
        return yorumTarihi;
    }

    public void setYorumTarihi(LocalDateTime yorumTarihi) {
        this.yorumTarihi = yorumTarihi;
    }

    public Personel getYorumYapanId() {
        return yorumYapanId;
    }

    public void setYorumYapanId(Personel yorumYapanId) {
        this.yorumYapanId = yorumYapanId;
    }
}
