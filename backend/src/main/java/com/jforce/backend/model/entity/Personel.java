package com.jforce.backend.model.entity;


import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "personel")
public class Personel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "personel_id")
    private String personelId;

    @Column(name = "kullanici_adi",length = 50,nullable = false,unique = true)
    private String kullaniciAdi;

    @Column(name = "parola_hash",length = 100,nullable = false)
    private String parolaHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "yonetici_id")
    private Personel yonetici;

    @Column(name = "adi",length = 50,nullable = false)
    private String adi;

    @Column(name = "soyadi",length = 50,nullable = false)
    private String soyadi;

    @Column(name = "dogum_tarihi",nullable = false)
    private LocalDate dogumTarihi;

    @Column(name = "e_posta",length = 100,nullable = false,unique = true)
    private String ePosta;

    @Column(name = "sicil_numarasi",nullable = false,unique = true)
    private Integer sicilNumarasi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departman_id", nullable = false)
    private Departman departman;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id",nullable = false)
    private Rol rol;

    @Column(name = "calisiyor_mu",nullable = false)
    private Boolean calisiyorMu = true;

    @Column(name = "profil_foto",nullable = false,length = 100)
    private String profilFoto = "def_pfp.png";

    @Column(name = "yillik_izin_hakki",nullable = false)
    private Integer yillikIzinHakki = 14;


    public Personel() {
    }

    public String getPersonelId() {
        return personelId;
    }

    public void setId(String personelId) {
        this.personelId = personelId;
    }

    public String getKullaniciAdi() {
        return kullaniciAdi;
    }

    public void setKullaniciAdi(String kullaniciAdi) {
        this.kullaniciAdi = kullaniciAdi;
    }

    public String getParolaHash() {
        return parolaHash;
    }

    public void setParolaHash(String parolaHash) {
        this.parolaHash = parolaHash;
    }

    public Personel getYonetici() {
        return yonetici;
    }

    public void setYonetici(Personel yonetici) {
        this.yonetici = yonetici;
    }

    public String getAdi() {
        return adi;
    }

    public void setAdi(String adi) {
        this.adi = adi;
    }

    public String getSoyadi() {
        return soyadi;
    }

    public void setSoyadi(String soyadi) {
        this.soyadi = soyadi;
    }

    public LocalDate getDogumTarihi() {
        return dogumTarihi;
    }

    public void setDogumTarihi(LocalDate dogumTarihi) {
        this.dogumTarihi = dogumTarihi;
    }

    public String getePosta() {
        return ePosta;
    }

    public void setePosta(String ePosta) {
        this.ePosta = ePosta;
    }

    public Integer getSicilNumarasi() {
        return sicilNumarasi;
    }

    public void setSicilNumarasi(Integer sicilNumarasi) {
        this.sicilNumarasi = sicilNumarasi;
    }

    public Departman getDepartman() {
        return departman;
    }

    public void setDepartman(Departman departman) {
        this.departman = departman;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRolId(Rol rol) {
        this.rol = rol;
    }

    public Boolean getCalisiyorMu() {
        return calisiyorMu;
    }

    public void setCalisiyorMu(Boolean calisiyorMu) {
        this.calisiyorMu = calisiyorMu;
    }

    public String getProfilFoto() {
        return profilFoto;
    }

    public void setProfilFoto(String profilFoto) {
        this.profilFoto = profilFoto;
    }

    public Integer getYillikIzinHakki() {
        return yillikIzinHakki;
    }

    public void setYillikIzinHakki(Integer yillikIzinHakki) {
        this.yillikIzinHakki = yillikIzinHakki;
    }
}
