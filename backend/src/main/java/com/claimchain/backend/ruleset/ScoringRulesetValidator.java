package com.claimchain.backend.ruleset;

import com.claimchain.backend.model.ClaimStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ScoringRulesetValidator implements RulesetValidator {

    private static final List<String> REQUIRED_WEIGHT_KEYS = List.of(
            "enforceability",
            "documentation",
            "collectability",
            "operationalRisk"
    );

    private final ObjectMapper objectMapper;

    public ScoringRulesetValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void validate(String configJson) {
        List<String> errors = new ArrayList<>();
        JsonNode root = parseObject(configJson, errors);

        if (root != null) {
            validateWeights(root, errors);
            validateGradeBands(root, errors);
            validateEligibility(root, errors);
            validateCaps(root, errors);
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

    private void validateWeights(JsonNode root, List<String> errors) {
        JsonNode weights = root.get("weights");
        if (weights == null || !weights.isObject()) {
            errors.add("weights object is required.");
            return;
        }

        double sum = 0d;
        boolean allValid = true;
        for (String key : REQUIRED_WEIGHT_KEYS) {
            JsonNode valueNode = weights.get(key);
            if (valueNode == null || !valueNode.isNumber()) {
                errors.add("weights." + key + " must be numeric.");
                allValid = false;
                continue;
            }

            double value = valueNode.asDouble();
            if (value <= 0d || value > 1d) {
                errors.add("weights." + key + " must be > 0 and <= 1.");
                allValid = false;
                continue;
            }
            sum += value;
        }

        if (allValid && (sum < 0.99d || sum > 1.01d)) {
            errors.add("weights must sum to approximately 1.0.");
        }
    }

    private void validateGradeBands(JsonNode root, List<String> errors) {
        JsonNode gradeBands = root.get("gradeBands");
        if (gradeBands == null || !gradeBands.isArray() || gradeBands.isEmpty()) {
            errors.add("gradeBands array is required.");
            return;
        }

        Integer previousScore = null;
        boolean includesZeroBand = false;
        Set<Integer> seenScores = new HashSet<>();

        for (int i = 0; i < gradeBands.size(); i++) {
            JsonNode band = gradeBands.get(i);
            if (!band.isObject()) {
                errors.add("gradeBands[" + i + "] must be an object.");
                continue;
            }

            JsonNode gradeNode = band.get("grade");
            if (gradeNode == null || !gradeNode.isTextual() || gradeNode.asText().trim().isEmpty()) {
                errors.add("gradeBands[" + i + "].grade must be a non-blank string.");
            }

            JsonNode minScoreNode = band.get("minScore");
            if (minScoreNode == null || !minScoreNode.isNumber() || !minScoreNode.canConvertToInt()) {
                errors.add("gradeBands[" + i + "].minScore must be an integer.");
                continue;
            }

            int minScore = minScoreNode.asInt();
            if (minScore < 0 || minScore > 100) {
                errors.add("gradeBands[" + i + "].minScore must be between 0 and 100.");
            }

            if (previousScore != null && minScore > previousScore) {
                errors.add("gradeBands must be sorted in descending minScore order.");
            }
            previousScore = minScore;

            if (!seenScores.add(minScore)) {
                errors.add("gradeBands contains duplicate minScore: " + minScore + ".");
            }

            if (minScore == 0) {
                includesZeroBand = true;
            }
        }

        if (!includesZeroBand) {
            errors.add("gradeBands must include a minScore=0 band.");
        }
    }

    private void validateEligibility(JsonNode root, List<String> errors) {
        JsonNode eligibility = root.get("eligibility");
        if (eligibility == null || eligibility.isNull()) {
            return;
        }
        if (!eligibility.isObject()) {
            errors.add("eligibility must be an object.");
            return;
        }

        JsonNode statusNode = eligibility.get("requiredClaimStatus");
        if (statusNode == null || statusNode.isNull()) {
            return;
        }
        if (!statusNode.isTextual() || statusNode.asText().trim().isEmpty()) {
            errors.add("eligibility.requiredClaimStatus must be a non-blank string.");
            return;
        }

        String statusText = statusNode.asText().trim().toUpperCase(Locale.ROOT);
        try {
            ClaimStatus.valueOf(statusText);
        } catch (IllegalArgumentException ex) {
            errors.add("eligibility.requiredClaimStatus must be a valid ClaimStatus value.");
        }
    }

    private void validateCaps(JsonNode root, List<String> errors) {
        JsonNode caps = root.get("caps");
        if (caps == null || caps.isNull()) {
            return;
        }
        if (!caps.isObject()) {
            errors.add("caps must be an object.");
            return;
        }

        Iterator<String> names = caps.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            JsonNode valueNode = caps.get(name);
            if (valueNode == null || !valueNode.isNumber()) {
                errors.add("caps." + name + " must be numeric.");
                continue;
            }
            double value = valueNode.asDouble();
            if (value < 0d || value > 100d) {
                errors.add("caps." + name + " must be between 0 and 100.");
            }
        }
    }
}
