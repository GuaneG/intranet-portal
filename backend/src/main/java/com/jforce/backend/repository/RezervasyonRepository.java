package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Rezervasyon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RezervasyonRepository extends JpaRepository<Rezervasyon, String> {

    @Query("""
            SELECT r.olusturan.personelId
            FROM Rezervasyon r
            WHERE r.rezervasyonId = :rezervasyonId
            """)
    Optional<String> findOwnerIdByRezervasyonId(
            @Param("rezervasyonId") String rezervasyonId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Rezervasyon r
            JOIN FETCH r.olusturan p
            JOIN FETCH p.rol
            JOIN FETCH r.oda
            LEFT JOIN FETCH r.iptalEden
            WHERE r.rezervasyonId = :rezervasyonId
            """)
    Optional<Rezervasyon> findByIdForUpdate(
            @Param("rezervasyonId") String rezervasyonId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT r
            FROM Rezervasyon r
            WHERE r.olusturan.personelId = :ownerId
              AND r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND (r.tarih > :bugun
                   OR (r.tarih = :bugun AND r.baslangicSaat > :mevcutSaat))
            ORDER BY r.tarih ASC,
                     r.baslangicSaat ASC,
                     r.rezervasyonId ASC
            """)
    List<Rezervasyon> findBaslamamisAktifOwnerRezervasyonlariForUpdate(
            @Param("ownerId") String ownerId,
            @Param("bugun") LocalDate bugun,
            @Param("mevcutSaat") int mevcutSaat
    );

    @Query("""
            SELECT r
            FROM Rezervasyon r
            JOIN FETCH r.oda o
            JOIN FETCH r.olusturan p
            JOIN FETCH p.rol
            WHERE r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND r.tarih BETWEEN :baslangicTarihi AND :bitisTarihi
              AND (:odaId IS NULL OR o.odaId = :odaId)
            ORDER BY r.tarih ASC,
                     r.baslangicSaat ASC,
                     o.odaId ASC,
                     r.rezervasyonId ASC
            """)
    List<Rezervasyon> findAktifTakvim(
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi,
            @Param("odaId") Integer odaId
    );

    @Query("""
            SELECT r
            FROM Rezervasyon r
            JOIN FETCH r.oda o
            WHERE r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND r.tarih = :tarih
            ORDER BY o.odaAd ASC,
                     o.odaId ASC,
                     r.baslangicSaat ASC,
                     r.rezervasyonId ASC
            """)
    List<Rezervasyon> findBugunkuAktifRezervasyonlar(
            @Param("tarih") LocalDate tarih
    );

    @Query("""
            SELECT COUNT(r) > 0
            FROM Rezervasyon r
            WHERE r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND r.oda.odaId = :odaId
              AND r.tarih = :tarih
              AND r.baslangicSaat < :bitisSaat
              AND r.bitisSaat > :baslangicSaat
            """)
    boolean existsAktifOdaCakismasi(
            @Param("odaId") Integer odaId,
            @Param("tarih") LocalDate tarih,
            @Param("baslangicSaat") Integer baslangicSaat,
            @Param("bitisSaat") Integer bitisSaat
    );

    @Query("""
            SELECT COUNT(r) > 0
            FROM Rezervasyon r
            WHERE r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND r.olusturan.personelId = :olusturanId
              AND r.tarih = :tarih
              AND r.baslangicSaat < :bitisSaat
              AND r.bitisSaat > :baslangicSaat
            """)
    boolean existsAktifOwnerCakismasi(
            @Param("olusturanId") String olusturanId,
            @Param("tarih") LocalDate tarih,
            @Param("baslangicSaat") Integer baslangicSaat,
            @Param("bitisSaat") Integer bitisSaat
    );

    @Query("""
            SELECT COUNT(r) > 0
            FROM Rezervasyon r
            WHERE r.durum = com.jforce.backend.model.enums.RezervasyonDurum.AKTIF
              AND r.tarih BETWEEN :baslangicTarihi AND :bitisTarihi
              AND (
                    :tamGun = true
                    OR r.bitisSaat > :yarimGunBitisSaati
              )
            """)
    boolean existsAktifTatilCakismasi(
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi,
            @Param("tamGun") boolean tamGun,
            @Param("yarimGunBitisSaati") int yarimGunBitisSaati
    );

    @Query(
            value = """
                    SELECT r
                    FROM Rezervasyon r
                    JOIN FETCH r.oda
                    JOIN FETCH r.olusturan p
                    JOIN FETCH p.rol
                    LEFT JOIN FETCH r.iptalEden
                    WHERE p.personelId = :olusturanId
                      AND (:durum IS NULL OR r.durum = :durum)
                      AND (:odaId IS NULL OR r.oda.odaId = :odaId)
                      AND (:baslangic IS NULL OR r.tarih >= :baslangic)
                      AND (:bitis IS NULL OR r.tarih <= :bitis)
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM Rezervasyon r
                    WHERE r.olusturan.personelId = :olusturanId
                      AND (:durum IS NULL OR r.durum = :durum)
                      AND (:odaId IS NULL OR r.oda.odaId = :odaId)
                      AND (:baslangic IS NULL OR r.tarih >= :baslangic)
                      AND (:bitis IS NULL OR r.tarih <= :bitis)
                    """
    )
    Page<Rezervasyon> findKendiGecmisi(
            @Param("olusturanId") String olusturanId,
            @Param("durum") com.jforce.backend.model.enums.RezervasyonDurum durum,
            @Param("odaId") Integer odaId,
            @Param("baslangic") LocalDate baslangic,
            @Param("bitis") LocalDate bitis,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT r
                    FROM Rezervasyon r
                    JOIN FETCH r.oda
                    JOIN FETCH r.olusturan p
                    JOIN FETCH p.rol
                    LEFT JOIN FETCH r.iptalEden
                    WHERE (:durum IS NULL OR r.durum = :durum)
                      AND (:odaId IS NULL OR r.oda.odaId = :odaId)
                      AND (:olusturanId IS NULL OR p.personelId = :olusturanId)
                      AND (:baslangic IS NULL OR r.tarih >= :baslangic)
                      AND (:bitis IS NULL OR r.tarih <= :bitis)
                    """,
            countQuery = """
                    SELECT COUNT(r)
                    FROM Rezervasyon r
                    WHERE (:durum IS NULL OR r.durum = :durum)
                      AND (:odaId IS NULL OR r.oda.odaId = :odaId)
                      AND (:olusturanId IS NULL OR r.olusturan.personelId = :olusturanId)
                      AND (:baslangic IS NULL OR r.tarih >= :baslangic)
                      AND (:bitis IS NULL OR r.tarih <= :bitis)
                    """
    )
    Page<Rezervasyon> findAdminGecmisi(
            @Param("durum") com.jforce.backend.model.enums.RezervasyonDurum durum,
            @Param("odaId") Integer odaId,
            @Param("olusturanId") String olusturanId,
            @Param("baslangic") LocalDate baslangic,
            @Param("bitis") LocalDate bitis,
            Pageable pageable
    );
}
