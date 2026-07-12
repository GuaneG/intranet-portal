package com.jforce.backend.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "personel_yetenek")
public class PersonelYetenek {

    @EmbeddedId
    private PersonelYetenekId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("personelId")
    @JoinColumn(name = "personel_id",columnDefinition = "varchar(36)")
    private Personel personel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yetenek_id")
    @MapsId("yetenekId")
    private Yetenek yetenek;

    public PersonelYetenek() {
    }

    public PersonelYetenekId getId() {
        return id;
    }

    public void setId(PersonelYetenekId id) {
        this.id = id;
    }

    public Personel getPersonel() {
        return personel;
    }

    public void setPersonel(Personel personelId) {
        this.personel = personelId;
    }

    public Yetenek getYetenek() {
        return yetenek;
    }

    public void setYetenek(Yetenek yetenekId) {
        this.yetenek = yetenekId;
    }
}
