package com.jforce.backend.model.enums;

public enum TalepDurum {

    ONAY_BEKLIYOR("Onay bekliyor"),
    ONAYLANDI("Onaylandı"),
    REDDEDILDI("Reddedildi"),
    IPTAL_EDILDI("İptal edildi");

    private final String gorunenAd;

    TalepDurum(String gorunenAd) {
        this.gorunenAd = gorunenAd;
    }

    public String getGorunenAd() {
        return gorunenAd;
    }
}
