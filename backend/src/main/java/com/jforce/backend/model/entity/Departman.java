package com.jforce.backend.model.entity;

import jakarta.persistence.*;
import org.springframework.stereotype.Component;

@Entity
@Table(name = "departman")
public class Departman {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "departman_id")
    private Integer departmanId;

    @Column(name = "departman_adi" , nullable = false, unique = true,length = 50)
    private String departmanAdi;

    //
    public Departman() {
    }

    public Integer getDepartmanId() {
        return departmanId;
    }

    public String getDepartmanAdi() {
        return departmanAdi;
    }

    public void setDepartmanId(Integer departmanId) {
        this.departmanId = departmanId;
    }

    public void setDepartmanAdi(String departmanAdi) {
        this.departmanAdi = departmanAdi;
    }
}
