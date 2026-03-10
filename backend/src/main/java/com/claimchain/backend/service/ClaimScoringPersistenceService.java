package com.claimchain.backend.service;

import com.claimchain.backend.dto.ClaimScoreResponseDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.scoring.ScoringTrigger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ClaimScoringPersistenceService {

    private final ClaimScoreRepository claimScoreRepository;
    private final ClaimRepository claimRepository;
    private final RulesetRepository rulesetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ClaimScoringPersistenceService(
            ClaimScoreRepository claimScoreRepository,
            ClaimRepository claimRepository,
            RulesetRepository rulesetRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.claimScoreRepository = claimScoreRepository;
        this.claimRepository = claimRepository;
        this.rulesetRepository = rulesetRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ClaimScore recordScoreRun(
            Long claimId,
            Long rulesetId,
            Integer rulesetVersion,
            boolean eligible,
            Integer scoreTotal,
            String grade,
            Integer subscoreEnforceability,
            Integer subscoreDocumentation,
            Integer subscoreCollectability,
            Integer subscoreOperationalRisk,
            String explainabilityJson,
            String featureSnapshotJson,
            Long scoredByUserId,
            ScoringTrigger trigger
    ) {
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));
        Ruleset ruleset = rulesetRepository.findById(rulesetId)
                .orElseThrow(() -> new IllegalArgumentException("Ruleset not found."));

        if (rulesetVersion == null) {
            throw new IllegalArgumentException("rulesetVersion is required.");
        }
        if (scoreTotal == null) {
            throw new IllegalArgumentException("scoreTotal is required.");
        }
        String normalizedGrade = normalizeRequiredString(grade, "grade");
        String normalizedExplainability = normalizeRequiredString(explainabilityJson, "explainabilityJson");
        String normalizedFeatureSnapshot = normalizeRequiredString(featureSnapshotJson, "featureSnapshotJson");
        ScoringTrigger effectiveTrigger = trigger == null ? ScoringTrigger.APPROVAL : trigger;

        User scoredByUser = null;
        if (scoredByUserId != null) {
            scoredByUser = userRepository.findById(scoredByUserId)
                    .orElseThrow(() -> new IllegalArgumentException("scoredByUser not found."));
        }

        ClaimScore score = new ClaimScore();
        score.setClaim(claim);
        score.setRuleset(ruleset);
        score.setRulesetVersion(rulesetVersion);
        score.setEligible(eligible);
        score.setScoreTotal(scoreTotal);
        score.setGrade(normalizedGrade);
        score.setSubscoreEnforceability(subscoreEnforceability);
        score.setSubscoreDocumentation(subscoreDocumentation);
        score.setSubscoreCollectability(subscoreCollectability);
        score.setSubscoreOperationalRisk(subscoreOperationalRisk);
        score.setExplainabilityJson(normalizedExplainability);
        score.setFeatureSnapshotJson(normalizedFeatureSnapshot);
        score.setScoredByUser(scoredByUser);

        ClaimScore saved = claimScoreRepository.save(score);

        Long actorId = scoredByUser == null ? null : scoredByUser.getId();
        String actorRole = scoredByUser == null || scoredByUser.getRole() == null ? null : scoredByUser.getRole().name();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("rulesetId", ruleset.getId());
        metadata.put("rulesetVersion", rulesetVersion);
        metadata.put("scoreTotal", scoreTotal);
        metadata.put("grade", normalizedGrade);
        metadata.put("eligible", eligible);
        metadata.put("trigger", effectiveTrigger.name());

        auditService.record(
                actorId,
                actorRole,
                "CLAIM_SCORED",
                "CLAIM",
                claim.getId(),
                toJson(metadata)
        );

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ClaimScoreResponseDTO> listScoreRunsForClaim(Long claimId, String adminEmail) {
        requireAdmin(adminEmail);
        return claimScoreRepository.findByClaimIdOrderByScoredAtDescIdDesc(claimId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private ClaimScoreResponseDTO toResponse(ClaimScore score) {
        ClaimScoreResponseDTO dto = new ClaimScoreResponseDTO();
        dto.setScoredAt(score.getScoredAt());
        dto.setScoreTotal(score.getScoreTotal());
        dto.setGrade(score.getGrade());
        dto.setEligible(score.isEligible());
        dto.setRulesetId(score.getRuleset() == null ? null : score.getRuleset().getId());
        dto.setRulesetVersion(score.getRulesetVersion());
        dto.setSubscoreEnforceability(score.getSubscoreEnforceability());
        dto.setSubscoreDocumentation(score.getSubscoreDocumentation());
        dto.setSubscoreCollectability(score.getSubscoreCollectability());
        dto.setSubscoreOperationalRisk(score.getSubscoreOperationalRisk());
        return dto;
    }

    private User requireAdmin(String email) {
        String normalized = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        User user = normalized == null ? null : userRepository.findByEmail(normalized);
        if (user == null) {
            throw new IllegalArgumentException("Admin user not found.");
        }
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Admin role required.");
        }
        return user;
    }

    private String normalizeRequiredString(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return normalized;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize scoring audit metadata.", ex);
        }
    }
}
