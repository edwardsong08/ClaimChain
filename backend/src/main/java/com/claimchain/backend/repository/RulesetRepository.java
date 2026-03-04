package com.claimchain.backend.repository;

import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RulesetRepository extends JpaRepository<Ruleset, Long> {
    Optional<Ruleset> findFirstByTypeAndStatus(RulesetType type, RulesetStatus status);
    List<Ruleset> findByTypeOrderByVersionDesc(RulesetType type);

    @Query("select coalesce(max(r.version), 0) from Ruleset r where r.type = :type")
    int findMaxVersionByType(@Param("type") RulesetType type);
}
