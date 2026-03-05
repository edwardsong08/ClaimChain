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
@Table(name = "package_claims")
public class PackageClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private Package packageEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    @Column(name = "included_reason_json", nullable = false, columnDefinition = "TEXT")
    private String includedReasonJson = "{}";

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "added_by_user_id", nullable = false)
    private User addedByUser;

    public PackageClaim() {
    }

    @PrePersist
    public void prePersist() {
        if (includedReasonJson == null || includedReasonJson.isBlank()) {
            includedReasonJson = "{}";
        }
        if (addedAt == null) {
            addedAt = Instant.now();
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

    public String getIncludedReasonJson() {
        return includedReasonJson;
    }

    public void setIncludedReasonJson(String includedReasonJson) {
        this.includedReasonJson = includedReasonJson;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public void setAddedAt(Instant addedAt) {
        this.addedAt = addedAt;
    }

    public User getAddedByUser() {
        return addedByUser;
    }

    public void setAddedByUser(User addedByUser) {
        this.addedByUser = addedByUser;
    }
}
