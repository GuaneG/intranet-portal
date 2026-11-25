package com.jforce.backend.repository;

import com.jforce.backend.model.entity.IzinBilgileri;
import com.jforce.backend.model.enums.TalepDurum;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


public interface IzinBilgileriRepository extends JpaRepository<IzinBilgileri, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IzinBilgileri i WHERE i.izinId = :izinId")
    Optional<IzinBilgileri> findByIdForUpdate(
            @Param("izinId") String izinId
    );

    @Query("""
        SELECT COALESCE(
            SUM(i.isGunuSayisi),
            0.0
        )
        FROM IzinBilgileri i
        WHERE i.personelId.personelId = :personelId
          AND i.izinTuruId.yillikHaktanDuserMi = true
          AND i.izinDurumId.durumKodu = :durumKodu
        """)
    BigDecimal onaylanmisIzinIsGunuToplami(
            @Param("personelId")
            String personelId,

            @Param("durumKodu")
            TalepDurum durumKodu
    );

    @Query("""
        SELECT COALESCE(SUM(i.isGunuSayisi), 0.0)
        FROM IzinBilgileri i
        WHERE i.personelId.personelId = :personelId
          AND i.izinTuruId.yillikHaktanDuserMi = true
          AND i.izinDurumId.durumKodu IN :durumlar
        """)
    BigDecimal yillikHaktanDusenToplam(
            @Param("personelId") String personelId,
            @Param("durumlar") Collection<TalepDurum> durumlar
    );

    @Query("""
        SELECT DISTINCT i.personelId.personelId
        FROM IzinBilgileri i
        WHERE i.personelId.personelId IN :personelIds
          AND i.izinDurumId.durumKodu = :durum
          AND i.baslangicTarihi <= :bugun
          AND i.bitisTarihi >= :bugun
        """)
    List<String> findBugunIzinliPersonelIds(
            @Param("personelIds") Collection<String> personelIds,
            @Param("durum") TalepDurum durum,
            @Param("bugun") LocalDate bugun
    );

    @Query("""
        SELECT i
        FROM IzinBilgileri i
        WHERE i.personelId.personelId = :personelId
          AND i.izinDurumId.durumKodu IN :durumlar
          AND i.baslangicTarihi <= :bitisTarihi
          AND i.bitisTarihi >= :baslangicTarihi
        ORDER BY i.baslangicTarihi ASC, i.izinId ASC
        """)
    List<IzinBilgileri> findCakismaAdaylari(
            @Param("personelId") String personelId,
            @Param("durumlar") Collection<TalepDurum> durumlar,
            @Param("baslangicTarihi") LocalDate baslangicTarihi,
            @Param("bitisTarihi") LocalDate bitisTarihi
    );

    @Query("""
        SELECT i
        FROM IzinBilgileri i
        WHERE i.personelId.personelId = :personelId
          AND (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
        """)
    Page<IzinBilgileri> findKendiTalepleri(
            @Param("personelId") String personelId,
            @Param("durum") TalepDurum durum,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT i
                    FROM IzinBilgileri i
                    JOIN FETCH i.personelId p
                    JOIN FETCH p.departman
                    JOIN FETCH i.izinTuruId
                    JOIN FETCH i.izinDurumId
                    LEFT JOIN FETCH i.islemYapanId
                    WHERE p.calisiyorMu = true
                      AND p.yonetici.personelId = :yoneticiId
                      AND (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
                    """,
            countQuery = """
                    SELECT COUNT(i)
                    FROM IzinBilgileri i
                    WHERE i.personelId.calisiyorMu = true
                      AND i.personelId.yonetici.personelId = :yoneticiId
                      AND (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
                    """
    )
    Page<IzinBilgileri> findTakimKararTalepleri(
            @Param("yoneticiId") String yoneticiId,
            @Param("durum") TalepDurum durum,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT i
                    FROM IzinBilgileri i
                    JOIN FETCH i.personelId p
                    JOIN FETCH p.departman
                    JOIN FETCH i.izinTuruId
                    JOIN FETCH i.izinDurumId
                    LEFT JOIN FETCH i.islemYapanId
                    WHERE (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
                    """,
            countQuery = """
                    SELECT COUNT(i)
                    FROM IzinBilgileri i
                    WHERE (:durum IS NULL OR i.izinDurumId.durumKodu = :durum)
                    """
    )
    Page<IzinBilgileri> findAdminKararTalepleri(
            @Param("durum") TalepDurum durum,
            Pageable pageable
    );
}
