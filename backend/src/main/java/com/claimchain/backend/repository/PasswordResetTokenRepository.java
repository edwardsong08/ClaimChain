package com.claimchain.backend.repository;

import com.claimchain.backend.model.PasswordResetToken;
import com.claimchain.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long deleteByUser(User user);

    long deleteByExpiresAtBefore(Instant cutoff);
}
