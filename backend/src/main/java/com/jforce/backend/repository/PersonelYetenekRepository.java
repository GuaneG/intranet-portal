package com.jforce.backend.repository;

import com.jforce.backend.model.entity.PersonelYetenek;
import com.jforce.backend.model.entity.PersonelYetenekId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PersonelYetenekRepository extends JpaRepository<PersonelYetenek, PersonelYetenekId> {

    boolean existsByYetenek_YetenekId(Integer yetenekId);

    @Query("""
            SELECT py
            FROM PersonelYetenek py
            JOIN FETCH py.yetenek y
            WHERE py.personel.personelId = :personelId
            ORDER BY y.yetenekAdi ASC
            """)
    //tek personel için yetenek sorgusu profilde gösterilcek
    List<PersonelYetenek> findAllByPersonelIdWithYetenek(@Param("personelId") String personelId);

    @Query("""
            SELECT py
            FROM PersonelYetenek py
            JOIN FETCH py.yetenek y
            WHERE py.personel.personelId IN :personelIds
            ORDER BY
                py.personel.personelId ASC,
                y.yetenekAdi ASC
            """)
    //verilen personel listesine göre yetenek sorgusu ekip dizininnde gösterilcek
    List<PersonelYetenek> findAllByPersonelIdsWithYetenek(@Param("personelIds") List<String> personelIds);

}
