package com.jforce.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rezervasyon")
public class RezervasyonAyarlari {

    private int calismaBaslangicSaati;
    private int calismaBitisSaati;
    private int yarimGunBitisSaati;
    private int maksimumSureSaat;
    private int maksimumIleriGun;

    @PostConstruct
    public void ayarlariDogrula() {
        boolean calismaAraligiGecerli = calismaBaslangicSaati >= 0
                && calismaBaslangicSaati < calismaBitisSaati
                && calismaBitisSaati <= 24;
        boolean yarimGunAraligiGecerli = yarimGunBitisSaati > calismaBaslangicSaati
                && yarimGunBitisSaati < calismaBitisSaati;
        boolean sureGecerli = maksimumSureSaat > 0
                && maksimumSureSaat <= calismaBitisSaati - calismaBaslangicSaati;

        if (!calismaAraligiGecerli
                || !yarimGunAraligiGecerli
                || !sureGecerli
                || maksimumIleriGun < 0) {
            throw new IllegalStateException("Rezervasyon ayarları tutarsız.");
        }
    }

    public int getCalismaBaslangicSaati() {
        return calismaBaslangicSaati;
    }

    public void setCalismaBaslangicSaati(int calismaBaslangicSaati) {
        this.calismaBaslangicSaati = calismaBaslangicSaati;
    }

    public int getCalismaBitisSaati() {
        return calismaBitisSaati;
    }

    public void setCalismaBitisSaati(int calismaBitisSaati) {
        this.calismaBitisSaati = calismaBitisSaati;
    }

    public int getYarimGunBitisSaati() {
        return yarimGunBitisSaati;
    }

    public void setYarimGunBitisSaati(int yarimGunBitisSaati) {
        this.yarimGunBitisSaati = yarimGunBitisSaati;
    }

    public int getMaksimumSureSaat() {
        return maksimumSureSaat;
    }

    public void setMaksimumSureSaat(int maksimumSureSaat) {
        this.maksimumSureSaat = maksimumSureSaat;
    }

    public int getMaksimumIleriGun() {
        return maksimumIleriGun;
    }

    public void setMaksimumIleriGun(int maksimumIleriGun) {
        this.maksimumIleriGun = maksimumIleriGun;
    }
}
