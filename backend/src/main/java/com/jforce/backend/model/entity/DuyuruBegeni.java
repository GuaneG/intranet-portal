package com.jforce.backend.model.entity;

import jakarta.persistence.*;

//2 tane manytomany ilişki saklayan table
// composite key nasıl gösterilir? idler hem foreign key hemde pk
@Entity
@Table(name = "duyuru_begeni")
public class DuyuruBegeni {

    @EmbeddedId
    private DuyuruBegeniId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("duyuruId")
    @JoinColumn(name = "duyuru_id", nullable = false)
    private DuyuruBilgileri duyuru;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("personelId")
    @JoinColumn(name = "personel_id",nullable = false,columnDefinition = "varchar(36)")
    private Personel personel;

    public DuyuruBegeni() {
    }

    public DuyuruBegeniId getId() {
        return id;
    }

    public void setId(DuyuruBegeniId id) {
        this.id = id;
    }

    public DuyuruBilgileri getDuyuru() {
        return duyuru;
    }

    public void setDuyuru(DuyuruBilgileri duyuruId) {
        this.duyuru = duyuruId;
    }

    public Personel getPersonel() {
        return personel;
    }

    public void setPersonel(Personel personelId) {
        this.personel = personelId;
    }
}
