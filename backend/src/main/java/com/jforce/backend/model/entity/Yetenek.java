package com.jforce.backend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "yetenek")
public class Yetenek {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "yetenek_id")
    private Integer yetenekId;

    @Column(name = "yetenek_adi",length = 50,nullable = false,unique = true)
    private String yetenekAdi;

    public Yetenek() {
    }

    public Integer getYetenekId() {
        return yetenekId;
    }

    public void setYetenekId(Integer yetenekId) {
        this.yetenekId = yetenekId;
    }

    public String getYetenekAdi() {
        return yetenekAdi;
    }

    public void setYetenekAdi(String yetenekAdi) {
        this.yetenekAdi = yetenekAdi;
    }
}
