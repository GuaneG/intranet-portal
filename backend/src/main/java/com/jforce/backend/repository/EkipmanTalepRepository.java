package com.jforce.backend.repository;

import com.jforce.backend.model.entity.EkipmanTalep;
import com.jforce.backend.model.enums.TalepDurum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EkipmanTalepRepository
        extends JpaRepository<EkipmanTalep, String> {

    @Query("""
            SELECT t.personel.personelId
            FROM EkipmanTalep t
            WHERE t.talepId = :talepId
            """)
    Optional<String> findOwnerIdByTalepId(@Param("talepId") String talepId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM EkipmanTalep t
            WHERE t.talepId = :talepId
            """)
    Optional<EkipmanTalep> findByIdForUpdate(
            @Param("talepId") String talepId
    );

    @Query("""
            SELECT COUNT(t) > 0
            FROM EkipmanTalep t
            WHERE t.personel.personelId = :personelId
              AND t.ekipmanTipi.ekipmanTipiId = :ekipmanTipiId
              AND t.talepDurum.durumKodu = :durum
            """)
    boolean existsPendingByPersonelAndTip(
            @Param("personelId") String personelId,
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            @Param("durum") TalepDurum durum
    );

    @Query("""
            SELECT COUNT(t) > 0
            FROM EkipmanTalep t
            WHERE t.ekipmanTipi.ekipmanTipiId = :ekipmanTipiId
              AND t.talepDurum.durumKodu = :durum
            """)
    boolean existsByEkipmanTipiIdAndDurum(
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            @Param("durum") TalepDurum durum
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT t
            FROM EkipmanTalep t
            WHERE t.personel.personelId = :personelId
              AND t.talepDurum.durumKodu = :durum
            ORDER BY t.talepId ASC
            """)
    List<EkipmanTalep> findPendingByPersonelForUpdate(
            @Param("personelId") String personelId,
            @Param("durum") TalepDurum durum
    );

    @Query(
            value = """
                    SELECT t
                    FROM EkipmanTalep t
                    JOIN FETCH t.personel p
                    JOIN FETCH p.departman
                    JOIN FETCH t.ekipmanTipi et
                    JOIN FETCH t.talepDurum td
                    LEFT JOIN FETCH t.islemYapanId
                    WHERE p.personelId = :personelId
                      AND (:durum IS NULL OR td.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR et.ekipmanTipiId = :ekipmanTipiId)
                    ORDER BY t.talepTarihi DESC, t.talepId DESC
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM EkipmanTalep t
                    WHERE t.personel.personelId = :personelId
                      AND (:durum IS NULL OR t.talepDurum.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR t.ekipmanTipi.ekipmanTipiId = :ekipmanTipiId)
                    """
    )
    Page<EkipmanTalep> findKendiTalepleri(
            @Param("personelId") String personelId,
            @Param("durum") TalepDurum durum,
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT t
                    FROM EkipmanTalep t
                    JOIN FETCH t.personel p
                    JOIN FETCH p.departman
                    JOIN FETCH t.ekipmanTipi et
                    JOIN FETCH t.talepDurum td
                    LEFT JOIN FETCH t.islemYapanId
                    WHERE p.calisiyorMu = true
                      AND p.yonetici.personelId = :yoneticiId
                      AND p.personelId <> :yoneticiId
                      AND (:durum IS NULL OR td.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR et.ekipmanTipiId = :ekipmanTipiId)
                    ORDER BY t.talepTarihi DESC, t.talepId DESC
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM EkipmanTalep t
                    WHERE t.personel.calisiyorMu = true
                      AND t.personel.yonetici.personelId = :yoneticiId
                      AND t.personel.personelId <> :yoneticiId
                      AND (:durum IS NULL OR t.talepDurum.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR t.ekipmanTipi.ekipmanTipiId = :ekipmanTipiId)
                    """
    )
    Page<EkipmanTalep> findTakimTalepleri(
            @Param("yoneticiId") String yoneticiId,
            @Param("durum") TalepDurum durum,
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT t
                    FROM EkipmanTalep t
                    JOIN FETCH t.personel p
                    JOIN FETCH p.departman
                    JOIN FETCH t.ekipmanTipi et
                    JOIN FETCH t.talepDurum td
                    LEFT JOIN FETCH t.islemYapanId
                    WHERE (:durum IS NULL OR td.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR et.ekipmanTipiId = :ekipmanTipiId)
                      AND (:personelId IS NULL OR p.personelId = :personelId)
                    ORDER BY t.talepTarihi DESC, t.talepId DESC
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM EkipmanTalep t
                    WHERE (:durum IS NULL OR t.talepDurum.durumKodu = :durum)
                      AND (:ekipmanTipiId IS NULL
                           OR t.ekipmanTipi.ekipmanTipiId = :ekipmanTipiId)
                      AND (:personelId IS NULL
                           OR t.personel.personelId = :personelId)
                    """
    )
    Page<EkipmanTalep> findAdminTalepleri(
            @Param("durum") TalepDurum durum,
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            @Param("personelId") String personelId,
            Pageable pageable
    );
}
