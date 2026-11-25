package com.jforce.backend.repository;

import com.jforce.backend.model.entity.EkipmanBilgileri;
import com.jforce.backend.model.enums.EkipmanDurum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EkipmanBilgileriRepository
        extends JpaRepository<EkipmanBilgileri, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e
            FROM EkipmanBilgileri e
            WHERE e.ekipmanId = :id
            """)
    Optional<EkipmanBilgileri> findByIdForUpdate(@Param("id") String id);

    @Query(
            value = """
                    SELECT e
                    FROM EkipmanBilgileri e
                    JOIN FETCH e.ekipmanTipiId t
                    WHERE t.ekipmanTipiId = :tipId
                      AND e.ekipmanDurum = :durum
                    ORDER BY e.marka ASC,
                             e.model ASC,
                             e.seriNo ASC,
                             e.ekipmanId ASC
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM EkipmanBilgileri e
                    WHERE e.ekipmanTipiId.ekipmanTipiId = :tipId
                      AND e.ekipmanDurum = :durum
                    """
    )
    Page<EkipmanBilgileri> findUygunCihazlar(
            @Param("tipId") Integer tipId,
            @Param("durum") EkipmanDurum durum,
            Pageable pageable
    );

    boolean existsBySeriNoIgnoreCase(String seriNo);

    boolean existsBySeriNoIgnoreCaseAndEkipmanIdNot(
            String seriNo,
            String ekipmanId
    );

    @Query(
            value = """
                    SELECT e
                    FROM EkipmanBilgileri e
                    JOIN FETCH e.ekipmanTipiId t
                    WHERE (:ekipmanTipiId IS NULL
                           OR t.ekipmanTipiId = :ekipmanTipiId)
                      AND (:durum IS NULL OR e.ekipmanDurum = :durum)
                      AND (:arama IS NULL
                           OR LOWER(e.marka) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
                           OR LOWER(e.model) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
                           OR LOWER(e.seriNo) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%')))
                    ORDER BY t.tipAdi ASC,
                             e.marka ASC,
                             e.model ASC,
                             e.seriNo ASC,
                             e.ekipmanId ASC
                    """,
            countQuery = """
                    SELECT COUNT(e)
                    FROM EkipmanBilgileri e
                    WHERE (:ekipmanTipiId IS NULL
                           OR e.ekipmanTipiId.ekipmanTipiId = :ekipmanTipiId)
                      AND (:durum IS NULL OR e.ekipmanDurum = :durum)
                      AND (:arama IS NULL
                           OR LOWER(e.marka) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
                           OR LOWER(e.model) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%'))
                           OR LOWER(e.seriNo) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%')))
                    """
    )
    Page<EkipmanBilgileri> findAdminSayfasi(
            @Param("ekipmanTipiId") Integer ekipmanTipiId,
            @Param("durum") EkipmanDurum durum,
            @Param("arama") String arama,
            Pageable pageable
    );
}
