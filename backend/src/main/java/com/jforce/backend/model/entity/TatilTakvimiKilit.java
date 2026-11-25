package com.jforce.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tatil_takvimi_kilit")
public class TatilTakvimiKilit {

    @Id
    @Column(name = "kilit_id")
    private Integer kilitId;

    protected TatilTakvimiKilit() {
    }

    public TatilTakvimiKilit(Integer kilitId) {
        this.kilitId = kilitId;
    }

    public Integer getKilitId() {
        return kilitId;
    }
}
