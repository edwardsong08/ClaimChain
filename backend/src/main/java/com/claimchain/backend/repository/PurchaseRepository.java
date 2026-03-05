package com.claimchain.backend.repository;

import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.PurchaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {

    Optional<Purchase> findByStripeCheckoutSessionId(String id);

    Optional<Purchase> findByBuyerUserIdAndPackageEntityIdAndStatus(
            Long buyerId,
            Long packageId,
            PurchaseStatus status
    );

    Optional<Purchase> findByBuyerUserIdAndIdempotencyKey(Long buyerId, String key);
}
