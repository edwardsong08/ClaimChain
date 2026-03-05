package com.claimchain.backend.dto;

public class AnonymizedClaimViewResponseDTO {

    private Long claimId;
    private String jurisdictionState;
    private String debtorType;
    private String claimType;
    private String disputeStatus;
    private Integer debtAgeDays;
    private String amountBand;
    private Integer scoreTotal;
    private String grade;
    private Double extractionSuccessRate;
    private String docTypesPresent;

    public Long getClaimId() {
        return claimId;
    }

    public void setClaimId(Long claimId) {
        this.claimId = claimId;
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
