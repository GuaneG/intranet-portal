package com.jforce.backend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "durum_turu")
public class DurumTuru {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "durum_id")
    private Integer durumId;

    @Column(name = "durum_adi",length = 50,unique = true,nullable = false)
    private String durumAdi;

    public DurumTuru() {
    }

    public Integer getDurumId() {
        return durumId;
    }

    public void setDurumId(Integer durumId) {
        this.durumId = durumId;
    }

    public String getDurumAdi() {
        return durumAdi;
    }

    public void setDurumAdi(String durumAdi) {
        this.durumAdi = durumAdi;
    }
}
