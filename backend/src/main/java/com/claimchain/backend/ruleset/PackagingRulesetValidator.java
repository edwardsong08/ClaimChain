package com.claimchain.backend.ruleset;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PackagingRulesetValidator implements RulesetValidator {

    private final ObjectMapper objectMapper;

    public PackagingRulesetValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void validate(String configJson) {
        List<String> errors = new ArrayList<>();
        JsonNode root = parseObject(configJson, errors);

        if (root != null) {
            validateEligibility(root, errors);
            validatePackageSizing(root, errors);
        }

        if (!errors.isEmpty()) {
            throw new RulesetValidationException(errors);
        }
    }

    private JsonNode parseObject(String configJson, List<String> errors) {
        if (configJson == null || configJson.trim().isEmpty()) {
            errors.add("configJson must not be blank.");
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (!root.isObject()) {
                errors.add("configJson must be a JSON object.");
                return null;
            }
            return root;
        } catch (JsonProcessingException ex) {
            errors.add("configJson must be valid JSON.");
            return null;
        }
    }

    private void validateEligibility(JsonNode root, List<String> errors) {
        JsonNode eligibility = root.get("eligibility");
        if (eligibility == null || !eligibility.isObject()) {
            errors.add("eligibility object is required.");
            return;
        }

        boolean hasValidMinGrade = false;
        JsonNode minGradeNode = eligibility.get("minGrade");
        if (minGradeNode != null && !minGradeNode.isNull()) {
            if (!minGradeNode.isTextual() || minGradeNode.asText().trim().isEmpty()) {
                errors.add("eligibility.minGrade must be a non-blank string.");
            } else {
                hasValidMinGrade = true;
            }
        }

        boolean hasValidMinScore = false;
        JsonNode minScoreNode = eligibility.get("minScore");
        if (minScoreNode != null && !minScoreNode.isNull()) {
            if (!minScoreNode.isNumber() || !minScoreNode.canConvertToInt()) {
                errors.add("eligibility.minScore must be an integer.");
            } else {
                int minScore = minScoreNode.asInt();
                if (minScore < 0 || minScore > 100) {
                    errors.add("eligibility.minScore must be between 0 and 100.");
                } else {
                    hasValidMinScore = true;
                }
            }
        }

        JsonNode minBalanceNode = eligibility.get("minBalance");
        if (minBalanceNode != null && !minBalanceNode.isNull()) {
            if (!minBalanceNode.isNumber()) {
                errors.add("eligibility.minBalance must be numeric.");
            } else if (minBalanceNode.decimalValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
                errors.add("eligibility.minBalance must be >= 0.");
            }
        }

        JsonNode requireJurisdictionKnownNode = eligibility.get("requireJurisdictionKnown");
        if (requireJurisdictionKnownNode != null
                && !requireJurisdictionKnownNode.isNull()
                && !requireJurisdictionKnownNode.isBoolean()) {
            errors.add("eligibility.requireJurisdictionKnown must be boolean.");
        }

        JsonNode requireInvoiceDocumentNode = eligibility.get("requireInvoiceDocument");
        if (requireInvoiceDocumentNode != null
                && !requireInvoiceDocumentNode.isNull()
                && !requireInvoiceDocumentNode.isBoolean()) {
            errors.add("eligibility.requireInvoiceDocument must be boolean.");
        }

        if (!hasValidMinGrade && !hasValidMinScore) {
            errors.add("eligibility must include minGrade or minScore.");
        }
    }

    private void validatePackageSizing(JsonNode root, List<String> errors) {
        JsonNode packageSizing = root.get("packageSizing");
        if (packageSizing == null || packageSizing.isNull()) {
            return;
        }
        if (!packageSizing.isObject()) {
            errors.add("packageSizing must be an object.");
            return;
        }

        Integer minClaims = readPositiveInt(packageSizing, "minClaims", errors);
        Integer maxClaims = readPositiveInt(packageSizing, "maxClaims", errors);

        if (minClaims != null && maxClaims != null && minClaims > maxClaims) {
            errors.add("packageSizing.minClaims must be <= packageSizing.maxClaims.");
        }
    }

    private Integer readPositiveInt(JsonNode objectNode, String fieldName, List<String> errors) {
        JsonNode node = objectNode.get(fieldName);
        if (node == null || node.isNull()) {
            errors.add("packageSizing." + fieldName + " is required.");
            return null;
        }
        if (!node.isNumber() || !node.canConvertToInt()) {
            errors.add("packageSizing." + fieldName + " must be an integer.");
            return null;
        }

        int value = node.asInt();
        if (value <= 0) {
            errors.add("packageSizing." + fieldName + " must be positive.");
            return null;
        }
        return value;
    }
}
