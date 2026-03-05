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
@Table(name = "anonymized_claim_views")
public class AnonymizedClaimView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private Package packageEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "jurisdiction_state", nullable = false, length = 50)
    private String jurisdictionState;

    @Column(name = "debtor_type", nullable = false, length = 50)
    private String debtorType;

    @Column(name = "claim_type", nullable = false, length = 50)
    private String claimType;

    @Column(name = "dispute_status", nullable = false, length = 50)
    private String disputeStatus;

    @Column(name = "debt_age_days", nullable = false)
    private Integer debtAgeDays;

    @Column(name = "amount_band", nullable = false, length = 30)
    private String amountBand;

    @Column(name = "score_total", nullable = false)
    private Integer scoreTotal;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "extraction_success_rate", nullable = false)
    private Double extractionSuccessRate;

    @Column(name = "doc_types_present", nullable = false, columnDefinition = "TEXT")
    private String docTypesPresent;

    public AnonymizedClaimView() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Package getPackageEntity() {
        return packageEntity;
    }

    public void setPackageEntity(Package packageEntity) {
        this.packageEntity = packageEntity;
    }

    public Claim getClaim() {
        return claim;
    }

    public void setClaim(Claim claim) {
        this.claim = claim;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getJurisdictionState() {
        return jurisdictionState;
    }

    public void setJurisdictionState(String jurisdictionState) {
        this.jurisdictionState = jurisdictionState;
    }

    public String getDebtorType() {
        return debtorType;
    }

    public void setDebtorType(String debtorType) {
        this.debtorType = debtorType;
    }

    public String getClaimType() {
        return claimType;
    }

    public void setClaimType(String claimType) {
        this.claimType = claimType;
    }

    public String getDisputeStatus() {
        return disputeStatus;
    }

    public void setDisputeStatus(String disputeStatus) {
        this.disputeStatus = disputeStatus;
    }

    public Integer getDebtAgeDays() {
        return debtAgeDays;
    }

    public void setDebtAgeDays(Integer debtAgeDays) {
        this.debtAgeDays = debtAgeDays;
    }

    public String getAmountBand() {
        return amountBand;
    }

    public void setAmountBand(String amountBand) {
        this.amountBand = amountBand;
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

    public Double getExtractionSuccessRate() {
        return extractionSuccessRate;
    }

    public void setExtractionSuccessRate(Double extractionSuccessRate) {
        this.extractionSuccessRate = extractionSuccessRate;
    }

    public String getDocTypesPresent() {
        return docTypesPresent;
    }

    public void setDocTypesPresent(String docTypesPresent) {
        this.docTypesPresent = docTypesPresent;
    }
}
