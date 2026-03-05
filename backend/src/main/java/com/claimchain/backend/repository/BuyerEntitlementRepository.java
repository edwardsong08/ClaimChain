package com.claimchain.backend.repository;

import com.claimchain.backend.model.BuyerEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BuyerEntitlementRepository extends JpaRepository<BuyerEntitlement, Long> {
}
