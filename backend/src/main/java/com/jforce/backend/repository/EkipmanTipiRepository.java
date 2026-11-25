package com.jforce.backend.repository;

import com.jforce.backend.model.entity.EkipmanTipi;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EkipmanTipiRepository
        extends JpaRepository<EkipmanTipi, Integer> {

    List<EkipmanTipi> findAllByAktifMiTrueOrderByTipAdiAscEkipmanTipiIdAsc();

    boolean existsByTipAdiIgnoreCase(String tipAdi);

    boolean existsByTipAdiIgnoreCaseAndEkipmanTipiIdNot(
            String tipAdi,
            Integer ekipmanTipiId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EkipmanTipi t WHERE t.ekipmanTipiId = :id")
    Optional<EkipmanTipi> findByIdForUpdate(@Param("id") Integer id);

    @Query(
            value = """
                    SELECT t
                    FROM EkipmanTipi t
                    WHERE (:arama IS NULL
                           OR LOWER(t.tipAdi) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%')))
                      AND (:aktif IS NULL OR t.aktifMi = :aktif)
                    ORDER BY t.tipAdi ASC, t.ekipmanTipiId ASC
                    """,
            countQuery = """
                    SELECT COUNT(t)
                    FROM EkipmanTipi t
                    WHERE (:arama IS NULL
                           OR LOWER(t.tipAdi) LIKE CONCAT('%', CONCAT(LOWER(:arama), '%')))
                      AND (:aktif IS NULL OR t.aktifMi = :aktif)
                    """
    )
    Page<EkipmanTipi> findAdminSayfasi(
            @Param("arama") String arama,
            @Param("aktif") Boolean aktif,
            Pageable pageable
    );
}
