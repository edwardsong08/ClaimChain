package com.claimchain.backend.repository;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByUser(User user);
}
