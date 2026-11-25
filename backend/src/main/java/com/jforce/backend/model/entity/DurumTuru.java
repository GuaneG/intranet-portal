package com.jforce.backend.model.entity;

import com.jforce.backend.model.enums.TalepDurum;
import jakarta.persistence.*;

@Entity
@Table(name = "durum_turu")
public class DurumTuru {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "durum_id")
    private Integer durumId;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum_adi", length = 30, nullable = false, unique = true)
    private TalepDurum durumKodu;

    public DurumTuru() {}

    public Integer getDurumId() {
        return durumId;
    }

    public void setDurumId(Integer durumId) {
        this.durumId = durumId;
    }

    public TalepDurum getDurumKodu() {
        return durumKodu;
    }

    public void setDurumKodu(TalepDurum durumKodu) {
        this.durumKodu = durumKodu;
    }
}