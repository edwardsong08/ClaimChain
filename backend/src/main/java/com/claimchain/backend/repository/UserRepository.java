package com.claimchain.backend.repository;

import com.claimchain.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    List<User> findByIsVerifiedFalse(); // ✅ Added for AdminController
}
