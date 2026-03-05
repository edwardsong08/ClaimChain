package com.claimchain.backend.repository;

import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByVerificationStatus(VerificationStatus status);
    boolean existsByRole(Role role);
}
