package com.claimchain.backend.repository;

import com.claimchain.backend.model.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    // We can add custom query methods here later if needed
}
