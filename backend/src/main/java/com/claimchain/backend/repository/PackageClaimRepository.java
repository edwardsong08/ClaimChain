package com.claimchain.backend.repository;

import com.claimchain.backend.model.PackageClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PackageClaimRepository extends JpaRepository<PackageClaim, Long> {
    boolean existsByPackageEntityIdAndClaimId(Long packageId, Long claimId);
}
