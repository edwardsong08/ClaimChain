package com.claimchain.backend.repository;

import com.claimchain.backend.model.PurchaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseEventRepository extends JpaRepository<PurchaseEvent, Long> {
    boolean existsByStripeEventId(String stripeEventId);
}
