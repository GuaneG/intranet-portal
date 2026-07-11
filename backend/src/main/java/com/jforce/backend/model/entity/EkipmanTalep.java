package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ekipman_talep")
public class EkipmanTalep {
    private String talepId;
    private String personelId;
    private Integer ekipmanTipiId;
    private String aciklama;
    private Integer talepDurumId;
    private LocalDateTime talepTarih;
    private String islemYapanId;
    private LocalDateTime islemTarihi;

    public EkipmanTalep() {
    }

    public String getTalepId() {
        return talepId;
    }

    public void setTalepId(String talepId) {
        this.talepId = talepId;
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setPersonelId(String personelId) {
        this.personelId = personelId;
    }

    public Integer getEkipmanTipiId() {
        return ekipmanTipiId;
    }

    public void setEkipmanTipiId(Integer ekipmanTipiId) {
        this.ekipmanTipiId = ekipmanTipiId;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public Integer getTalepDurumId() {
        return talepDurumId;
    }

    public void setTalepDurumId(Integer talepDurumId) {
        this.talepDurumId = talepDurumId;
    }

    public LocalDateTime getTalepTarih() {
        return talepTarih;
    }

    public void setTalepTarih(LocalDateTime talepTarih) {
        this.talepTarih = talepTarih;
    }

    public String getIslemYapanId() {
        return islemYapanId;
    }

    public void setIslemYapanId(String islemYapanId) {
        this.islemYapanId = islemYapanId;
    }

    public LocalDateTime getIslemTarihi() {
        return islemTarihi;
    }

    public void setIslemTarihi(LocalDateTime islemTarihi) {
        this.islemTarihi = islemTarihi;
    }
}
