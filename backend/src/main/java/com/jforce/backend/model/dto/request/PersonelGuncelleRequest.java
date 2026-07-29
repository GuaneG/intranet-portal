package com.jforce.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

// PATCH isteğinde JSON'da bulunmayan alan ile açıkça null gönderilen alanı
// ayırt edebilmek için record yerine alan varlığını takip eden sınıf kullanılır.
public class PersonelGuncelleRequest {

    @Size(min = 1, max = 50)
    private String ad;
    private boolean adGonderildiMi;

    @Size(min = 3, max = 50)
    private String soyad;
    private boolean soyadGonderildiMi;

    @Past
    private LocalDate dogumTarihi;
    private boolean dogumTarihiGonderildiMi;

    @Email
    @Size(max = 100)
    private String ePosta;
    private boolean ePostaGonderildiMi;

    @Positive
    private Integer departmanId;
    private boolean departmanIdGonderildiMi;

    @PastOrPresent
    private LocalDate isGirisTarihi;
    private boolean isGirisTarihiGonderildiMi;

    private String yoneticiId;
    private boolean yoneticiIdGonderildiMi;

    @Size(max = 30)
    private String telNo;
    private boolean telNoGonderildiMi;

    @Size(max = 100)
    private String mezunOkul;
    private boolean mezunOkulGonderildiMi;

    @Size(max = 100)
    private String mezunBolum;
    private boolean mezunBolumGonderildiMi;

    @Positive
    private Integer mezuniyetYili;
    private boolean mezuniyetYiliGonderildiMi;

    public String getAd() {
        return ad;
    }

    @JsonSetter("ad")  // JsonSetter("ad") anotasyonu bize derki -> JSON içinde ad objesi gelirse bu methodda set'le o objeyi
    public void setAd(String ad) {
        this.ad = ad;
        this.adGonderildiMi = true; // Alan açıkça null gönderilse bile flag true olur.
                                    // Null değere izin verilip verilmeyeceğine service karar verir.
                                    // JSON'da ad alanı hiç bulunmazsa setter çalışmaz ve mevcut ad korunur.
    }

    @JsonIgnore
    public boolean isAdGonderildiMi() {
        return adGonderildiMi;
    }

    public String getSoyad() {
        return soyad;
    }

    @JsonSetter("soyad")
    public void setSoyad(String soyad) {
        this.soyad = soyad;
        this.soyadGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isSoyadGonderildiMi() {
        return soyadGonderildiMi;
    }

    public LocalDate getDogumTarihi() {
        return dogumTarihi;
    }

    @JsonSetter("dogumTarihi")
    public void setDogumTarihi(LocalDate dogumTarihi) {
        this.dogumTarihi = dogumTarihi;
        this.dogumTarihiGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isDogumTarihiGonderildiMi() {
        return dogumTarihiGonderildiMi;
    }

    public String getePosta() {
        return ePosta;
    }

    @JsonSetter("ePosta")
    public void setePosta(String ePosta) {
        this.ePosta = ePosta;
        this.ePostaGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isePostaGonderildiMi() {
        return ePostaGonderildiMi;
    }

    public Integer getDepartmanId() {
        return departmanId;
    }

    @JsonSetter("departmanId")
    public void setDepartmanId(Integer departmanId) {
        this.departmanId = departmanId;
        this.departmanIdGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isDepartmanIdGonderildiMi() {
        return departmanIdGonderildiMi;
    }

    public LocalDate getIsGirisTarihi() {
        return isGirisTarihi;
    }

    @JsonSetter("isGirisTarihi")
    public void setIsGirisTarihi(LocalDate isGirisTarihi) {
        this.isGirisTarihi = isGirisTarihi;
        this.isGirisTarihiGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isIsGirisTarihiGonderildiMi() {
        return isGirisTarihiGonderildiMi;
    }

    public String getYoneticiId() {
        return yoneticiId;
    }

    @JsonSetter("yoneticiId")
    public void setYoneticiId(String yoneticiId) {
        this.yoneticiId = yoneticiId;
        this.yoneticiIdGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isYoneticiIdGonderildiMi() {
        return yoneticiIdGonderildiMi;
    }

    public String getTelNo() {
        return telNo;
    }

    @JsonSetter("telNo")
    public void setTelNo(String telNo) {
        this.telNo = telNo;
        this.telNoGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isTelNoGonderildiMi() {
        return telNoGonderildiMi;
    }

    public String getMezunOkul() {
        return mezunOkul;
    }

    @JsonSetter("mezunOkul")
    public void setMezunOkul(String mezunOkul) {
        this.mezunOkul = mezunOkul;
        this.mezunOkulGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isMezunOkulGonderildiMi() {
        return mezunOkulGonderildiMi;
    }

    public String getMezunBolum() {
        return mezunBolum;
    }

    @JsonSetter("mezunBolum")
    public void setMezunBolum(String mezunBolum) {
        this.mezunBolum = mezunBolum;
        this.mezunBolumGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isMezunBolumGonderildiMi() {
        return mezunBolumGonderildiMi;
    }

    public Integer getMezuniyetYili() {
        return mezuniyetYili;
    }

    @JsonSetter("mezuniyetYili")
    public void setMezuniyetYili(Integer mezuniyetYili) {
        this.mezuniyetYili = mezuniyetYili;
        this.mezuniyetYiliGonderildiMi = true;
    }

    @JsonIgnore
    public boolean isMezuniyetYiliGonderildiMi() {
        return mezuniyetYiliGonderildiMi;
    }

    public boolean herhangiBirAlanGonderildiMi() {
        return adGonderildiMi || soyadGonderildiMi || dogumTarihiGonderildiMi || ePostaGonderildiMi || departmanIdGonderildiMi || isGirisTarihiGonderildiMi
                || yoneticiIdGonderildiMi || telNoGonderildiMi || mezunOkulGonderildiMi || mezunBolumGonderildiMi || mezuniyetYiliGonderildiMi;
    }
}
