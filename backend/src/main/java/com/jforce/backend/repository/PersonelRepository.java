package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Personel;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.jforce.backend.model.enums.RolAdi;

import java.util.Set;
import java.util.List;
import java.util.Optional;

public interface PersonelRepository extends JpaRepository<Personel,String> {
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select p from Personel p where p.personelId = :personelId")
        Optional<Personel> findByIdForUpdate(
                @Param("personelId") String personelId
        );

        //query derivation ile doğum günü olanları bulamayacağımız için kendimiz JPQL ile özel sorgu yazdık
        @Query("SELECT p FROM Personel p JOIN FETCH p.departman " +
            "WHERE MONTH(p.dogumTarihi) = :ay AND DAY(p.dogumTarihi) = :gun AND p.calisiyorMu = true")
        List<Personel> findByDogumGunu(@Param("ay") int ay, @Param("gun") int gun);

        @Query("""
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman
                WHERE p.calisiyorMu = true
                  AND MONTH(p.dogumTarihi) IN :aylar
                """)
        List<Personel> findAktifDogumGunuTakvimiPersonelleri(
                @Param("aylar") Set<Integer> aylar
        );

        Optional<Personel> findByKullaniciAdi(String kullaniciAdi);

        //aynı kullanıcı varmı kontrol
        boolean existsByKullaniciAdiIgnoreCase(String kullaniciAdi);

        //aynı e posta varmı kontrol
        boolean existsByEPostaIgnoreCase(String ePosta);

        //başka personellerde aynı eposta varmı kontrol
        boolean existsByEPostaIgnoreCaseAndPersonelIdNot(String ePosta, String personelId);

        List<Personel> findAllByYonetici(Personel yonetici);

        boolean existsByDepartman_DepartmanId(Integer departmanId);

        @Query("""
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman
                JOIN FETCH p.rol
                LEFT JOIN FETCH p.yonetici
                WHERE p.calisiyorMu = true
                """)
        List<Personel> findAllAktifHiyerarsiPersonelleri();

        @Query("""
                SELECT p.personelId
                FROM Personel p
                WHERE p.calisiyorMu = true
                  AND (
                        :departmanId IS NULL
                        OR p.departman.departmanId = :departmanId
                  )
                  AND (
                        :yetenekId IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM PersonelYetenek py
                            WHERE py.personel = p
                              AND py.yetenek.yetenekId = :yetenekId
                        )
                  )
                  AND (
                        :arama IS NULL
                        OR LOWER(p.adi) LIKE
                           CONCAT('%', CONCAT(LOWER(:arama), '%'))
                        OR LOWER(p.soyadi) LIKE
                           CONCAT('%', CONCAT(LOWER(:arama), '%'))
                        OR LOWER(CONCAT(p.adi, CONCAT(' ', p.soyadi))) LIKE
                           CONCAT('%', CONCAT(LOWER(:arama), '%'))
                  )
                """)
        List<String> findAktifHiyerarsiEslesenPersonelIds(
                @Param("arama") String arama,
                @Param("departmanId") Integer departmanId,
                @Param("yetenekId") Integer yetenekId
        );

        @Query("""
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman
                JOIN FETCH p.rol
                LEFT JOIN FETCH p.yonetici y
                LEFT JOIN FETCH y.departman
                LEFT JOIN FETCH y.rol
                LEFT JOIN FETCH y.yonetici yy
                LEFT JOIN FETCH yy.departman
                LEFT JOIN FETCH yy.rol
                WHERE p.personelId = :personelId
                """)
        Optional<Personel> findYoneticiZinciriAktoruById(
                @Param("personelId") String personelId
        );

