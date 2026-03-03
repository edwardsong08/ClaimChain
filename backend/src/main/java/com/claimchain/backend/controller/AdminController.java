package com.claimchain.backend.controller;

import com.claimchain.backend.dto.AdminBootstrapRequestDTO;
import com.claimchain.backend.dto.RejectUserRequestDTO;
import com.claimchain.backend.model.User;
import com.claimchain.backend.service.AdminService;
import com.claimchain.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;

    public AdminController(AuthService authService, AdminService adminService) {
        this.authService = authService;
        this.adminService = adminService;
    }

    @PostMapping("/bootstrap")
    public ResponseEntity<Map<String, Object>> bootstrapAdmin(@Valid @RequestBody AdminBootstrapRequestDTO request) {
        Map<String, Object> response = authService.bootstrapAdmin(request);
        return ResponseEntity.status(201).body(response);
    }

    // Get all unverified users
    @GetMapping("/unverified-users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> getUnverifiedUsers() {
        return adminService.getPendingUsers();
    }

    // Verify a user manually
    @PostMapping("/verify-user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> verifyUser(@PathVariable Long userId, Principal principal) {
        adminService.approveUser(userId, principal.getName());
        return ResponseEntity.ok("User verified successfully.");
    }

    @PostMapping("/reject-user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectUser(
            @PathVariable Long userId,
            @Valid @RequestBody RejectUserRequestDTO request,
            Principal principal
    ) {
        adminService.rejectUser(userId, principal.getName(), request.getReason());
        return ResponseEntity.ok("User rejected successfully.");
    }
}
