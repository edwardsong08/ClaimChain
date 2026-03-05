package com.claimchain.backend.repository;

import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PackageRepository extends JpaRepository<Package, Long> {

    List<Package> findAllByOrderByCreatedAtDesc();

    List<Package> findByStatusOrderByCreatedAtDesc(PackageStatus status);

    @Query("select distinct p from Package p left join fetch p.packageClaims pc left join fetch pc.claim where p.id = :id")
    Optional<Package> findByIdWithClaims(@Param("id") Long id);
}
