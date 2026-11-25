package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Departman;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DepartmanRepository
        extends JpaRepository<Departman, Integer> {

    List<Departman> findAllByOrderByDepartmanAdiAscDepartmanIdAsc();

    boolean existsByDepartmanAdiIgnoreCase(String departmanAdi);

    boolean existsByDepartmanAdiIgnoreCaseAndDepartmanIdNot(String departmanAdi, Integer departmanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Departman d WHERE d.departmanId = :departmanId")
    Optional<Departman> findByIdForUpdate(@Param("departmanId") Integer departmanId);
}
