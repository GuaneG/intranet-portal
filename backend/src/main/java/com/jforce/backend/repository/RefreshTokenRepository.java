package com.jforce.backend.repository;

import com.jforce.backend.model.entity.Personel;
import com.jforce.backend.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken,String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    //delete sorguları bir RefreshToken dönmez, void de yapabilirdik metod tipini ama Long sayesinde kaç satır silindi onu görebileceğiz.
    Long deleteAllByExpiresAtBefore(Instant now);

    Long deleteAllByPersonel(Personel personel);

}
