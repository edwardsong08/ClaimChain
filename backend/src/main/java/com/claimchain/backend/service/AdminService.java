package com.claimchain.backend.service;

import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> getAllUsers() {
        return userRepository.findAllWithVerifiedByOrderByIdDesc();
    }

    public List<User> getPendingUsers() {
        return userRepository.findByVerificationStatusWithVerifiedByOrderByIdDesc(VerificationStatus.PENDING);
    }

    @Transactional
    public void approveUser(Long userId, String adminEmail) {
        User admin = findAdminByEmail(adminEmail);

        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new IllegalArgumentException("User not found.");
        }

        User user = optionalUser.get();
        user.setVerificationStatus(VerificationStatus.APPROVED);
        user.setVerifiedAt(Instant.now());
        user.setVerifiedBy(admin);
        user.setRejectedAt(null);
        user.setRejectReason(null);
        userRepository.save(user);
    }

    @Transactional
    public void rejectUser(Long userId, String adminEmail, String reason) {
        User admin = findAdminByEmail(adminEmail);

        Optional<User> optionalUser = userRepository.findById(userId);
        if (optionalUser.isEmpty()) {
            throw new IllegalArgumentException("User not found.");
        }

        User user = optionalUser.get();
        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setRejectedAt(Instant.now());
        user.setRejectReason(reason.trim());
        user.setVerifiedBy(admin);
        user.setVerifiedAt(null);
        userRepository.save(user);
    }

    private User findAdminByEmail(String email) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        User admin = normalizedEmail == null ? null : userRepository.findByEmail(normalizedEmail);
        if (admin == null) {
            throw new IllegalArgumentException("Admin user not found.");
        }
        return admin;
    }
}
