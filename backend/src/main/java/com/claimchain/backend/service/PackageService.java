package com.claimchain.backend.service;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageClaim;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.PackageClaimRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PackageService {

    private final PackageRepository packageRepository;
    private final PackageClaimRepository packageClaimRepository;
    private final ClaimRepository claimRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PackageService(
            PackageRepository packageRepository,
            PackageClaimRepository packageClaimRepository,
            ClaimRepository claimRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packageRepository = packageRepository;
        this.packageClaimRepository = packageClaimRepository;
        this.claimRepository = claimRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Package createDraftPackage(Long adminUserId, String notesOptional) {
        User admin = requireAdminById(adminUserId);

        Package draft = new Package();
        draft.setStatus(PackageStatus.DRAFT);
        draft.setCreatedByUser(admin);
        draft.setNotes(normalizeNotes(notesOptional));
        draft.setTotalClaims(0);
        draft.setTotalFaceValue(BigDecimal.ZERO);

        return packageRepository.save(draft);
    }

    @Transactional
    public void addClaimToPackage(Long packageId, Long claimId, Long adminUserId) {
        User admin = requireAdminById(adminUserId);

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException("Package not found."));

        if (packageEntity.getStatus() != PackageStatus.DRAFT) {
            throw new PackageConflictException("PACKAGE_NOT_DRAFT", "Package must be DRAFT to add claims.");
        }

        if (packageClaimRepository.existsByPackageEntityIdAndClaimId(packageId, claimId)) {
            throw new PackageConflictException("PACKAGE_CLAIM_DUPLICATE", "Claim is already added to this package.");
        }

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found."));

        if (claim.getStatus() == ClaimStatus.PACKAGED) {
            throw new PackageConflictException("CLAIM_ALREADY_PACKAGED", "Claim is already PACKAGED.");
        }
        if (claim.getStatus() != ClaimStatus.APPROVED) {
            throw new PackageValidationException("CLAIM_NOT_APPROVED", "Claim must be APPROVED before packaging.");
        }

        PackageClaim packageClaim = new PackageClaim();
        packageClaim.setPackageEntity(packageEntity);
        packageClaim.setClaim(claim);
        packageClaim.setIncludedReasonJson("{}");
        packageClaim.setAddedByUser(admin);

        try {
            packageClaimRepository.save(packageClaim);
        } catch (DataIntegrityViolationException ex) {
            throw new PackageConflictException("PACKAGE_CLAIM_DUPLICATE", "Claim is already added to this package.");
        }

        int nextTotalClaims = (packageEntity.getTotalClaims() == null ? 0 : packageEntity.getTotalClaims()) + 1;
        BigDecimal existingTotalFaceValue = packageEntity.getTotalFaceValue() == null
                ? BigDecimal.ZERO
                : packageEntity.getTotalFaceValue();
        BigDecimal claimFaceValue = resolveClaimFaceValue(claim);

        packageEntity.setTotalClaims(nextTotalClaims);
        packageEntity.setTotalFaceValue(existingTotalFaceValue.add(claimFaceValue));

        claim.setStatus(ClaimStatus.PACKAGED);

        packageRepository.save(packageEntity);
        claimRepository.save(claim);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", packageEntity.getId());
        metadata.put("claimId", claim.getId());

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "PACKAGE_CLAIM_ADDED",
                "PACKAGE",
                packageEntity.getId(),
                toJson(metadata)
        );
    }

    @Transactional(readOnly = true)
    public Package getPackage(Long packageId) {
        return packageRepository.findByIdWithClaims(packageId)
                .orElseThrow(() -> new PackageNotFoundException("Package not found."));
    }

    @Transactional(readOnly = true)
    public List<Package> listPackages(PackageStatus status) {
        if (status == null) {
            return packageRepository.findAllByOrderByCreatedAtDesc();
        }
        return packageRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    public PackageStatus parsePackageStatus(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return PackageStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new PackageValidationException("PACKAGE_STATUS_INVALID", "Invalid package status: " + value);
        }
    }

    private User requireAdminById(Long adminUserId) {
        if (adminUserId == null) {
            throw new PackageValidationException("ADMIN_REQUIRED", "Admin user is required.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new PackageValidationException("ADMIN_REQUIRED", "Admin user not found."));
        if (admin.getRole() != Role.ADMIN) {
            throw new PackageValidationException("ADMIN_REQUIRED", "Admin role required.");
        }
        return admin;
    }

    private BigDecimal resolveClaimFaceValue(Claim claim) {
        if (claim.getCurrentAmount() != null) {
            return claim.getCurrentAmount();
        }
        if (claim.getAmountOwed() != null) {
            return claim.getAmountOwed();
        }
        return BigDecimal.ZERO;
    }

    private String normalizeNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize package audit metadata.", ex);
        }
    }

    public static class PackageNotFoundException extends RuntimeException {
        public PackageNotFoundException(String message) {
            super(message);
        }
    }

    public static class ClaimNotFoundException extends RuntimeException {
        public ClaimNotFoundException(String message) {
            super(message);
        }
    }

    public static class PackageValidationException extends RuntimeException {
        private final String code;

        public PackageValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class PackageConflictException extends RuntimeException {
        private final String code;

        public PackageConflictException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
