package com.claimchain.backend.repository;

import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<User> findByVerificationStatus(VerificationStatus status);
    @Query("select u from User u left join fetch u.verifiedBy order by u.id desc")
    List<User> findAllWithVerifiedByOrderByIdDesc();
    @Query("select u from User u left join fetch u.verifiedBy where u.verificationStatus = :status order by u.id desc")
    List<User> findByVerificationStatusWithVerifiedByOrderByIdDesc(@Param("status") VerificationStatus status);
    boolean existsByRole(Role role);
}
