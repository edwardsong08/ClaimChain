package com.claimchain.backend.service;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.DisputeStatus;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageClaim;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.packaging.PackagingRulesetConfig;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.PackageClaimRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PackageService {

    private static final double EPSILON = 1e-9d;
    private static final String DEFAULT_BUCKET = "UNKNOWN";
    private static final Set<String> PRIMARY_PROOF_DOC_TYPES = Set.of("INVOICE", "CONTRACT");

    private final PackageRepository packageRepository;
    private final PackageClaimRepository packageClaimRepository;
    private final ClaimRepository claimRepository;
    private final ClaimScoreRepository claimScoreRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final RulesetRepository rulesetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PackageService(
            PackageRepository packageRepository,
            PackageClaimRepository packageClaimRepository,
            ClaimRepository claimRepository,
            ClaimScoreRepository claimScoreRepository,
            ClaimDocumentRepository claimDocumentRepository,
            RulesetRepository rulesetRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.packageRepository = packageRepository;
        this.packageClaimRepository = packageClaimRepository;
        this.claimRepository = claimRepository;
        this.claimScoreRepository = claimScoreRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.rulesetRepository = rulesetRepository;
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

    @Transactional
    public void listPackage(Long packageId, Long adminUserId) {
        transitionPackageStatus(
                packageId,
                adminUserId,
                PackageStatus.READY,
                PackageStatus.LISTED,
                "PACKAGE_LISTED"
        );
    }

    @Transactional
    public void unlistPackage(Long packageId, Long adminUserId) {
        transitionPackageStatus(
                packageId,
                adminUserId,
                PackageStatus.LISTED,
                PackageStatus.READY,
                "PACKAGE_UNLISTED"
        );
    }

    @Transactional
    public BuildPackageResult buildOnePackage(Long adminUserId, String notesOptional, boolean dryRun) {
        User admin = requireAdminById(adminUserId);
        Ruleset activePackagingRuleset = requireActivePackagingRuleset();
        PackagingRulesetConfig config = parsePackagingRuleset(activePackagingRuleset.getConfigJson());
        PackagingRuleContext context = normalizeAndValidateContext(config);

        List<CandidateClaim> eligibleCandidates = loadEligibleCandidates(context);
        SelectionOutcome selectionOutcome = selectCandidates(eligibleCandidates, context);
        List<SelectedCandidate> selectedCandidates = selectionOutcome.selectedCandidates;
        BigDecimal totalFaceValue = selectionOutcome.totalFaceValue;
        boolean buildable = selectionOutcome.failureReasons.isEmpty();
        List<Long> selectedClaimIds = selectedCandidates.stream()
                .map(selected -> selected.candidate.claim.getId())
                .toList();

        if (dryRun) {
            BuildPackageResult preview = new BuildPackageResult();
            preview.setDryRun(true);
            preview.setStatus(PackageStatus.READY.name());
            preview.setRulesetId(activePackagingRuleset.getId());
            preview.setRulesetVersion(activePackagingRuleset.getVersion());
            preview.setTotalClaims(selectedCandidates.size());
            preview.setTotalFaceValue(totalFaceValue);
            preview.setClaimIds(selectedClaimIds);
            preview.setBuildable(buildable);
            if (!buildable) {
                preview.setFailureReasons(selectionOutcome.failureReasons);
            }
            return preview;
        }

        if (!buildable) {
            throw new PackageConflictException(
                    "PACKAGE_BUILD_FAILED",
                    "Unable to build package.",
                    selectionOutcome.failureReasons
            );
        }

        Package packageEntity = new Package();
        packageEntity.setStatus(PackageStatus.READY);
        packageEntity.setCreatedByUser(admin);
        packageEntity.setRuleset(activePackagingRuleset);
        packageEntity.setRulesetVersion(activePackagingRuleset.getVersion());
        packageEntity.setNotes(normalizeNotes(notesOptional));
        packageEntity.setTotalClaims(selectedCandidates.size());
        packageEntity.setTotalFaceValue(totalFaceValue);
        Package savedPackage = packageRepository.save(packageEntity);

        for (SelectedCandidate selected : selectedCandidates) {
            Claim claim = selected.candidate.claim;
            claim.setStatus(ClaimStatus.PACKAGED);
            claimRepository.save(claim);

            PackageClaim packageClaim = new PackageClaim();
            packageClaim.setPackageEntity(savedPackage);
            packageClaim.setClaim(claim);
            packageClaim.setAddedByUser(admin);
            packageClaim.setIncludedReasonJson(includedReasonJson(selected, context, activePackagingRuleset));
            packageClaimRepository.save(packageClaim);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", savedPackage.getId());
        metadata.put("rulesetId", activePackagingRuleset.getId());
        metadata.put("rulesetVersion", activePackagingRuleset.getVersion());
        metadata.put("totalClaims", selectedCandidates.size());
        metadata.put("totalFaceValue", totalFaceValue);
        metadata.put("selectionMode", context.selectionMode);
        metadata.put("maxPctPerJurisdiction", context.maxPctPerJurisdiction);
        metadata.put("maxPctPerDebtorType", context.maxPctPerDebtorType);
        metadata.put("minClaims", context.minClaims);
        metadata.put("maxClaims", context.maxClaims);
        metadata.put("minTotalFaceValue", context.minTotalFaceValue);
        metadata.put("maxTotalFaceValue", context.maxTotalFaceValue);

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "PACKAGE_CREATED",
                "PACKAGE",
                savedPackage.getId(),
                toJson(metadata)
        );

        BuildPackageResult created = new BuildPackageResult();
        created.setPackageId(savedPackage.getId());
        created.setDryRun(false);
        created.setStatus(savedPackage.getStatus() == null ? null : savedPackage.getStatus().name());
        created.setRulesetId(activePackagingRuleset.getId());
        created.setRulesetVersion(activePackagingRuleset.getVersion());
        created.setTotalClaims(savedPackage.getTotalClaims());
        created.setTotalFaceValue(savedPackage.getTotalFaceValue());
        created.setClaimIds(selectedClaimIds);
        created.setBuildable(true);
        return created;
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

    private void transitionPackageStatus(
            Long packageId,
            Long adminUserId,
            PackageStatus expectedFrom,
            PackageStatus toStatus,
            String auditAction
    ) {
        User admin = requireAdminById(adminUserId);
        if (packageId == null) {
            throw new PackageValidationException("PACKAGE_ID_REQUIRED", "Package id is required.");
        }

        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageNotFoundException("Package not found."));

        PackageStatus currentStatus = packageEntity.getStatus();
        if (currentStatus != expectedFrom) {
            throw new PackageConflictException(
                    "PACKAGE_STATUS_INVALID",
                    "Package status transition is invalid.",
                    List.of(
                            "currentStatus=" + (currentStatus == null ? "UNKNOWN" : currentStatus.name()),
                            "requiredStatus=" + expectedFrom.name(),
                            "targetStatus=" + toStatus.name()
                    )
            );
        }

        packageEntity.setStatus(toStatus);
        packageRepository.save(packageEntity);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageId", packageEntity.getId());
        metadata.put("fromStatus", currentStatus == null ? null : currentStatus.name());
        metadata.put("toStatus", toStatus.name());

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                auditAction,
                "PACKAGE",
                packageEntity.getId(),
                toJson(metadata)
        );
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

    private Ruleset requireActivePackagingRuleset() {
        return rulesetRepository.findFirstByTypeAndStatus(RulesetType.PACKAGING, RulesetStatus.ACTIVE)
                .orElseThrow(() -> new PackageConflictException(
                        "PACKAGE_BUILD_FAILED",
                        "Unable to build package.",
                        List.of("No ACTIVE PACKAGING ruleset found.")
                ));
    }

    private PackagingRulesetConfig parsePackagingRuleset(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "Active packaging ruleset config is blank.");
        }
        try {
            return objectMapper.readValue(configJson, PackagingRulesetConfig.class);
        } catch (JsonProcessingException ex) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "Active packaging ruleset config is invalid JSON.");
        }
    }

    private PackagingRuleContext normalizeAndValidateContext(PackagingRulesetConfig config) {
        if (config == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "Active packaging ruleset config is missing.");
        }
        if (config.getEligibility() == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility object is required.");
        }
        if (config.getPackageSizing() == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing object is required.");
        }
        if (config.getDiversification() == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "diversification object is required.");
        }
        if (config.getSelectionStrategy() == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "selectionStrategy object is required.");
        }

        String mode = normalizeEnumName(config.getSelectionStrategy().getMode());
        if (!"BEST_FIRST".equals(mode)) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "selectionStrategy.mode must be BEST_FIRST in v1.");
        }

        Integer minClaims = config.getPackageSizing().getMinClaims();
        Integer maxClaims = config.getPackageSizing().getMaxClaims();
        if (minClaims == null || minClaims <= 0) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.minClaims must be positive.");
        }
        if (maxClaims == null || maxClaims <= 0) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.maxClaims must be positive.");
        }
        if (minClaims > maxClaims) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.minClaims must be <= packageSizing.maxClaims.");
        }

        BigDecimal minTotalFaceValue = config.getPackageSizing().getMinTotalFaceValue();
        BigDecimal maxTotalFaceValue = config.getPackageSizing().getMaxTotalFaceValue();
        if (minTotalFaceValue != null && minTotalFaceValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.minTotalFaceValue must be >= 0.");
        }
        if (maxTotalFaceValue != null && maxTotalFaceValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.maxTotalFaceValue must be > 0.");
        }
        if (minTotalFaceValue != null && maxTotalFaceValue != null && minTotalFaceValue.compareTo(maxTotalFaceValue) > 0) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "packageSizing.minTotalFaceValue must be <= packageSizing.maxTotalFaceValue.");
        }

        double maxPctPerJurisdiction = requirePercent(config.getDiversification().getMaxPctPerJurisdiction(), "diversification.maxPctPerJurisdiction");
        double maxPctPerDebtorType = requirePercent(config.getDiversification().getMaxPctPerDebtorType(), "diversification.maxPctPerDebtorType");

        Integer minScore = config.getEligibility().getMinScore();
        if (minScore != null && (minScore < 0 || minScore > 100)) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility.minScore must be between 0 and 100.");
        }

        String minGrade = normalizeEnumName(config.getEligibility().getMinGrade());
        Integer minGradeRank = minGrade == null ? null : gradeRank(minGrade);
        if (minGrade != null && minGradeRank == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility.minGrade must be one of A, B, C, D, F.");
        }

        Double minExtractionSuccessRate = config.getEligibility().getMinExtractionSuccessRate();
        if (minExtractionSuccessRate == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility.minExtractionSuccessRate is required.");
        }
        if (minExtractionSuccessRate < 0.0d || minExtractionSuccessRate > 1.0d) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility.minExtractionSuccessRate must be between 0 and 1.");
        }

        Set<String> requiredDocTypes = new HashSet<>();
        if (config.getEligibility().getRequiredDocTypes() != null) {
            for (String requiredDocType : config.getEligibility().getRequiredDocTypes()) {
                String normalized = normalizeEnumName(requiredDocType);
                if (normalized == null) {
                    continue;
                }
                requiredDocTypes.add(normalized);
            }
        }

        Set<String> excludedDisputeStatuses = new HashSet<>();
        if (config.getEligibility().getExcludeDisputeStatuses() != null) {
            for (String excludedStatus : config.getEligibility().getExcludeDisputeStatuses()) {
                String normalized = normalizeEnumName(excludedStatus);
                if (normalized == null) {
                    continue;
                }
                if (!isValidDisputeStatus(normalized)) {
                    throw new PackageValidationException("PACKAGE_RULESET_INVALID", "eligibility.excludeDisputeStatuses contains invalid status: " + excludedStatus);
                }
                excludedDisputeStatuses.add(normalized);
            }
        }

        PackagingRuleContext context = new PackagingRuleContext();
        context.minScore = minScore;
        context.minGrade = minGrade;
        context.minGradeRank = minGradeRank;
        context.requiredDocTypes = requiredDocTypes;
        context.minExtractionSuccessRate = minExtractionSuccessRate;
        context.excludedDisputeStatuses = excludedDisputeStatuses;
        context.minClaims = minClaims;
        context.maxClaims = maxClaims;
        context.minTotalFaceValue = minTotalFaceValue;
        context.maxTotalFaceValue = maxTotalFaceValue;
        context.maxPctPerJurisdiction = maxPctPerJurisdiction;
        context.maxPctPerDebtorType = maxPctPerDebtorType;
        context.selectionMode = mode;
        return context;
    }

    private List<CandidateClaim> loadEligibleCandidates(PackagingRuleContext context) {
        List<CandidateClaim> candidates = new ArrayList<>();
        List<Claim> approvedClaims = claimRepository.findByStatusOrderBySubmittedAtDesc(ClaimStatus.APPROVED);

        for (Claim claim : approvedClaims) {
            if (claim.getStatus() == ClaimStatus.PACKAGED) {
                continue;
            }

            ClaimScore currentScore = claimScoreRepository.findFirstByClaimIdOrderByScoredAtDesc(claim.getId()).orElse(null);
            if (currentScore == null || currentScore.getScoreTotal() == null) {
                continue;
            }
            if (context.minScore != null && currentScore.getScoreTotal() < context.minScore) {
                continue;
            }
            if (context.minGradeRank != null && gradeRankOrZero(currentScore.getGrade()) < context.minGradeRank) {
                continue;
            }

            List<ClaimDocument> documents = claimDocumentRepository.findByClaimId(claim.getId());
            Set<String> availableDocTypes = new HashSet<>();
            int extractionSucceededCount = 0;
            for (ClaimDocument document : documents) {
                if (document.getDocumentType() != null) {
                    availableDocTypes.add(document.getDocumentType().name());
                }
                if (document.getExtractionStatus() == ExtractionStatus.SUCCEEDED) {
                    extractionSucceededCount++;
                }
            }
            boolean requiredDocsPresent = hasRequiredPackagingDocuments(availableDocTypes, context.requiredDocTypes);
            if (!requiredDocsPresent) {
                continue;
            }

            int totalDocs = documents.size();
            double extractionSuccessRate = totalDocs == 0 ? 0.0d : ((double) extractionSucceededCount) / totalDocs;
            if (extractionSuccessRate + EPSILON < context.minExtractionSuccessRate) {
                continue;
            }

            String disputeStatus = claim.getDisputeStatus() == null ? null : claim.getDisputeStatus().name();
            if (disputeStatus != null && context.excludedDisputeStatuses.contains(disputeStatus)) {
                continue;
            }

            CandidateClaim candidate = new CandidateClaim();
            candidate.claim = claim;
            candidate.scoreTotal = currentScore.getScoreTotal();
            candidate.grade = currentScore.getGrade();
            candidate.faceValue = resolveClaimFaceValue(claim);
            candidate.extractionSuccessRate = extractionSuccessRate;
            candidate.requiredDocTypesPresent = requiredDocsPresent;
            candidate.jurisdictionBucket = normalizeBucket(claim.getJurisdictionState());
            candidate.debtorTypeBucket = claim.getDebtorType() == null ? DEFAULT_BUCKET : claim.getDebtorType().name();
            candidates.add(candidate);
        }

        candidates.sort(
                Comparator.comparing((CandidateClaim c) -> c.scoreTotal, Comparator.reverseOrder())
                        .thenComparing(c -> c.faceValue, Comparator.reverseOrder())
                        .thenComparing(c -> c.claim.getSubmittedAt(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(c -> c.claim.getId(), Comparator.nullsLast(Comparator.naturalOrder()))
        );
        return candidates;
    }

    private boolean hasRequiredPackagingDocuments(Set<String> availableDocTypes, Set<String> requiredDocTypes) {
        if (!hasPrimaryProofDocument(availableDocTypes)) {
            return false;
        }
        if (requiredDocTypes == null || requiredDocTypes.isEmpty()) {
            return true;
        }
        Set<String> additionalRequiredDocTypes = new HashSet<>(requiredDocTypes);
        additionalRequiredDocTypes.removeAll(PRIMARY_PROOF_DOC_TYPES);
        return availableDocTypes.containsAll(additionalRequiredDocTypes);
    }

    private boolean hasPrimaryProofDocument(Set<String> availableDocTypes) {
        for (String primaryDocType : PRIMARY_PROOF_DOC_TYPES) {
            if (availableDocTypes.contains(primaryDocType)) {
                return true;
            }
        }
        return false;
    }

    private SelectionOutcome selectCandidates(List<CandidateClaim> candidates, PackagingRuleContext context) {
        Map<String, Integer> jurisdictionCounts = new HashMap<>();
        Map<String, Integer> debtorTypeCounts = new HashMap<>();
        List<SelectedCandidate> selected = new ArrayList<>();
        BigDecimal runningFaceValue = BigDecimal.ZERO;

        for (CandidateClaim candidate : candidates) {
            if (selected.size() >= context.maxClaims) {
                break;
            }
            if (context.maxTotalFaceValue != null && runningFaceValue.add(candidate.faceValue).compareTo(context.maxTotalFaceValue) > 0) {
                continue;
            }
            if (wouldViolateCap(candidate.jurisdictionBucket, jurisdictionCounts, selected.size(), context.maxPctPerJurisdiction)) {
                continue;
            }
            if (wouldViolateCap(candidate.debtorTypeBucket, debtorTypeCounts, selected.size(), context.maxPctPerDebtorType)) {
                continue;
            }

            int jurisdictionCountAfter = incrementCount(jurisdictionCounts, candidate.jurisdictionBucket);
            int debtorTypeCountAfter = incrementCount(debtorTypeCounts, candidate.debtorTypeBucket);
            int totalClaimsAfter = selected.size() + 1;
            runningFaceValue = runningFaceValue.add(candidate.faceValue);

            SelectedCandidate selectedCandidate = new SelectedCandidate();
            selectedCandidate.candidate = candidate;
            selectedCandidate.jurisdictionCountAfter = jurisdictionCountAfter;
            selectedCandidate.debtorTypeCountAfter = debtorTypeCountAfter;
            selectedCandidate.totalClaimsAfter = totalClaimsAfter;
            selected.add(selectedCandidate);

            if (selected.size() >= context.maxClaims) {
                break;
            }
            if (context.maxTotalFaceValue != null && runningFaceValue.compareTo(context.maxTotalFaceValue) >= 0) {
                break;
            }
        }

        List<String> failures = new ArrayList<>();
        if (selected.size() < context.minClaims) {
            failures.add("minClaims not met: required " + context.minClaims + ", selected " + selected.size());
        }

        BigDecimal selectedTotalFaceValue = runningFaceValue;
        if (context.minTotalFaceValue != null && selectedTotalFaceValue.compareTo(context.minTotalFaceValue) < 0) {
            failures.add("minTotalFaceValue not met: required " + context.minTotalFaceValue + ", selected " + selectedTotalFaceValue);
        }

        SelectionOutcome outcome = new SelectionOutcome();
        outcome.selectedCandidates = selected;
        outcome.totalFaceValue = selectedTotalFaceValue;
        outcome.failureReasons = failures.isEmpty() ? List.of() : List.copyOf(failures);
        return outcome;
    }

    private String includedReasonJson(
            SelectedCandidate selected,
            PackagingRuleContext context,
            Ruleset activePackagingRuleset
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scoreTotal", selected.candidate.scoreTotal);
        payload.put("grade", selected.candidate.grade);
        payload.put("packagingRulesetId", activePackagingRuleset.getId());
        payload.put("packagingRulesetVersion", activePackagingRuleset.getVersion());

        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("minScore", context.minScore);
        thresholds.put("minGrade", context.minGrade);
        thresholds.put("requiredDocTypes", context.requiredDocTypes);
        thresholds.put("minExtractionSuccessRate", context.minExtractionSuccessRate);
        payload.put("eligibilityThresholds", thresholds);

        payload.put("extractionSuccessRate", selected.candidate.extractionSuccessRate);
        payload.put("requiredDocTypesPresent", selected.candidate.requiredDocTypesPresent);

        Map<String, Object> diversification = new LinkedHashMap<>();
        diversification.put("jurisdiction", selected.candidate.jurisdictionBucket);
        diversification.put("jurisdictionCountAfter", selected.jurisdictionCountAfter);
        diversification.put("debtorType", selected.candidate.debtorTypeBucket);
        diversification.put("debtorTypeCountAfter", selected.debtorTypeCountAfter);
        diversification.put("totalClaimsAfter", selected.totalClaimsAfter);
        diversification.put("maxPctPerJurisdiction", context.maxPctPerJurisdiction);
        diversification.put("maxPctPerDebtorType", context.maxPctPerDebtorType);
        payload.put("diversification", diversification);

        return toJson(payload);
    }

    private boolean wouldViolateCap(String bucket, Map<String, Integer> counts, int currentSelectedCount, double maxPct) {
        if (currentSelectedCount == 0) {
            return false;
        }
        int newTotal = currentSelectedCount + 1;
        int newBucketCount = counts.getOrDefault(bucket, 0) + 1;
        double ratio = ((double) newBucketCount) / newTotal;
        return ratio > maxPct + EPSILON;
    }

    private int incrementCount(Map<String, Integer> counts, String bucket) {
        int next = counts.getOrDefault(bucket, 0) + 1;
        counts.put(bucket, next);
        return next;
    }

    private String normalizeBucket(String value) {
        if (value == null) {
            return DEFAULT_BUCKET;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? DEFAULT_BUCKET : normalized;
    }

    private double requirePercent(Double value, String fieldName) {
        if (value == null) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", fieldName + " is required.");
        }
        if (value <= 0.0d || value > 1.0d) {
            throw new PackageValidationException("PACKAGE_RULESET_INVALID", fieldName + " must be within (0,1].");
        }
        return value;
    }

    private String normalizeEnumName(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isValidDisputeStatus(String value) {
        try {
            DisputeStatus.valueOf(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private Integer gradeRank(String grade) {
        return switch (grade) {
            case "A" -> 5;
            case "B" -> 4;
            case "C" -> 3;
            case "D" -> 2;
            case "F" -> 1;
            default -> null;
        };
    }

    private int gradeRankOrZero(String grade) {
        String normalized = normalizeEnumName(grade);
        Integer rank = normalized == null ? null : gradeRank(normalized);
        return rank == null ? 0 : rank;
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
        private final List<String> details;

        public PackageConflictException(String code, String message) {
            super(message);
            this.code = code;
            this.details = null;
        }

        public PackageConflictException(String code, String message, List<String> details) {
            super(message);
            this.code = code;
            this.details = details == null ? null : List.copyOf(details);
        }

        public String getCode() {
            return code;
        }

        public List<String> getDetails() {
            return details;
        }
    }

    public static class BuildPackageResult {
        private Long packageId;
        private boolean dryRun;
        private String status;
        private Long rulesetId;
        private Integer rulesetVersion;
        private Integer totalClaims;
        private BigDecimal totalFaceValue;
        private List<Long> claimIds;
        private boolean buildable;
        private List<String> failureReasons;

        public Long getPackageId() {
            return packageId;
        }

        public void setPackageId(Long packageId) {
            this.packageId = packageId;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Long getRulesetId() {
            return rulesetId;
        }

        public void setRulesetId(Long rulesetId) {
            this.rulesetId = rulesetId;
        }

        public Integer getRulesetVersion() {
            return rulesetVersion;
        }

        public void setRulesetVersion(Integer rulesetVersion) {
            this.rulesetVersion = rulesetVersion;
        }

        public Integer getTotalClaims() {
            return totalClaims;
        }

        public void setTotalClaims(Integer totalClaims) {
            this.totalClaims = totalClaims;
        }

        public BigDecimal getTotalFaceValue() {
            return totalFaceValue;
        }

        public void setTotalFaceValue(BigDecimal totalFaceValue) {
            this.totalFaceValue = totalFaceValue;
        }

        public List<Long> getClaimIds() {
            return claimIds;
        }

        public void setClaimIds(List<Long> claimIds) {
            this.claimIds = claimIds;
        }

        public boolean isBuildable() {
            return buildable;
        }

        public void setBuildable(boolean buildable) {
            this.buildable = buildable;
        }

        public List<String> getFailureReasons() {
            return failureReasons;
        }

        public void setFailureReasons(List<String> failureReasons) {
            this.failureReasons = failureReasons;
        }
    }

    private static class PackagingRuleContext {
        private Integer minScore;
        private String minGrade;
        private Integer minGradeRank;
        private Set<String> requiredDocTypes;
        private Double minExtractionSuccessRate;
        private Set<String> excludedDisputeStatuses;
        private Integer minClaims;
        private Integer maxClaims;
        private BigDecimal minTotalFaceValue;
        private BigDecimal maxTotalFaceValue;
        private double maxPctPerJurisdiction;
        private double maxPctPerDebtorType;
        private String selectionMode;
    }

    private static class CandidateClaim {
        private Claim claim;
        private Integer scoreTotal;
        private String grade;
        private BigDecimal faceValue;
        private double extractionSuccessRate;
        private boolean requiredDocTypesPresent;
        private String jurisdictionBucket;
        private String debtorTypeBucket;
    }

    private static class SelectedCandidate {
        private CandidateClaim candidate;
        private int jurisdictionCountAfter;
        private int debtorTypeCountAfter;
        private int totalClaimsAfter;
    }

    private static class SelectionOutcome {
        private List<SelectedCandidate> selectedCandidates;
        private BigDecimal totalFaceValue;
        private List<String> failureReasons;
    }
}
