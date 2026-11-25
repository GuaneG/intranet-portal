package com.jforce.backend.repository;

import com.jforce.backend.model.entity.IzinTuru;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IzinTuruRepository extends JpaRepository<IzinTuru, Integer> {

    List<IzinTuru> findAllByOrderByIzinTuruAdiAscIzinTuruIdAsc();
}
