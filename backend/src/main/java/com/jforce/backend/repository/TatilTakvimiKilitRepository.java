package com.jforce.backend.repository;

import com.jforce.backend.model.entity.TatilTakvimiKilit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface TatilTakvimiKilitRepository
        extends JpaRepository<TatilTakvimiKilit, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from TatilTakvimiKilit k where k.kilitId = 1")
    Optional<TatilTakvimiKilit> takvimiKilitle();
}
