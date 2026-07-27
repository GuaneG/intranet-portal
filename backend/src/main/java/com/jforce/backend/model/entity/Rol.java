package com.jforce.backend.model.entity;

import com.jforce.backend.model.enums.RolAdi;
import jakarta.persistence.*;

@Entity
@Table(name = "rol")
public class Rol {

    @Id
    @Column(name = "rol_id")
    private Integer rolId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_adi",length = 50,unique = true,nullable = false)
    private RolAdi rolAdi;

    public Integer getRolId() {
        return rolId;
    }

    public void setRolId(Integer rolId) {
        this.rolId = rolId;
    }

    public RolAdi getRolAdi() {
        return rolAdi;
    }

    public void setRolAdi(RolAdi rolAdi) {
        this.rolAdi = rolAdi;
    }

    public Rol() {
    }
}
