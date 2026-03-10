package com.claimchain.backend.service;

import com.claimchain.backend.model.AnonymizedClaimView;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageClaim;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.AnonymizedClaimViewRepository;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AnonymizedViewService {

    private static final String UNKNOWN = "UNKNOWN";

    private final PackageRepository packageRepository;
    private final AnonymizedClaimViewRepository anonymizedClaimViewRepository;
    private final ClaimScoreRepository claimScoreRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AnonymizedViewService(
            PackageRepository packageRepository,
            AnonymizedClaimViewRepository anonymizedClaimViewRepository,
            ClaimScoreRepository claimScoreRepository,
            ClaimDocumentRepository claimDocumentRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packageRepository = packageRepository;
        this.anonymizedClaimViewRepository = anonymizedClaimViewRepository;
        this.claimScoreRepository = claimScoreRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void generateForPackage(Long packageId, Long adminUserId) {
        User admin = requireAdminById(adminUserId);
        Package packageEntity = requirePackageWithClaims(packageId);

        int generatedCount = 0;
        for (PackageClaim packageClaim : packageEntity.getPackageClaims()) {
            if (packageClaim == null || packageClaim.getClaim() == null || packageClaim.getClaim().getId() == null) {
                continue;
            }
            Claim claim = packageClaim.getClaim();
            long claimId = claim.getId();

            List<ClaimDocument> documents = claimDocumentRepository.findByClaimId(claimId);
            ClaimScore score = claimScoreRepository.findFirstByClaimIdOrderByScoredAtDescIdDesc(claimId).orElse(null);

            AnonymizedClaimView view = anonymizedClaimViewRepository
                    .findByPackageEntityIdAndClaimId(packageEntity.getId(), claimId)
                    .orElseGet(AnonymizedClaimView::new);

            if (view.getId() == null) {
                view.setPackageEntity(packageEntity);
                view.setClaim(claim);
            }

            view.setJurisdictionState(normalizeString(claim.getJurisdictionState()));
            view.setDebtorType(claim.getDebtorType() == null ? UNKNOWN : claim.getDebtorType().name());
            view.setClaimType(claim.getClaimType() == null ? UNKNOWN : claim.getClaimType().name());
            view.setDisputeStatus(claim.getDisputeStatus() == null ? UNKNOWN : claim.getDisputeStatus().name());
            view.setDebtAgeDays(computeDebtAgeDays(claim.getDateOfDefault()));

            BigDecimal amount = resolveFaceValue(claim);
            view.setAmountBand(toAmountBand(amount));

            view.setScoreTotal(score == null || score.getScoreTotal() == null ? 0 : score.getScoreTotal());
            view.setGrade(normalizeGrade(score == null ? null : score.getGrade()));

            view.setExtractionSuccessRate(computeExtractionSuccessRate(documents));
            view.setDocTypesPresent(toDocTypesJson(documents));

            anonymizedClaimViewRepository.save(view);
            generatedCount++;
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", packageEntity.getId());
        metadata.put("count", generatedCount);

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "ANON_VIEW_GENERATED",
                "PACKAGE",
                packageEntity.getId(),
                toJson(metadata)
        );
    }

    @Transactional(readOnly = true)
    public List<AnonymizedClaimView> listByPackage(Long packageId, Long adminUserId) {
        requireAdminById(adminUserId);
        if (packageId == null || !packageRepository.existsById(packageId)) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }
        return anonymizedClaimViewRepository.findByPackageIdOrderByScoreTotalDesc(packageId);
    }

    private User requireAdminById(Long adminUserId) {
        if (adminUserId == null) {
            throw new PackageService.PackageValidationException("ADMIN_REQUIRED", "Admin user is required.");
        }
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new PackageService.PackageValidationException("ADMIN_REQUIRED", "Admin user not found."));
        if (admin.getRole() != Role.ADMIN) {
            throw new PackageService.PackageValidationException("ADMIN_REQUIRED", "Admin role required.");
        }
        return admin;
    }

    private Package requirePackageWithClaims(Long packageId) {
        if (packageId == null) {
            throw new PackageService.PackageValidationException("PACKAGE_ID_REQUIRED", "Package id is required.");
        }
        return packageRepository.findByIdWithClaims(packageId)
                .orElseThrow(() -> new PackageService.PackageNotFoundException("Package not found."));
    }

    private String normalizeString(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? UNKNOWN : normalized;
    }

    private int computeDebtAgeDays(LocalDate dateOfDefault) {
        if (dateOfDefault == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(dateOfDefault, LocalDate.now(ZoneOffset.UTC));
        if (days <= 0) {
            return 0;
        }
        return days > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) days;
    }

    private BigDecimal resolveFaceValue(Claim claim) {
        if (claim.getCurrentAmount() != null) {
            return claim.getCurrentAmount();
        }
        if (claim.getAmountOwed() != null) {
            return claim.getAmountOwed();
        }
        return BigDecimal.ZERO;
    }

    private String toAmountBand(BigDecimal amount) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount;
        if (normalized.compareTo(BigDecimal.valueOf(100)) < 0) {
            return "<100";
        }
        if (normalized.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return "100-999";
        }
        if (normalized.compareTo(BigDecimal.valueOf(5000)) < 0) {
            return "1000-4999";
        }
        if (normalized.compareTo(BigDecimal.valueOf(25000)) < 0) {
            return "5000-24999";
        }
        return "25000+";
    }

    private String normalizeGrade(String grade) {
        if (grade == null) {
            return "UNSCORED";
        }
        String normalized = grade.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? "UNSCORED" : normalized;
    }

    private double computeExtractionSuccessRate(List<ClaimDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0.0d;
        }
        long succeeded = documents.stream()
                .filter(Objects::nonNull)
                .filter(document -> document.getExtractionStatus() == ExtractionStatus.SUCCEEDED)
                .count();
        return ((double) succeeded) / documents.size();
    }

    private String toDocTypesJson(List<ClaimDocument> documents) {
        List<String> docTypes = documents == null
                ? List.of()
                : documents.stream()
                .filter(Objects::nonNull)
                .map(ClaimDocument::getDocumentType)
                .filter(Objects::nonNull)
                .map(DocumentType::name)
                .distinct()
                .sorted()
                .toList();
        return toJson(docTypes);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize anonymized view data.", ex);
        }
    }
}
