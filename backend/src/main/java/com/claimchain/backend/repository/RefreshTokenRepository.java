package com.claimchain.backend.repository;

import com.claimchain.backend.model.RefreshToken;
import com.claimchain.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    long deleteByUser(User user);

    long deleteByExpiresAtBefore(Instant now);
}