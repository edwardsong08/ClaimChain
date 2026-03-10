package com.claimchain.backend.service;

import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.ruleset.PackagingRulesetValidator;
import com.claimchain.backend.ruleset.ScoringRulesetValidator;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class RulesetService {

    private final RulesetRepository rulesetRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ScoringRulesetValidator scoringRulesetValidator;
    private final PackagingRulesetValidator packagingRulesetValidator;

    public RulesetService(
            RulesetRepository rulesetRepository,
            UserRepository userRepository,
            AuditService auditService,
            ObjectMapper objectMapper,
            ScoringRulesetValidator scoringRulesetValidator,
            PackagingRulesetValidator packagingRulesetValidator
    ) {
        this.rulesetRepository = rulesetRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.scoringRulesetValidator = scoringRulesetValidator;
        this.packagingRulesetValidator = packagingRulesetValidator;
    }

    @Transactional(readOnly = true)
    public Optional<Ruleset> getActive(RulesetType type) {
        return rulesetRepository.findFirstByTypeAndStatusOrderByVersionDescIdDesc(type, RulesetStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<Ruleset> listByType(RulesetType type) {
        return rulesetRepository.findByTypeOrderByVersionDesc(type);
    }

    @Transactional
    public Ruleset createDraft(RulesetType type, String configJson, String adminEmail) {
        User admin = requireAdmin(adminEmail);
        String normalizedConfigJson = normalizeConfigJson(configJson);

        int nextVersion = rulesetRepository.findMaxVersionByType(type) + 1;

        Ruleset draft = new Ruleset();
        draft.setType(type);
        draft.setStatus(RulesetStatus.DRAFT);
        draft.setVersion(nextVersion);
        draft.setConfigJson(normalizedConfigJson);
        draft.setCreatedByUser(admin);

        Ruleset saved = rulesetRepository.save(draft);

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "RULESET_DRAFT_CREATED",
                "RULESET",
                saved.getId(),
                metadataJson(saved, null)
        );

        return saved;
    }

    @Transactional
    public Ruleset activate(Long rulesetId, String adminEmail) {
        User admin = requireAdmin(adminEmail);

        Ruleset target = rulesetRepository.findById(rulesetId)
                .orElseThrow(() -> new IllegalArgumentException("Ruleset not found."));

        if (target.getStatus() != RulesetStatus.DRAFT) {
            throw new IllegalArgumentException("Only DRAFT rulesets can be activated.");
        }

        validateForActivation(target);

        Ruleset currentActive = rulesetRepository.findFirstByTypeAndStatusOrderByVersionDescIdDesc(target.getType(), RulesetStatus.ACTIVE)
                .orElse(null);
        Long priorActiveId = currentActive == null ? null : currentActive.getId();

        if (currentActive != null && !currentActive.getId().equals(target.getId())) {
            currentActive.setStatus(RulesetStatus.ARCHIVED);
            rulesetRepository.saveAndFlush(currentActive);
        }

        target.setStatus(RulesetStatus.ACTIVE);
        target.setActivatedByUser(admin);
        target.setActivatedAt(Instant.now());
        Ruleset saved = rulesetRepository.save(target);

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "RULESET_ACTIVATED",
                "RULESET",
                saved.getId(),
                metadataJson(saved, priorActiveId)
        );

        return saved;
    }

    private void validateForActivation(Ruleset ruleset) {
        if (ruleset.getType() == RulesetType.SCORING) {
            scoringRulesetValidator.validate(ruleset.getConfigJson());
            return;
        }
        if (ruleset.getType() == RulesetType.PACKAGING) {
            packagingRulesetValidator.validate(ruleset.getConfigJson());
            return;
        }
        throw new IllegalArgumentException("Unsupported ruleset type: " + ruleset.getType());
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

    private String normalizeConfigJson(String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            throw new IllegalArgumentException("configJson must not be blank.");
        }
        return configJson.trim();
    }

    private String metadataJson(Ruleset ruleset, Long priorActiveId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", ruleset.getType() == null ? null : ruleset.getType().name());
        metadata.put("version", ruleset.getVersion());
        metadata.put("priorActiveId", priorActiveId);

        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize ruleset audit metadata.", ex);
        }
    }
}
