package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Yetenek;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface YetenekRepository extends JpaRepository<Yetenek, Integer> {
    List<Yetenek> findAllByOrderByYetenekAdiAscYetenekIdAsc();

    boolean existsByYetenekAdiIgnoreCase(String yetenekAdi);

    boolean existsByYetenekAdiIgnoreCaseAndYetenekIdNot(String yetenekAdi, Integer yetenekId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT y FROM Yetenek y WHERE y.yetenekId = :yetenekId")
    Optional<Yetenek> findByIdForUpdate(@Param("yetenekId") Integer yetenekId);
}
