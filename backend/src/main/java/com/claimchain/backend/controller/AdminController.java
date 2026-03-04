package com.claimchain.backend.controller;

import com.claimchain.backend.dto.AdminClaimDecisionRequestDTO;
import com.claimchain.backend.dto.AdminBootstrapRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.RejectUserRequestDTO;
import com.claimchain.backend.model.User;
import com.claimchain.backend.service.AdminService;
import com.claimchain.backend.service.AuthService;
import com.claimchain.backend.service.ClaimService;
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
    private final ClaimService claimService;

    public AdminController(AuthService authService, AdminService adminService, ClaimService claimService) {
        this.authService = authService;
        this.adminService = adminService;
        this.claimService = claimService;
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

    @GetMapping("/claims")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClaimResponseDTO>> listClaimsByStatus(
            @RequestParam(name = "status", defaultValue = "SUBMITTED") String status
    ) {
        return ResponseEntity.ok(claimService.getClaimsByStatusForAdmin(status));
    }

    @PostMapping("/claims/{claimId}/decision")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponseDTO> reviewClaim(
            @PathVariable Long claimId,
            @Valid @RequestBody AdminClaimDecisionRequestDTO request,
            Principal principal
    ) {
        ClaimResponseDTO response = claimService.reviewClaim(claimId, request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/claims/{claimId}/start-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClaimResponseDTO> startClaimReview(
            @PathVariable Long claimId,
            Principal principal
    ) {
        ClaimResponseDTO response = claimService.startReview(claimId, principal.getName());
        return ResponseEntity.ok(response);
    }
}
