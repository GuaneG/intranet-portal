package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Oda;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OdaRepository extends JpaRepository<Oda, Integer> {

    List<Oda> findAllByOrderByOdaAdAscOdaIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Oda o WHERE o.odaId = :odaId")
    Optional<Oda> findByIdForUpdate(@Param("odaId") Integer odaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Oda o ORDER BY o.odaId ASC")
    List<Oda> findAllForUpdate();
}
