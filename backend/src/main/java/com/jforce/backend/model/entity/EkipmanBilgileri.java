package com.jforce.backend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "ekipman_bilgileri")
public class EkipmanBilgileri {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ekipman_id")
    private String ekipmanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ekipman_tipi_id",nullable = false)
    private EkipmanTipi ekipmanTipiId;

    @Column(name = "marka",nullable = false,length = 50)
    private String marka;

    @Column(name = "model",length = 50)
    private String model;

    @Column(name = "seri_no",length = 100,unique = true,nullable = false)
    private String seriNo;

    @Column(name = "ekipman_durum",length = 50,nullable = false)
    private String ekipmanDurum;

    public EkipmanBilgileri() {
    }

    public String getEkipmanId() {
        return ekipmanId;
    }

    public void setEkipmanId(String ekipmanId) {
        this.ekipmanId = ekipmanId;
    }

    public EkipmanTipi getEkipmanTipiId() {
        return ekipmanTipiId;
    }

    public void setEkipmanTipiId(EkipmanTipi ekipmanTipiId) {
        this.ekipmanTipiId = ekipmanTipiId;
    }

    public String getMarka() {
        return marka;
    }

    public void setMarka(String marka) {
        this.marka = marka;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSeriNo() {
        return seriNo;
    }

    public void setSeriNo(String seriNo) {
        this.seriNo = seriNo;
    }

    public String getEkipmanDurum() {
        return ekipmanDurum;
    }

    public void setEkipmanDurum(String ekipmanDurum) {
        this.ekipmanDurum = ekipmanDurum;
    }
}
