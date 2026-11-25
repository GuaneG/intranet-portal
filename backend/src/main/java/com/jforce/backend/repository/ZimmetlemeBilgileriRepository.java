package com.jforce.backend.repository;

import com.jforce.backend.model.entity.ZimmetlemeBilgileri;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ZimmetlemeBilgileriRepository
        extends JpaRepository<ZimmetlemeBilgileri, String> {

    @Query("""
            SELECT z.personelId.personelId
            FROM ZimmetlemeBilgileri z
            WHERE z.zimmetId = :zimmetId
            """)
    Optional<String> findOwnerIdByZimmetId(@Param("zimmetId") String zimmetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT z
            FROM ZimmetlemeBilgileri z
            WHERE z.zimmetId = :zimmetId
            """)
    Optional<ZimmetlemeBilgileri> findByIdForUpdate(
            @Param("zimmetId") String zimmetId
    );

    @Query("""
            SELECT COUNT(z) > 0
            FROM ZimmetlemeBilgileri z
            WHERE z.ekipmanId.ekipmanId = :ekipmanId
              AND z.iadeTarihi IS NULL
            """)
    boolean existsByEkipmanIdAndIadeTarihiIsNull(
            @Param("ekipmanId") String ekipmanId
    );

    @Query("""
            SELECT z
            FROM ZimmetlemeBilgileri z
            JOIN FETCH z.ekipmanId e
            JOIN FETCH e.ekipmanTipiId
            LEFT JOIN FETCH z.talep
            WHERE z.talep.talepId = :talepId
            """)
    Optional<ZimmetlemeBilgileri> findByTalepId(
            @Param("talepId") String talepId
    );

    @Query("""
            SELECT z
            FROM ZimmetlemeBilgileri z
            JOIN FETCH z.ekipmanId e
            JOIN FETCH e.ekipmanTipiId
            JOIN FETCH z.talep t
            WHERE t.talepId IN :talepIds
            ORDER BY z.zimmetId ASC
            """)
    List<ZimmetlemeBilgileri> findAllByTalepIds(
            @Param("talepIds") Collection<String> talepIds
    );

    @Query("""
            SELECT z.ekipmanId.ekipmanId
            FROM ZimmetlemeBilgileri z
            WHERE z.ekipmanId.ekipmanId IN :ekipmanIds
              AND z.iadeTarihi IS NULL
            ORDER BY z.ekipmanId.ekipmanId ASC
            """)
    List<String> findOpenEquipmentIds(
            @Param("ekipmanIds") Collection<String> ekipmanIds
    );

    @Query(
            value = """
                    SELECT z
                    FROM ZimmetlemeBilgileri z
                    JOIN FETCH z.personelId p
                    JOIN FETCH z.ekipmanId e
                    JOIN FETCH e.ekipmanTipiId
                    LEFT JOIN FETCH z.talep
                    WHERE p.personelId = :personelId
                    ORDER BY z.teslimTarihi DESC, z.zimmetId DESC
                    """,
            countQuery = """
                    SELECT COUNT(z)
                    FROM ZimmetlemeBilgileri z
                    WHERE z.personelId.personelId = :personelId
                    """
    )
    Page<ZimmetlemeBilgileri> findKendiZimmetleri(
            @Param("personelId") String personelId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT z
                    FROM ZimmetlemeBilgileri z
                    JOIN FETCH z.personelId p
                    JOIN FETCH p.departman
                    JOIN FETCH z.ekipmanId e
                    JOIN FETCH e.ekipmanTipiId et
                    LEFT JOIN FETCH z.talep
                    WHERE (:aktif IS NULL OR p.calisiyorMu = :aktif)
                      AND (:personelId IS NULL OR p.personelId = :personelId)
                      AND (:ekipmanTipiId IS NULL
                           OR et.ekipmanTipiId = :ekipmanTipiId)
                    ORDER BY z.teslimTarihi DESC, z.zimmetId DESC
                    """,
            countQuery = """
                    SELECT COUNT(z)
                    FROM ZimmetlemeBilgileri z
                    WHERE (:aktif IS NULL OR z.personelId.calisiyorMu = :aktif)
                      AND (:personelId IS NULL
                           OR z.personelId.personelId = :personelId)
                      AND (:ekipmanTipiId IS NULL
                           OR z.ekipmanId.ekipmanTipiId.ekipmanTipiId = :ekipmanTipiId)
                    """
    )
    Page<ZimmetlemeBilgileri> findAdminZimmetleri(
            @Param("aktif") Boolean aktif,
            @Param("personelId") String personelId,
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            Pageable pageable
    );
}
