package com.claimchain.backend.scoring;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.DisputeStatus;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.service.ClaimScoringPersistenceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ScoringEngine {

    private static final String GROUP_ENFORCEABILITY = "enforceability";
    private static final String GROUP_DOCUMENTATION = "documentation";
    private static final String GROUP_COLLECTABILITY = "collectability";
    private static final String GROUP_OPERATIONAL_RISK = "operationalrisk";

    private final ClaimRepository claimRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final ClaimScoreRepository claimScoreRepository;
    private final RulesetRepository rulesetRepository;
    private final ClaimScoringPersistenceService claimScoringPersistenceService;
    private final ObjectMapper objectMapper;

    public ScoringEngine(
            ClaimRepository claimRepository,
            ClaimDocumentRepository claimDocumentRepository,
            ClaimScoreRepository claimScoreRepository,
            RulesetRepository rulesetRepository,
            ClaimScoringPersistenceService claimScoringPersistenceService,
            ObjectMapper objectMapper
    ) {
        this.claimRepository = claimRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.claimScoreRepository = claimScoreRepository;
        this.rulesetRepository = rulesetRepository;
        this.claimScoringPersistenceService = claimScoringPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClaimScore scoreClaim(Long claimId, Long scoredByUserIdNullable, boolean isRescore) {
        ScoringTrigger defaultTrigger = isRescore ? ScoringTrigger.ADMIN_RESCORE : ScoringTrigger.APPROVAL;
        return scoreClaim(claimId, scoredByUserIdNullable, isRescore, defaultTrigger);
    }

    @Transactional
    public ClaimScore scoreClaim(
            Long claimId,
            Long scoredByUserIdNullable,
            boolean isRescore,
            ScoringTrigger trigger
    ) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));

        Ruleset activeRuleset = rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("No ACTIVE SCORING ruleset found."));

        ScoringTrigger effectiveTrigger = trigger == null ? ScoringTrigger.APPROVAL : trigger;
        ScoringRulesetConfig config = parseConfig(activeRuleset.getConfigJson());
        List<ClaimDocument> documents = claimDocumentRepository.findByClaimId(claim.getId());
        ScoreComputation computation = evaluate(claim, documents, config, isRescore, effectiveTrigger);

        return claimScoringPersistenceService.recordScoreRun(
                claim.getId(),
                activeRuleset.getId(),
                activeRuleset.getVersion(),
                computation.eligible(),
                computation.scoreTotal(),
                computation.grade(),
                computation.enforceabilitySubscore(),
                computation.documentationSubscore(),
                computation.collectabilitySubscore(),
                computation.operationalRiskSubscore(),
                computation.explainabilityJson(),
                computation.featureSnapshotJson(),
                scoredByUserIdNullable,
                effectiveTrigger
        );
    }

    public boolean autoScoreOnApprovalIfReady(Long claimId, Long scoredByUserIdNullable) {
        Optional<Ruleset> activeRulesetOpt = rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE);
        if (activeRulesetOpt.isEmpty()) {
            return false;
        }

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));
        if (!canAutoTriggerForStatus(claim.getStatus())) {
            return false;
        }

        scoreClaim(claim.getId(), scoredByUserIdNullable, false, ScoringTrigger.APPROVAL);
        return true;
    }

    public boolean autoScoreOnDocumentsReadyIfApproved(Long claimId) {
        Optional<Ruleset> activeRulesetOpt = rulesetRepository.findFirstByTypeAndStatus(RulesetType.SCORING, RulesetStatus.ACTIVE);
        if (activeRulesetOpt.isEmpty()) {
            return false;
        }

        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));

        if (!canAutoTriggerForStatus(claim.getStatus())) {
            return false;
        }

        ScoringRulesetConfig config = parseConfig(activeRulesetOpt.get().getConfigJson());
        List<ClaimDocument> documents = claimDocumentRepository.findByClaimId(claim.getId());
        if (!isReadyForAutoScoring(documents, config)) {
            return false;
        }

        scoreClaim(claim.getId(), null, false, ScoringTrigger.DOC_READY);
        return true;
    }

    private boolean isReadyForAutoScoring(List<ClaimDocument> documents, ScoringRulesetConfig config) {
        return true;
    }

    private ScoreComputation evaluate(
            Claim claim,
            List<ClaimDocument> documents,
            ScoringRulesetConfig config,
            boolean isRescore,
            ScoringTrigger trigger
    ) {
        DerivedMetrics metrics = buildMetrics(claim, documents);
        ScoringRulesetConfig.EligibilityConfig eligibility = config.getEligibility();

        List<String> eligibilityFailures = new ArrayList<>();

        if (!matchesRequiredClaimStatus(claim, eligibility)) {
            String required = eligibility == null ? null : eligibility.getRequiredClaimStatus();
            eligibilityFailures.add("Claim status must be " + required + ".");
        }

        Set<String> requiredDocTypes = normalizeStringSet(eligibility == null ? null : eligibility.getRequiredDocTypes());
        Set<String> missingDocTypes = new LinkedHashSet<>(requiredDocTypes);
        missingDocTypes.removeAll(metrics.presentDocTypes());

        boolean blockActiveDisputes = eligibility != null && Boolean.TRUE.equals(eligibility.getBlockActiveDisputes());
        if (blockActiveDisputes && claim.getDisputeStatus() == DisputeStatus.ACTIVE) {
            eligibilityFailures.add("Active dispute blocks eligibility.");
        }

        boolean eligible = eligibilityFailures.isEmpty();
        if (!eligible) {
            String explainabilityJson = buildExplainabilityJson(trigger, eligibilityFailures, List.of());
            String featureSnapshotJson = buildFeatureSnapshotJson(
                    claim,
                    metrics,
                    eligibility,
                    requiredDocTypes,
                    missingDocTypes,
                    isRescore
            );
            return new ScoreComputation(
                    false,
                    0,
                    "F",
                    0,
                    0,
                    0,
                    0,
                    explainabilityJson,
                    featureSnapshotJson
            );
        }

        int enforceability = 0;
        int documentation = 0;
        int collectability = 0;
        int operationalRisk = 0;

        List<ScoringContribution> contributions = new ArrayList<>();
        for (ScoringRulesetConfig.RuleConfig rule : config.getRules()) {
            if (rule == null) {
                continue;
            }

            Map<String, Object> fieldsUsed = new LinkedHashMap<>();
            if (!matchesRule(rule.getWhen(), claim, metrics, fieldsUsed)) {
                continue;
            }

            int points = rule.getPoints() == null ? 0 : rule.getPoints();
            String normalizedGroup = normalizeGroup(rule.getGroup());
            switch (normalizedGroup) {
                case GROUP_ENFORCEABILITY -> enforceability += points;
                case GROUP_DOCUMENTATION -> documentation += points;
                case GROUP_COLLECTABILITY -> collectability += points;
                case GROUP_OPERATIONAL_RISK -> operationalRisk += points;
                default -> {
                }
            }

            ScoringContribution contribution = new ScoringContribution();
            contribution.setRuleId(rule.getId());
            contribution.setGroup(rule.getGroup());
            contribution.setDelta(points);
            contribution.setReason(rule.getReason());
            contribution.setFieldsUsed(fieldsUsed);
            contributions.add(contribution);
        }

        ScoringRulesetConfig.CapsConfig caps = config.getCaps();
        enforceability = applyCap(enforceability, caps == null ? null : caps.getEnforceabilityMax());
        documentation = applyCap(documentation, caps == null ? null : caps.getDocumentationMax());
        collectability = applyCap(collectability, caps == null ? null : caps.getCollectabilityMax());
        operationalRisk = applyCap(operationalRisk, caps == null ? null : caps.getOperationalRiskMax());

        int total = Math.round(enforceability + documentation + collectability + operationalRisk);
        total = clamp(total, 0, 100);
        String grade = resolveGrade(total, config.getGradeBands());

        String explainabilityJson = buildExplainabilityJson(trigger, eligibilityFailures, contributions);
        String featureSnapshotJson = buildFeatureSnapshotJson(
                claim,
                metrics,
                eligibility,
                requiredDocTypes,
                missingDocTypes,
                isRescore
        );

        return new ScoreComputation(
                true,
                total,
                grade,
                enforceability,
                documentation,
                collectability,
                operationalRisk,
                explainabilityJson,
                featureSnapshotJson
        );
    }

    private ScoringRulesetConfig parseConfig(String configJson) {
        try {
            ScoringRulesetConfig config = objectMapper.readValue(configJson, ScoringRulesetConfig.class);
            if (config.getEligibility() == null) {
                config.setEligibility(new ScoringRulesetConfig.EligibilityConfig());
            }
            if (config.getRules() == null) {
                config.setRules(List.of());
            }
            if (config.getGradeBands() == null) {
                config.setGradeBands(List.of());
            }
            if (config.getCaps() == null) {
                config.setCaps(new ScoringRulesetConfig.CapsConfig());
            }
            return config;
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse active scoring ruleset config_json.", ex);
        }
    }

    private DerivedMetrics buildMetrics(Claim claim, List<ClaimDocument> documents) {
        List<ClaimDocument> safeDocs = documents == null ? List.of() : documents;
        int docCount = safeDocs.size();
        double extractionSuccessRate = computeExtractionSuccessRate(safeDocs);
        Set<String> presentDocTypes = collectPresentDocTypes(safeDocs);

        Long debtAgeDays = null;
        if (claim.getDateOfDefault() != null) {
            LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
            debtAgeDays = ChronoUnit.DAYS.between(claim.getDateOfDefault(), todayUtc);
        }

        BigDecimal currentAmount = claim.getCurrentAmount() != null ? claim.getCurrentAmount() : claim.getAmountOwed();
        boolean jurisdictionKnown = isJurisdictionKnown(claim.getJurisdictionState());

        return new DerivedMetrics(docCount, extractionSuccessRate, presentDocTypes, debtAgeDays, currentAmount, jurisdictionKnown);
    }

    private double computeExtractionSuccessRate(List<ClaimDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0d;
        }

        long successCount = documents.stream()
                .filter(doc -> doc.getExtractionStatus() == ExtractionStatus.SUCCEEDED)
                .count();
        return successCount / (double) documents.size();
    }

    private Set<String> collectPresentDocTypes(List<ClaimDocument> documents) {
        Set<String> present = new TreeSet<>();
        if (documents == null) {
            return present;
        }
        for (ClaimDocument document : documents) {
            if (document.getDocumentType() != null) {
                present.add(document.getDocumentType().name());
            }
        }
        return present;
    }

    private Set<String> normalizeStringSet(List<String> values) {
        Set<String> normalized = new TreeSet<>();
        if (values == null) {
            return normalized;
        }

        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private boolean matchesRequiredClaimStatus(Claim claim, ScoringRulesetConfig.EligibilityConfig eligibility) {
        if (eligibility == null || eligibility.getRequiredClaimStatus() == null || eligibility.getRequiredClaimStatus().isBlank()) {
            return true;
        }

        if (claim.getStatus() == null) {
            return false;
        }
        String required = eligibility.getRequiredClaimStatus().trim().toUpperCase(Locale.ROOT);
        return claim.getStatus().name().equals(required);
    }

    private boolean matchesRule(
            Map<String, Object> when,
            Claim claim,
            DerivedMetrics metrics,
            Map<String, Object> fieldsUsed
    ) {
        if (when == null || when.isEmpty()) {
            return false;
        }

        for (Map.Entry<String, Object> entry : when.entrySet()) {
            String key = entry.getKey();
            Object expected = entry.getValue();
            boolean matched = switch (key) {
                case "jurisdictionKnown" -> {
                    fieldsUsed.put("jurisdictionKnown", metrics.jurisdictionKnown());
                    fieldsUsed.put("jurisdictionState", claim.getJurisdictionState());
                    yield metrics.jurisdictionKnown() == toBoolean(expected);
                }
                case "debtAgeDaysLte" -> {
                    fieldsUsed.put("debtAgeDays", metrics.debtAgeDays());
                    Long actual = metrics.debtAgeDays();
                    Long limit = toLong(expected);
                    yield actual != null && limit != null && actual <= limit;
                }
                case "debtAgeDaysGte" -> {
                    fieldsUsed.put("debtAgeDays", metrics.debtAgeDays());
                    Long actual = metrics.debtAgeDays();
                    Long limit = toLong(expected);
                    yield actual != null && limit != null && actual >= limit;
                }
                case "debtAgeDaysBetween" -> {
                    fieldsUsed.put("debtAgeDays", metrics.debtAgeDays());
                    Long actual = metrics.debtAgeDays();
                    LongRange range = toLongRange(expected);
                    yield actual != null && range != null && actual >= range.min() && actual <= range.max();
                }
                case "extractionSuccessRateGte" -> {
                    fieldsUsed.put("extractionSuccessRate", metrics.extractionSuccessRate());
                    Double limit = toDouble(expected);
                    yield limit != null && metrics.extractionSuccessRate() >= limit;
                }
                case "extractionSuccessRateBetween" -> {
                    fieldsUsed.put("extractionSuccessRate", metrics.extractionSuccessRate());
                    DoubleRange range = toDoubleRange(expected);
                    yield range != null
                            && metrics.extractionSuccessRate() >= range.min()
                            && metrics.extractionSuccessRate() <= range.max();
                }
                case "extractionSuccessRateLt" -> {
                    fieldsUsed.put("extractionSuccessRate", metrics.extractionSuccessRate());
                    Double limit = toDouble(expected);
                    yield limit != null && metrics.extractionSuccessRate() < limit;
                }
                case "debtorTypeEquals" -> {
                    String actual = claim.getDebtorType() == null ? null : claim.getDebtorType().name();
                    fieldsUsed.put("debtorType", actual);
                    String expectedValue = toUpperTrimmedString(expected);
                    yield actual != null && expectedValue != null && actual.equals(expectedValue);
                }
                case "disputeStatusEquals" -> {
                    String actual = claim.getDisputeStatus() == null ? null : claim.getDisputeStatus().name();
                    fieldsUsed.put("disputeStatus", actual);
                    String expectedValue = toUpperTrimmedString(expected);
                    yield actual != null && expectedValue != null && actual.equals(expectedValue);
                }
                case "currentAmountBetween" -> {
                    BigDecimal actual = metrics.currentAmount();
                    fieldsUsed.put("currentAmount", actual);
                    BigDecimalRange range = toBigDecimalRange(expected);
                    yield actual != null
                            && range != null
                            && actual.compareTo(range.min()) >= 0
                            && actual.compareTo(range.max()) <= 0;
                }
                case "docCountGte" -> {
                    fieldsUsed.put("docCount", metrics.docCount());
                    Long limit = toLong(expected);
                    yield limit != null && metrics.docCount() >= limit;
                }
                default -> false;
            };

            if (!matched) {
                return false;
            }
        }

        return true;
    }

    private String normalizeGroup(String group) {
        if (group == null) {
            return "";
        }
        return group.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private int applyCap(int value, Integer cap) {
        if (cap == null) {
            return value;
        }
        return Math.min(value, cap);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String resolveGrade(int total, List<ScoringRulesetConfig.GradeBandConfig> gradeBands) {
        if (gradeBands == null) {
            return "F";
        }

        for (ScoringRulesetConfig.GradeBandConfig band : gradeBands) {
            if (band == null || band.getGrade() == null || band.getMinScore() == null) {
                continue;
            }
            if (total >= band.getMinScore()) {
                return band.getGrade();
            }
        }

        return "F";
    }

    private String buildExplainabilityJson(
            ScoringTrigger trigger,
            List<String> eligibilityFailures,
            List<ScoringContribution> contributions
    ) {
        Map<String, Object> explainability = new LinkedHashMap<>();
        explainability.put("trigger", trigger == null ? ScoringTrigger.APPROVAL.name() : trigger.name());
        explainability.put("eligibleReasons", eligibilityFailures);
        explainability.put("contributions", contributions);
        return toJson(explainability, "explainability");
    }

    private boolean canAutoTriggerForStatus(ClaimStatus status) {
        return status == ClaimStatus.APPROVED && !isFrozenStatus(status);
    }

    private boolean isFrozenStatus(ClaimStatus status) {
        return status == ClaimStatus.PACKAGED || status == ClaimStatus.LISTED || status == ClaimStatus.SOLD;
    }

    private String buildFeatureSnapshotJson(
            Claim claim,
            DerivedMetrics metrics,
            ScoringRulesetConfig.EligibilityConfig eligibility,
            Set<String> requiredDocTypes,
            Set<String> missingDocTypes,
            boolean isRescore
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("claimStatus", claim.getStatus() == null ? null : claim.getStatus().name());
        snapshot.put("requiredClaimStatus", eligibility == null ? null : eligibility.getRequiredClaimStatus());
        snapshot.put("requiredDocTypes", requiredDocTypes);
        snapshot.put("presentDocTypes", metrics.presentDocTypes());
        snapshot.put("missingRequiredDocTypes", missingDocTypes);
        snapshot.put("minExtractionSuccessRate", eligibility == null ? null : eligibility.getMinExtractionSuccessRate());
        snapshot.put("extractionSuccessRate", roundToFourDecimals(metrics.extractionSuccessRate()));
        snapshot.put("blockActiveDisputes", eligibility != null && Boolean.TRUE.equals(eligibility.getBlockActiveDisputes()));
        snapshot.put("disputeStatus", claim.getDisputeStatus() == null ? null : claim.getDisputeStatus().name());
        snapshot.put("debtorType", claim.getDebtorType() == null ? null : claim.getDebtorType().name());
        snapshot.put("jurisdictionState", claim.getJurisdictionState());
        snapshot.put("debtAgeDays", metrics.debtAgeDays());
        snapshot.put("currentAmount", metrics.currentAmount());
        snapshot.put("docCount", metrics.docCount());
        snapshot.put("isRescore", isRescore);
        return toJson(snapshot, "feature snapshot");
    }

    private double roundToFourDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }

    private String toJson(Object payload, String fieldName) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize " + fieldName + " JSON.", ex);
        }
    }

    private boolean isJurisdictionKnown(String jurisdictionState) {
        if (jurisdictionState == null) {
            return false;
        }
        String normalized = jurisdictionState.trim();
        return !normalized.isEmpty() && !"UNKNOWN".equalsIgnoreCase(normalized);
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean boolValue) {
            return boolValue;
        }
        if (value instanceof String stringValue) {
            return Boolean.parseBoolean(stringValue.trim());
        }
        return false;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.parseLong(stringValue.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Double.parseDouble(stringValue.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String toUpperTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private LongRange toLongRange(Object value) {
        if (!(value instanceof List<?> values) || values.size() != 2) {
            return null;
        }
        Long min = toLong(values.get(0));
        Long max = toLong(values.get(1));
        if (min == null || max == null) {
            return null;
        }
        return new LongRange(Math.min(min, max), Math.max(min, max));
    }

    private DoubleRange toDoubleRange(Object value) {
        if (!(value instanceof List<?> values) || values.size() != 2) {
            return null;
        }
        Double min = toDouble(values.get(0));
        Double max = toDouble(values.get(1));
        if (min == null || max == null) {
            return null;
        }
        return new DoubleRange(Math.min(min, max), Math.max(min, max));
    }

    private BigDecimalRange toBigDecimalRange(Object value) {
        if (!(value instanceof List<?> values) || values.size() != 2) {
            return null;
        }
        BigDecimal min = toBigDecimal(values.get(0));
        BigDecimal max = toBigDecimal(values.get(1));
        if (min == null || max == null) {
            return null;
        }
        if (min.compareTo(max) <= 0) {
            return new BigDecimalRange(min, max);
        }
        return new BigDecimalRange(max, min);
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String stringValue) {
            try {
                return new BigDecimal(stringValue.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private record LongRange(long min, long max) {
    }

    private record DoubleRange(double min, double max) {
    }

    private record BigDecimalRange(BigDecimal min, BigDecimal max) {
    }

    private record DerivedMetrics(
            int docCount,
            double extractionSuccessRate,
            Set<String> presentDocTypes,
            Long debtAgeDays,
            BigDecimal currentAmount,
            boolean jurisdictionKnown
    ) {
    }

    private record ScoreComputation(
            boolean eligible,
            int scoreTotal,
            String grade,
            int enforceabilitySubscore,
            int documentationSubscore,
            int collectabilitySubscore,
            int operationalRiskSubscore,
            String explainabilityJson,
            String featureSnapshotJson
    ) {
    }
}
