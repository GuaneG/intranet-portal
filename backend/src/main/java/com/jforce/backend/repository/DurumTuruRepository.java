package com.jforce.backend.repository;

import com.jforce.backend.model.entity.DurumTuru;
import com.jforce.backend.model.enums.TalepDurum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DurumTuruRepository
        extends JpaRepository<DurumTuru, Integer> {

    Optional<DurumTuru> findByDurumKodu(TalepDurum durumKodu);
}
