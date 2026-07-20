package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Personel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonelRepository extends JpaRepository<Personel,String> {
     Optional<Personel> findByKullaniciAdi(String kullaniciAdi);
}
