package com.claimchain.backend.repository;

import com.claimchain.backend.model.ClaimScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClaimScoreRepository extends JpaRepository<ClaimScore, Long> {
    List<ClaimScore> findByClaimIdOrderByScoredAtDesc(Long claimId);
    Optional<ClaimScore> findFirstByClaimIdOrderByScoredAtDesc(Long claimId);
}
