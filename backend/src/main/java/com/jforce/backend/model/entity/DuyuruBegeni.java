package com.jforce.backend.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

//2 tane manytomany ilişki saklayan table

@Entity
@Table(name = "duyuru_begeni")
public class DuyuruBegeni {

    private Integer duyuruId;
    private String personelId;

    public DuyuruBegeni() {
    }

    public Integer getDuyuruId() {
        return duyuruId;
    }

    public void setDuyuruId(Integer duyuruId) {
        this.duyuruId = duyuruId;
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setPersonelId(String personelId) {
        this.personelId = personelId;
    }
}
