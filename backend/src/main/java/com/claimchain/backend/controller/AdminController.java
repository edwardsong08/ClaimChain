package com.claimchain.backend.controller;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.AdminClaimDecisionRequestDTO;
import com.claimchain.backend.dto.AdminBootstrapRequestDTO;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.dto.ClaimFreezeOverrideRequestDTO;
import com.claimchain.backend.dto.ClaimScoreResponseDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.RejectUserRequestDTO;
import com.claimchain.backend.model.User;
import com.claimchain.backend.service.AdminService;
import com.claimchain.backend.service.AuthService;
import com.claimchain.backend.service.ClaimService;
import com.claimchain.backend.service.ClaimScoringPersistenceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;
    private final ClaimService claimService;
    private final ClaimScoringPersistenceService claimScoringPersistenceService;

    public AdminController(
            AuthService authService,
            AdminService adminService,
            ClaimService claimService,
            ClaimScoringPersistenceService claimScoringPersistenceService
    ) {
        this.authService = authService;
        this.adminService = adminService;
        this.claimService = claimService;
        this.claimScoringPersistenceService = claimScoringPersistenceService;
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

    @PostMapping("/claims/{claimId}/override-freeze")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> requestClaimFreezeOverride(
            @PathVariable Long claimId,
            @Valid @RequestBody ClaimFreezeOverrideRequestDTO request,
            Principal principal
    ) {
        claimService.requestFreezeOverride(claimId, request, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/claims/{claimId}/scores")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ClaimScoreResponseDTO>> listClaimScores(
            @PathVariable Long claimId,
            Principal principal
    ) {
        return ResponseEntity.ok(claimScoringPersistenceService.listScoreRunsForClaim(claimId, principal.getName()));
    }

    @PostMapping("/claims/{claimId}/rescore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> rescoreClaim(
            @PathVariable Long claimId,
            Principal principal
    ) {
        claimService.rescoreClaim(claimId, principal.getName());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ClaimService.ClaimFrozenException.class)
    public ResponseEntity<ApiErrorResponse> handleClaimFrozen(
            ClaimService.ClaimFrozenException ex,
            HttpServletRequest request
    ) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        ApiErrorResponse body = new ApiErrorResponse(
                "CLAIM_FROZEN",
                ex.getMessage(),
                List.of(ex.getMessage()),
                Instant.now(),
                requestId
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
