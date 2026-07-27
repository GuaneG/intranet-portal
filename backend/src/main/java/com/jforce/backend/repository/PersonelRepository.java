package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Personel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PersonelRepository extends JpaRepository<Personel,String> {
    //query derivation ile doğum günü olanları bulamayacağımız için kendimiz JPQL ile özel sorgu yazdık
    @Query("SELECT p FROM Personel p JOIN FETCH p.departman " +
            "WHERE MONTH(p.dogumTarihi) = :ay AND DAY(p.dogumTarihi) = :gun AND p.calisiyorMu = true")
    List<Personel> findByDogumGunu(@Param("ay") int ay, @Param("gun") int gun);
     Optional<Personel> findByKullaniciAdi(String kullaniciAdi);
}
