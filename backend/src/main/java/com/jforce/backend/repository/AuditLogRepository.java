package com.jforce.backend.repository;

import com.jforce.backend.model.entity.AuditLog;
import com.jforce.backend.model.enums.AuditEylem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog,String> {

    @EntityGraph(attributePaths = {"personel", "hedefPersonel"})
    @Query(
            value = """
                    SELECT audit
                    FROM AuditLog audit
                    WHERE (:baslangic IS NULL OR audit.olusmaZamani >= :baslangic)
                      AND (:bitisHaric IS NULL OR audit.olusmaZamani < :bitisHaric)
                      AND (:eylemTipi IS NULL OR audit.eylemTipi = :eylemTipi)
                      AND (:aktorPersonelId IS NULL OR audit.personel.personelId = :aktorPersonelId)
                      AND (:hedefPersonelId IS NULL OR audit.hedefPersonel.personelId = :hedefPersonelId)
                    ORDER BY audit.olusmaZamani DESC, audit.logId DESC
                    """,
            countQuery = """
                    SELECT COUNT(audit)
                    FROM AuditLog audit
                    WHERE (:baslangic IS NULL OR audit.olusmaZamani >= :baslangic)
                      AND (:bitisHaric IS NULL OR audit.olusmaZamani < :bitisHaric)
                      AND (:eylemTipi IS NULL OR audit.eylemTipi = :eylemTipi)
                      AND (:aktorPersonelId IS NULL OR audit.personel.personelId = :aktorPersonelId)
                      AND (:hedefPersonelId IS NULL OR audit.hedefPersonel.personelId = :hedefPersonelId)
                    """
    )
    Page<AuditLog> findAuditKayitlari(
            @Param("baslangic") Instant baslangic,
            @Param("bitisHaric") Instant bitisHaric,
            @Param("eylemTipi") AuditEylem eylemTipi,
            @Param("aktorPersonelId") String aktorPersonelId,
            @Param("hedefPersonelId") String hedefPersonelId,
            Pageable pageable
    );
}
