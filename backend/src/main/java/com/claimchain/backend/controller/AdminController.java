package com.claimchain.backend.controller;

import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    // Get all unverified users
    @GetMapping("/unverified-users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getUnverifiedUsers() {
        return userRepository.findByIsVerifiedFalse();
    }

    // Verify a user manually
    @PostMapping("/verify-user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> verifyUser(@PathVariable Long userId) {
        Optional<User> optionalUser = userRepository.findById(userId);

        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            user.setVerified(true);
            userRepository.save(user);
            return ResponseEntity.ok("User verified successfully.");
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
