package com.claimchain.backend.repository;

import com.claimchain.backend.model.AnonymizedClaimView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnonymizedClaimViewRepository extends JpaRepository<AnonymizedClaimView, Long> {

    @Query("select v from AnonymizedClaimView v join fetch v.claim where v.packageEntity.id = :packageId order by v.scoreTotal desc, v.id asc")
    List<AnonymizedClaimView> findByPackageIdOrderByScoreTotalDesc(@Param("packageId") Long packageId);

    Optional<AnonymizedClaimView> findByPackageEntityIdAndClaimId(Long packageId, Long claimId);
}
