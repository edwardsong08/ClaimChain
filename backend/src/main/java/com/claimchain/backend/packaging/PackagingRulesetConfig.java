package com.claimchain.backend.packaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PackagingRulesetConfig {

    private Integer schemaVersion;
    private Eligibility eligibility;
    private PackageSizing packageSizing;
    private Diversification diversification;
    private SelectionStrategy selectionStrategy;

    public Integer getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(Integer schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public Eligibility getEligibility() {
        return eligibility;
    }

    public void setEligibility(Eligibility eligibility) {
        this.eligibility = eligibility;
    }

    public PackageSizing getPackageSizing() {
        return packageSizing;
    }

    public void setPackageSizing(PackageSizing packageSizing) {
        this.packageSizing = packageSizing;
    }

    public Diversification getDiversification() {
        return diversification;
    }

    public void setDiversification(Diversification diversification) {
        this.diversification = diversification;
    }

    public SelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public void setSelectionStrategy(SelectionStrategy selectionStrategy) {
        this.selectionStrategy = selectionStrategy;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Eligibility {
        private Integer minScore;
        private String minGrade;
        private List<String> requiredDocTypes;
        private Double minExtractionSuccessRate;
        private List<String> excludeDisputeStatuses;

        public Integer getMinScore() {
            return minScore;
        }

        public void setMinScore(Integer minScore) {
            this.minScore = minScore;
        }

        public String getMinGrade() {
            return minGrade;
        }

        public void setMinGrade(String minGrade) {
            this.minGrade = minGrade;
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

        public List<String> getExcludeDisputeStatuses() {
            return excludeDisputeStatuses;
        }

        public void setExcludeDisputeStatuses(List<String> excludeDisputeStatuses) {
            this.excludeDisputeStatuses = excludeDisputeStatuses;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PackageSizing {
        private Integer minClaims;
        private Integer maxClaims;
        private BigDecimal minTotalFaceValue;
        private BigDecimal maxTotalFaceValue;

        public Integer getMinClaims() {
            return minClaims;
        }

        public void setMinClaims(Integer minClaims) {
            this.minClaims = minClaims;
        }

        public Integer getMaxClaims() {
            return maxClaims;
        }

        public void setMaxClaims(Integer maxClaims) {
            this.maxClaims = maxClaims;
        }

        public BigDecimal getMinTotalFaceValue() {
            return minTotalFaceValue;
        }

        public void setMinTotalFaceValue(BigDecimal minTotalFaceValue) {
            this.minTotalFaceValue = minTotalFaceValue;
        }

        public BigDecimal getMaxTotalFaceValue() {
            return maxTotalFaceValue;
        }

        public void setMaxTotalFaceValue(BigDecimal maxTotalFaceValue) {
            this.maxTotalFaceValue = maxTotalFaceValue;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Diversification {
        private Double maxPctPerJurisdiction;
        private Double maxPctPerDebtorType;

        public Double getMaxPctPerJurisdiction() {
            return maxPctPerJurisdiction;
        }

        public void setMaxPctPerJurisdiction(Double maxPctPerJurisdiction) {
            this.maxPctPerJurisdiction = maxPctPerJurisdiction;
        }

        public Double getMaxPctPerDebtorType() {
            return maxPctPerDebtorType;
        }

        public void setMaxPctPerDebtorType(Double maxPctPerDebtorType) {
            this.maxPctPerDebtorType = maxPctPerDebtorType;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SelectionStrategy {
        private String mode;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }
}
