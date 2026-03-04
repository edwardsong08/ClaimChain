package com.claimchain.backend.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoringRulesetConfig {

    private Integer schemaVersion;
    private EligibilityConfig eligibility = new EligibilityConfig();
    private WeightsConfig weights = new WeightsConfig();
    private List<GradeBandConfig> gradeBands = new ArrayList<>();
    private CapsConfig caps = new CapsConfig();
    private List<RuleConfig> rules = new ArrayList<>();

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public EligibilityConfig getEligibility() {
        return eligibility;
    }

    public void setEligibility(EligibilityConfig eligibility) {
        this.eligibility = eligibility;
    }

    public WeightsConfig getWeights() {
        return weights;
    }

    public void setWeights(WeightsConfig weights) {
        this.weights = weights;
    }

    public List<GradeBandConfig> getGradeBands() {
        return gradeBands;
    }

    public void setGradeBands(List<GradeBandConfig> gradeBands) {
        this.gradeBands = gradeBands;
    }

    public CapsConfig getCaps() {
        return caps;
    }

    public void setCaps(CapsConfig caps) {
        this.caps = caps;
    }

    public List<RuleConfig> getRules() {
        return rules;
    }

    public void setRules(List<RuleConfig> rules) {
        this.rules = rules;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EligibilityConfig {
        private String requiredClaimStatus;
        private List<String> requiredDocTypes = new ArrayList<>();
        private Double minExtractionSuccessRate;
        private Boolean blockActiveDisputes;

        public String getRequiredClaimStatus() {
            return requiredClaimStatus;
        }

        public void setRequiredClaimStatus(String requiredClaimStatus) {
            this.requiredClaimStatus = requiredClaimStatus;
        }

        public List<String> getRequiredDocTypes() {
            return requiredDocTypes;
        }

        public void setRequiredDocTypes(List<String> requiredDocTypes) {
            this.requiredDocTypes = requiredDocTypes;
        }

        public Double getMinExtractionSuccessRate() {
            return minExtractionSuccessRate;
        }

        public void setMinExtractionSuccessRate(Double minExtractionSuccessRate) {
            this.minExtractionSuccessRate = minExtractionSuccessRate;
        }

        public Boolean getBlockActiveDisputes() {
            return blockActiveDisputes;
        }

        public void setBlockActiveDisputes(Boolean blockActiveDisputes) {
            this.blockActiveDisputes = blockActiveDisputes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WeightsConfig {
        private Double enforceability;
        private Double documentation;
        private Double collectability;
        private Double operationalRisk;

        public Double getEnforceability() {
            return enforceability;
        }

        public void setEnforceability(Double enforceability) {
            this.enforceability = enforceability;
        }

        public Double getDocumentation() {
            return documentation;
        }

        public void setDocumentation(Double documentation) {
            this.documentation = documentation;
        }

        public Double getCollectability() {
            return collectability;
        }

        public void setCollectability(Double collectability) {
            this.collectability = collectability;
        }

        public Double getOperationalRisk() {
            return operationalRisk;
        }

        public void setOperationalRisk(Double operationalRisk) {
            this.operationalRisk = operationalRisk;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GradeBandConfig {
        private String grade;
        private Integer minScore;

        public String getGrade() {
            return grade;
        }

        public void setGrade(String grade) {
            this.grade = grade;
        }

        public Integer getMinScore() {
            return minScore;
        }

        public void setMinScore(Integer minScore) {
            this.minScore = minScore;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CapsConfig {
        private Integer enforceabilityMax;
        private Integer documentationMax;
        private Integer collectabilityMax;
        private Integer operationalRiskMax;

        public Integer getEnforceabilityMax() {
            return enforceabilityMax;
        }

        public void setEnforceabilityMax(Integer enforceabilityMax) {
            this.enforceabilityMax = enforceabilityMax;
        }

        public Integer getDocumentationMax() {
            return documentationMax;
        }

        public void setDocumentationMax(Integer documentationMax) {
            this.documentationMax = documentationMax;
        }

        public Integer getCollectabilityMax() {
            return collectabilityMax;
        }

        public void setCollectabilityMax(Integer collectabilityMax) {
            this.collectabilityMax = collectabilityMax;
        }

        public Integer getOperationalRiskMax() {
            return operationalRiskMax;
        }

        public void setOperationalRiskMax(Integer operationalRiskMax) {
            this.operationalRiskMax = operationalRiskMax;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RuleConfig {
        private String id;
        private String group;
        private Integer points;
        private String reason;
        private Map<String, Object> when;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public Integer getPoints() {
            return points;
        }

        public void setPoints(Integer points) {
            this.points = points;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public Map<String, Object> getWhen() {
            return when;
        }

        public void setWhen(Map<String, Object> when) {
            this.when = when;
        }
    }
}
