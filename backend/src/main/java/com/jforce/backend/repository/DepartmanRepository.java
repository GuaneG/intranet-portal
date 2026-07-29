package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Departman;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmanRepository extends JpaRepository<Departman, Integer> {
}
