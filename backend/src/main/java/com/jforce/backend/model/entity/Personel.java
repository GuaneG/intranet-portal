package com.jforce.backend.model.entity;


import jakarta.persistence.*;

@Entity
@Table(name = "personel")
public class Personel {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
}
