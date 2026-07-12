package com.jforce.backend.model.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;


import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class DuyuruBegeniId implements Serializable {

    Integer duyuruId;
    @Column(length = 36)
    String personelId;

    public DuyuruBegeniId() {
    }

    public DuyuruBegeniId(Integer duyuruId, String personelId) {
        this.duyuruId = duyuruId;
        this.personelId = personelId;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        DuyuruBegeniId that = (DuyuruBegeniId) o;
        return Objects.equals(duyuruId, that.duyuruId) && Objects.equals(personelId, that.personelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(duyuruId, personelId);
    }
}
