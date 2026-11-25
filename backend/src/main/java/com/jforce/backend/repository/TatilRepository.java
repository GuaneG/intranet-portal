package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Tatil;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TatilRepository
        extends JpaRepository<Tatil, Integer> {

    @Query("""
            select t
            from Tatil t
            where t.tarih <= :bitisTarihi
              and t.bitisTarihi >= :baslangicTarihi
            order by t.tarih asc, t.tatilId asc
            """)
    List<Tatil> findAllKesisen(
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select t
            from Tatil t
            where t.tarih <= :bitisTarihi
              and t.bitisTarihi >= :baslangicTarihi
            """)
    List<Tatil> cakisanTatilleriKilitleyerekBul(
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select t
            from Tatil t
            where t.tatilId <> :haricTatilId
              and t.tarih <= :bitisTarihi
              and t.bitisTarihi >= :baslangicTarihi
            """)
    List<Tatil> cakisanBaskaTatilleriKilitleyerekBul(
            @Param("haricTatilId") Integer haricTatilId,
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi
    );
}
