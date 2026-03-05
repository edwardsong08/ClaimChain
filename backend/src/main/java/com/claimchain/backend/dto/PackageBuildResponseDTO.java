package com.claimchain.backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PackageBuildResponseDTO {

    private Long packageId;
    private boolean dryRun;
    private boolean buildable;
    private String status;
    private Long rulesetId;
    private Integer rulesetVersion;
    private Integer totalClaims;
    private BigDecimal totalFaceValue;
    private List<Long> claimIds;
    private List<String> failureReasons;

    public Long getPackageId() {
        return packageId;
    }

    public void setPackageId(Long packageId) {
        this.packageId = packageId;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public boolean isBuildable() {
        return buildable;
    }

    public void setBuildable(boolean buildable) {
        this.buildable = buildable;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Integer getTotalClaims() {
        return totalClaims;
    }

    public void setTotalClaims(Integer totalClaims) {
        this.totalClaims = totalClaims;
    }

    public BigDecimal getTotalFaceValue() {
        return totalFaceValue;
    }

    public void setTotalFaceValue(BigDecimal totalFaceValue) {
        this.totalFaceValue = totalFaceValue;
    }

    public List<Long> getClaimIds() {
        return claimIds;
    }

    public void setClaimIds(List<Long> claimIds) {
        this.claimIds = claimIds;
    }

    public List<String> getFailureReasons() {
        return failureReasons;
    }

    public void setFailureReasons(List<String> failureReasons) {
        this.failureReasons = failureReasons;
    }
}
