package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "oda")
public class Oda {

    private Integer odaId;
    private String odaAd;
    private Integer odaKapasite;

    public Oda() {
    }

    public Integer getOdaId() {
        return odaId;
    }

    public void setOdaId(Integer odaId) {
        this.odaId = odaId;
    }

    public String getOdaAd() {
        return odaAd;
    }

    public void setOdaAd(String odaAd) {
        this.odaAd = odaAd;
    }

    public Integer getOdaKapasite() {
        return odaKapasite;
    }

    public void setOdaKapasite(Integer odaKapasite) {
        this.odaKapasite = odaKapasite;
    }
}
