package com.jforce.backend.model.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

@Entity
@Table(
        name = "oda",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_oda_ad",
                columnNames = "oda_ad"
        )
)
@Check(constraints = "oda_kapasite > 0")
public class Oda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oda_id")
    private Integer odaId;

    @Column(name = "oda_ad", nullable = false, length = 50)
    private String odaAd;

    @Column(name = "oda_kapasite",nullable = false)
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
