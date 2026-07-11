package com.jforce.backend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ekipman_tipi")
public class EkipmanTipi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ekipman_tipi_id")
    private Integer ekipmanTipiId;

    @Column(name = "tip_adi",length = 50,nullable = false,unique = true)
    private String tipAdi;

    public EkipmanTipi() {
    }

    public Integer getEkipmanTipiId() {
        return ekipmanTipiId;
    }

    public void setEkipmanTipiId(Integer ekipmanTipiId) {
        this.ekipmanTipiId = ekipmanTipiId;
    }

    public String getTipAdi() {
        return tipAdi;
    }

    public void setTipAdi(String tipAdi) {
        this.tipAdi = tipAdi;
    }
}
