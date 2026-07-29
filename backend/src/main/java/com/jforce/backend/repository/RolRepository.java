package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Rol;
import com.jforce.backend.model.enums.RolAdi;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    Optional<Rol> findByRolAdi(RolAdi rolAdi);
}
