package com.claimchain.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class BuyerPackageDetailResponseDTO {

    private Long id;
    private Integer totalClaims;
    private BigDecimal totalFaceValue;
    private Instant createdAt;
    private List<AnonymizedClaimViewResponseDTO> claims;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<AnonymizedClaimViewResponseDTO> getClaims() {
        return claims;
    }

    public void setClaims(List<AnonymizedClaimViewResponseDTO> claims) {
        this.claims = claims;
    }
}
