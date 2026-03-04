package com.claimchain.backend.dto;

import java.time.Instant;

public class ClaimScoreResponseDTO {

    private Instant scoredAt;
    private Integer scoreTotal;
    private String grade;
    private boolean eligible;
    private Long rulesetId;
    private Integer rulesetVersion;
    private Integer subscoreEnforceability;
    private Integer subscoreDocumentation;
    private Integer subscoreCollectability;
    private Integer subscoreOperationalRisk;

    public ClaimScoreResponseDTO() {
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(Instant scoredAt) {
        this.scoredAt = scoredAt;
    }

    public Integer getScoreTotal() {
        return scoreTotal;
    }

    public void setScoreTotal(Integer scoreTotal) {
        this.scoreTotal = scoreTotal;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
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

    public Integer getSubscoreEnforceability() {
        return subscoreEnforceability;
    }

    public void setSubscoreEnforceability(Integer subscoreEnforceability) {
        this.subscoreEnforceability = subscoreEnforceability;
    }

    public Integer getSubscoreDocumentation() {
        return subscoreDocumentation;
    }

    public void setSubscoreDocumentation(Integer subscoreDocumentation) {
        this.subscoreDocumentation = subscoreDocumentation;
    }

    public Integer getSubscoreCollectability() {
        return subscoreCollectability;
    }

    public void setSubscoreCollectability(Integer subscoreCollectability) {
        this.subscoreCollectability = subscoreCollectability;
    }

    public Integer getSubscoreOperationalRisk() {
        return subscoreOperationalRisk;
    }

    public void setSubscoreOperationalRisk(Integer subscoreOperationalRisk) {
        this.subscoreOperationalRisk = subscoreOperationalRisk;
    }
}
