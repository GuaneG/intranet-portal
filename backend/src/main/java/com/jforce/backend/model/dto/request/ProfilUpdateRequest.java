package com.jforce.backend.model.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// PATCH isteğinde gönderilmeyen alan ile açıkça null gönderilen alanı
// ayırt edebilmek için record yerine alan varlığını izleyen sınıf kullanılır.
public class ProfilUpdateRequest {

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

    @JsonIgnore
    public boolean herhangiBirAlanGonderildiMi() {
        return telNoGonderildiMi || mezunOkulGonderildiMi || mezunBolumGonderildiMi || mezuniyetYiliGonderildiMi;
    }
}