    @Query(
            value = """
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman d
                JOIN FETCH p.rol r
                WHERE p.personelId <> :aktorId
                  AND r.rolAdi IN :izinliRoller
                  AND (
                        :arama IS NULL
                        OR LOWER(p.adi) LIKE
                           CONCAT('%',CONCAT(LOWER(:arama), '%'))
                        OR LOWER(p.soyadi) LIKE
                           CONCAT('%',CONCAT(LOWER(:arama), '%'))
                        OR LOWER( CONCAT( p.adi,CONCAT(' ', p.soyadi))) LIKE
                           CONCAT('%',CONCAT(LOWER(:arama), '%'))
                        OR LOWER(p.kullaniciAdi) LIKE
                           CONCAT('%',CONCAT(LOWER(:arama), '%')))
                  AND (
                        :departmanId IS NULL
                        OR d.departmanId = :departmanId
                  )
                  AND (
                        :rol IS NULL
                        OR r.rolAdi = :rol
                  )
                  AND (
                        :aktif IS NULL
                        OR p.calisiyorMu = :aktif
                  )
                ORDER BY
                    p.adi ASC,
                    p.soyadi ASC,
                    p.personelId ASC
                """,
            countQuery = """
                SELECT COUNT(p)
                FROM Personel p
                WHERE p.personelId <> :aktorId
                  AND p.rol.rolAdi IN :izinliRoller
                  AND (
                        :arama IS NULL
                        OR LOWER(p.adi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(p.soyadi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(
                               CONCAT(
                                   p.adi,
                                   CONCAT(' ', p.soyadi)
                               )
                           ) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(p.kullaniciAdi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                  )
                  AND (
                        :departmanId IS NULL
                        OR p.departman.departmanId = :departmanId
                  )
                  AND (
                        :rol IS NULL
                        OR p.rol.rolAdi = :rol
                  )
                  AND (
                        :aktif IS NULL
                        OR p.calisiyorMu = :aktif
                  )
                """
    )
    Page<Personel> findPersonelYonetimSayfasi(
            @Param("aktorId")
            String aktorId,
            @Param("izinliRoller")
            Set<RolAdi> izinliRoller,
            @Param("arama")
            String arama,
            @Param("departmanId")
            Integer departmanId,
            @Param("rol")
            RolAdi rol,
            @Param("aktif")
            Boolean aktif,
            Pageable pageable
    );
    @Query(
            value = """
                SELECT p
                FROM Personel p
                JOIN FETCH p.departman d
                JOIN FETCH p.rol r
                WHERE p.calisiyorMu = true
                  AND r.rolAdi IN :gorunurRoller
                  AND (
                        :gorunurDepartmanId IS NULL
                        OR d.departmanId = :gorunurDepartmanId
                  )
                  AND (
                        :departmanId IS NULL
                        OR d.departmanId = :departmanId
                  )
                  AND (
                        :yetenekId IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM PersonelYetenek py
                            WHERE py.personel = p
                              AND py.yetenek.yetenekId = :yetenekId
                        )
                  )
                  AND (
                        :arama IS NULL
                        OR LOWER(p.adi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(p.soyadi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(
                               CONCAT(
                                   p.adi,
                                   CONCAT(' ', p.soyadi)
                               )
                           ) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                  )
                ORDER BY
                    p.adi ASC,
                    p.soyadi ASC,
                    p.personelId ASC
                """,
            countQuery = """
                SELECT COUNT(p)
                FROM Personel p
                WHERE p.calisiyorMu = true
                  AND p.rol.rolAdi IN :gorunurRoller
                  AND (
                        :gorunurDepartmanId IS NULL
                        OR p.departman.departmanId = :gorunurDepartmanId
                  )
                  AND (
                        :departmanId IS NULL
                        OR p.departman.departmanId = :departmanId
                  )
                  AND (
                        :yetenekId IS NULL
                        OR EXISTS (
                            SELECT 1
                            FROM PersonelYetenek py
                            WHERE py.personel = p
                              AND py.yetenek.yetenekId = :yetenekId
                        )
                  )
                  AND (
                        :arama IS NULL
                        OR LOWER(p.adi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(p.soyadi) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                        OR LOWER(
                               CONCAT(
                                   p.adi,
                                   CONCAT(' ', p.soyadi)
                               )
                           ) LIKE
                           CONCAT(
                               '%',
                               CONCAT(LOWER(:arama), '%')
                           )
                  )
                """
    )
    Page<Personel> findAktifEkipDizini(
            @Param("gorunurRoller")
            Set<RolAdi> gorunurRoller,
            @Param("gorunurDepartmanId")
            Integer gorunurDepartmanId,
            @Param("departmanId")
            Integer departmanId,
            @Param("yetenekId")
            Integer yetenekId,
            @Param("arama")
            String arama,
            Pageable pageable
    );
    //jpql ile filtrelemeyi yapmış olduk, isnull çalışırsa filtre yok,varsa OR koşulundan sonraki çalışır
    //2. sorgu kaç kayıt olduğunu göstermek için, pagination mantığı gereği.

    @Query("""
        SELECT p
        FROM Personel p
        JOIN FETCH p.departman
        JOIN FETCH p.rol
        LEFT JOIN FETCH p.yonetici
        WHERE p.personelId = :personelId
        """)
    Optional<Personel> findYonetimDetayiById(
            @Param("personelId")
            String personelId
    );

    @Query("""
        SELECT p
        FROM Personel p
        WHERE p.calisiyorMu = true
          AND p.rol.rolAdi = :yoneticiRolu
          AND p.departman.departmanId = :departmanId
          AND (
                :haricPersonelId IS NULL
                OR p.personelId <> :haricPersonelId
          )
        ORDER BY
            p.adi ASC,
            p.soyadi ASC,
            p.personelId ASC
        """)
    List<Personel> findAtanabilirYoneticiler(
            @Param("departmanId")
            Integer departmanId,
            @Param("yoneticiRolu")
            RolAdi yoneticiRolu,
            @Param("haricPersonelId")
            String haricPersonelId
    );

}
