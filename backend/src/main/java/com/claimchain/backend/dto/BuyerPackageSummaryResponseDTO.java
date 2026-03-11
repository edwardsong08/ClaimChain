package com.claimchain.backend.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class BuyerPackageSummaryResponseDTO {

    private Long id;
    private String status;
    private Integer totalClaims;
    private BigDecimal totalFaceValue;
    private BigDecimal price;
    private Instant createdAt;
    private Instant purchasedAt;

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getPurchasedAt() {
        return purchasedAt;
    }

    public void setPurchasedAt(Instant purchasedAt) {
        this.purchasedAt = purchasedAt;
    }
}
