package com.claimchain.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "claim_scores")
public class ClaimScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruleset_id", nullable = false)
    private Ruleset ruleset;

    @Column(name = "ruleset_version", nullable = false)
    private Integer rulesetVersion;

    @Column(name = "eligible", nullable = false)
    private boolean eligible;

    @Column(name = "score_total", nullable = false)
    private Integer scoreTotal;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "subscore_enforceability")
    private Integer subscoreEnforceability;

    @Column(name = "subscore_documentation")
    private Integer subscoreDocumentation;

    @Column(name = "subscore_collectability")
    private Integer subscoreCollectability;

    @Column(name = "subscore_operational_risk")
    private Integer subscoreOperationalRisk;

    @Column(name = "explainability_json", nullable = false, columnDefinition = "TEXT")
    private String explainabilityJson;

    @Column(name = "feature_snapshot_json", nullable = false, columnDefinition = "TEXT")
    private String featureSnapshotJson;

    @Column(name = "scored_at", nullable = false)
    private Instant scoredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scored_by_user_id")
    private User scoredByUser;

    public ClaimScore() {
    }

    @PrePersist
    public void prePersist() {
        if (scoredAt == null) {
            scoredAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public Ruleset getRuleset() {
        return ruleset;
    }

    public void setRuleset(Ruleset ruleset) {
        this.ruleset = ruleset;
    }

    public Integer getRulesetVersion() {
        return rulesetVersion;
    }

    public void setRulesetVersion(Integer rulesetVersion) {
        this.rulesetVersion = rulesetVersion;
    }

    public boolean isEligible() {
        return eligible;
    }

    public void setEligible(boolean eligible) {
        this.eligible = eligible;
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

    public String getExplainabilityJson() {
        return explainabilityJson;
    }

    public void setExplainabilityJson(String explainabilityJson) {
        this.explainabilityJson = explainabilityJson;
    }

    public String getFeatureSnapshotJson() {
        return featureSnapshotJson;
    }

    public void setFeatureSnapshotJson(String featureSnapshotJson) {
        this.featureSnapshotJson = featureSnapshotJson;
    }

    public Instant getScoredAt() {
        return scoredAt;
    }

    public void setScoredAt(Instant scoredAt) {
        this.scoredAt = scoredAt;
    }

    public User getScoredByUser() {
        return scoredByUser;
    }

    public void setScoredByUser(User scoredByUser) {
        this.scoredByUser = scoredByUser;
    }
}
