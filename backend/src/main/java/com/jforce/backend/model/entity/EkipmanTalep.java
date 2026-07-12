package com.jforce.backend.model.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ekipman_talep")
public class EkipmanTalep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "talep_id")
    private String talepId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "personel_id",nullable = false)
    private Personel personel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ekipman_tipi_id",nullable = false)
    private EkipmanTipi ekipmanTipi;

    @Column(name = "aciklama")
    private String aciklama;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "talep_durum_id",nullable = false)
    private DurumTuru talepDurum;

    @Column(name = "talep_tarihi")      //kurulduğu an tarih koyma gibi bir anotasyon varmı bak
    private LocalDateTime talepTarihi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "islem_yapan_id")
    private Personel islemYapanId;

    @Column(name = "islem_tarihi")
    private LocalDateTime islemTarihi;

    public EkipmanTalep() {
    }

    public String getTalepId() {
        return talepId;
    }

    public void setTalepId(String talepId) {
        this.talepId = talepId;
    }

    public Personel getPersonel() {
        return personel;
    }

    public void setPersonel(Personel personelId) {
        this.personel = personelId;
    }

    public EkipmanTipi getEkipmanTipi() {
        return ekipmanTipi;
    }

    public void setEkipmanTipi(EkipmanTipi ekipmanTipiId) {
        this.ekipmanTipi = ekipmanTipiId;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public DurumTuru getTalepDurum() {
        return talepDurum;
    }

    public void setTalepDurum(DurumTuru talepDurumId) {
        this.talepDurum = talepDurumId;
    }

    public LocalDateTime getTalepTarih() {
        return talepTarihi;
    }

    public void setTalepTarih(LocalDateTime talepTarihi) {
        this.talepTarihi = talepTarihi;
    }

    public Personel getIslemYapanId() {
        return islemYapanId;
    }

    public void setIslemYapanId(Personel islemYapanId) {
        this.islemYapanId = islemYapanId;
    }

    public LocalDateTime getIslemTarihi() {
        return islemTarihi;
    }

    public void setIslemTarihi(LocalDateTime islemTarihi) {
        this.islemTarihi = islemTarihi;
    }
}
