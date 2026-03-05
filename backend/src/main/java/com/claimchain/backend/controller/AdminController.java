package com.claimchain.backend.controller;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.AdminClaimDecisionRequestDTO;
import com.claimchain.backend.dto.AdminBootstrapRequestDTO;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.dto.ClaimFreezeOverrideRequestDTO;
import com.claimchain.backend.dto.ClaimScoreResponseDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.PackageBuildRequestDTO;
import com.claimchain.backend.dto.PackageBuildResponseDTO;
import com.claimchain.backend.dto.PackageCreateRequestDTO;
import com.claimchain.backend.dto.PackageDetailResponseDTO;
import com.claimchain.backend.dto.PackageResponseDTO;
import com.claimchain.backend.dto.RejectUserRequestDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageClaim;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.service.AdminService;
import com.claimchain.backend.service.AuthService;
import com.claimchain.backend.service.ClaimService;
import com.claimchain.backend.service.ClaimScoringPersistenceService;
import com.claimchain.backend.service.PackageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;
    private final ClaimService claimService;
    private final ClaimScoringPersistenceService claimScoringPersistenceService;
    private final PackageService packageService;
    private final UserRepository userRepository;

    public AdminController(
            AuthService authService,
            AdminService adminService,
            ClaimService claimService,
            ClaimScoringPersistenceService claimScoringPersistenceService,
            PackageService packageService,
            UserRepository userRepository
    ) {
        this.authService = authService;
        this.adminService = adminService;
        this.claimService = claimService;
        this.claimScoringPersistenceService = claimScoringPersistenceService;
        this.packageService = packageService;
        this.userRepository = userRepository;
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

    @PostMapping("/packages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PackageResponseDTO> createDraftPackage(
            @Valid @RequestBody(required = false) PackageCreateRequestDTO request,
            Principal principal
    ) {
        Long adminUserId = requirePrincipalUserId(principal);
        Package created = packageService.createDraftPackage(adminUserId, request == null ? null : request.getNotes());
        return ResponseEntity.status(HttpStatus.CREATED).body(toPackageResponse(created));
    }

    @PostMapping("/packages/{id}/claims/{claimId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addClaimToPackage(
            @PathVariable Long id,
            @PathVariable Long claimId,
            Principal principal
    ) {
        Long adminUserId = requirePrincipalUserId(principal);
        packageService.addClaimToPackage(id, claimId, adminUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/packages/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PackageDetailResponseDTO> getPackage(@PathVariable Long id) {
        Package packageEntity = packageService.getPackage(id);
        return ResponseEntity.ok(toPackageDetail(packageEntity));
    }

    @GetMapping("/packages")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PackageResponseDTO>> listPackages(
            @RequestParam(name = "status", required = false) String status
    ) {
        PackageStatus parsedStatus = packageService.parsePackageStatus(status);
        List<PackageResponseDTO> response = packageService.listPackages(parsedStatus)
                .stream()
                .map(this::toPackageResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/packages/build")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PackageBuildResponseDTO> buildPackage(
            @Valid @RequestBody(required = false) PackageBuildRequestDTO request,
            Principal principal
    ) {
        Long adminUserId = requirePrincipalUserId(principal);
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        String notes = request == null ? null : request.getNotes();

        PackageService.BuildPackageResult result = packageService.buildOnePackage(adminUserId, notes, dryRun);
        PackageBuildResponseDTO response = toPackageBuildResponse(result);

        if (dryRun) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

    private Long requirePrincipalUserId(Principal principal) {
        String normalizedEmail = principal == null || principal.getName() == null
                ? null
                : principal.getName().trim().toLowerCase(Locale.ROOT);
        User user = normalizedEmail == null ? null : userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            throw new IllegalArgumentException("Admin user not found.");
        }
        return user.getId();
    }

    private PackageResponseDTO toPackageResponse(Package packageEntity) {
        PackageResponseDTO dto = new PackageResponseDTO();
        dto.setId(packageEntity.getId());
        dto.setStatus(packageEntity.getStatus() == null ? null : packageEntity.getStatus().name());
        dto.setTotalClaims(packageEntity.getTotalClaims());
        dto.setTotalFaceValue(packageEntity.getTotalFaceValue());
        dto.setCreatedAt(packageEntity.getCreatedAt());
        return dto;
    }

    private PackageDetailResponseDTO toPackageDetail(Package packageEntity) {
        PackageDetailResponseDTO dto = new PackageDetailResponseDTO();
        dto.setId(packageEntity.getId());
        dto.setStatus(packageEntity.getStatus() == null ? null : packageEntity.getStatus().name());
        dto.setTotalClaims(packageEntity.getTotalClaims());
        dto.setTotalFaceValue(packageEntity.getTotalFaceValue());
        dto.setNotes(packageEntity.getNotes());
        dto.setCreatedAt(packageEntity.getCreatedAt());
        dto.setCreatedByUserId(packageEntity.getCreatedByUser() == null ? null : packageEntity.getCreatedByUser().getId());
        dto.setRulesetId(packageEntity.getRuleset() == null ? null : packageEntity.getRuleset().getId());
        dto.setRulesetVersion(packageEntity.getRulesetVersion());

        List<Long> claimIds = packageEntity.getPackageClaims().stream()
                .sorted(Comparator.comparing(
                        PackageClaim::getAddedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .map(PackageClaim::getClaim)
                .filter(Objects::nonNull)
                .map(Claim::getId)
                .filter(Objects::nonNull)
                .toList();
        dto.setClaimIds(claimIds);
        return dto;
    }

    private PackageBuildResponseDTO toPackageBuildResponse(PackageService.BuildPackageResult result) {
        PackageBuildResponseDTO dto = new PackageBuildResponseDTO();
        dto.setPackageId(result.getPackageId());
        dto.setDryRun(result.isDryRun());
        dto.setBuildable(result.isBuildable());
        dto.setStatus(result.getStatus());
        dto.setRulesetId(result.getRulesetId());
        dto.setRulesetVersion(result.getRulesetVersion());
        dto.setTotalClaims(result.getTotalClaims());
        dto.setTotalFaceValue(result.getTotalFaceValue());
        dto.setClaimIds(result.getClaimIds());
        dto.setFailureReasons(result.getFailureReasons());
        return dto;
    }

}
