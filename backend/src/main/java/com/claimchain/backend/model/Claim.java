package com.claimchain.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    private String debtorName;
    private String debtorEmail;
    private String debtorPhone;
    @Column(name = "debtor_address")
    private String debtorAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "debtor_type", nullable = false, length = 50)
    private DebtorType debtorType = DebtorType.OTHER;

    @Column(name = "jurisdiction_state", nullable = false, length = 50)
    private String jurisdictionState = "UNKNOWN";

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false, length = 50)
    private ClaimType claimType = ClaimType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 50)
    private DisputeStatus disputeStatus = DisputeStatus.NONE;

    private String clientName;
    private String clientContact;
    private String clientAddress;
    private String debtType;
    private String contactHistory;

    private String serviceDescription;
    private BigDecimal amountOwed;
    @Column(name = "original_amount", precision = 19, scale = 2)
    private BigDecimal originalAmount;
    @Column(name = "current_amount", precision = 19, scale = 2)
    private BigDecimal currentAmount;

    private LocalDate dateOfService;
    private LocalDate dateOfDefault;
    @Column(name = "last_payment_date")
    private LocalDate lastPaymentDate;

    private String documentUrl; // For public viewing (optional)
    private String contractFileKey; // 🔑 S3 object key for backend

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private ClaimStatus status = ClaimStatus.SUBMITTED;
    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;
    @Column(name = "missing_docs_json", columnDefinition = "TEXT")
    private String missingDocsJson;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_started_by_user_id")
    private User reviewStartedByUser;
    @Column(name = "review_started_at")
    private Instant reviewStartedAt;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_user_id")
    private User reviewedByUser;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    private Integer riskScore; // placeholder for next step

    private LocalDateTime submittedAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Claim() {}

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }

    public String getDebtorName() { return debtorName; }
    public void setDebtorName(String debtorName) { this.debtorName = debtorName; }

    public String getDebtorEmail() { return debtorEmail; }
    public void setDebtorEmail(String debtorEmail) { this.debtorEmail = debtorEmail; }

    public String getDebtorPhone() { return debtorPhone; }
    public void setDebtorPhone(String debtorPhone) { this.debtorPhone = debtorPhone; }

    public String getDebtorAddress() { return debtorAddress; }
    public void setDebtorAddress(String debtorAddress) { this.debtorAddress = debtorAddress; }

    public DebtorType getDebtorType() { return debtorType; }
    public void setDebtorType(DebtorType debtorType) { this.debtorType = debtorType; }

    public String getJurisdictionState() { return jurisdictionState; }
    public void setJurisdictionState(String jurisdictionState) { this.jurisdictionState = jurisdictionState; }

    public ClaimType getClaimType() { return claimType; }
    public void setClaimType(ClaimType claimType) { this.claimType = claimType; }

    public DisputeStatus getDisputeStatus() { return disputeStatus; }
    public void setDisputeStatus(DisputeStatus disputeStatus) { this.disputeStatus = disputeStatus; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientContact() { return clientContact; }
    public void setClientContact(String clientContact) { this.clientContact = clientContact; }

    public String getClientAddress() { return clientAddress; }
    public void setClientAddress(String clientAddress) { this.clientAddress = clientAddress; }

    public String getDebtType() { return debtType; }
    public void setDebtType(String debtType) { this.debtType = debtType; }

    public String getContactHistory() { return contactHistory; }
    public void setContactHistory(String contactHistory) { this.contactHistory = contactHistory; }

    public String getServiceDescription() { return serviceDescription; }
    public void setServiceDescription(String serviceDescription) { this.serviceDescription = serviceDescription; }

    public BigDecimal getAmountOwed() { return amountOwed; }
    public void setAmountOwed(BigDecimal amountOwed) { this.amountOwed = amountOwed; }

    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }

    public BigDecimal getCurrentAmount() { return currentAmount; }
    public void setCurrentAmount(BigDecimal currentAmount) { this.currentAmount = currentAmount; }

    public LocalDate getDateOfService() { return dateOfService; }
    public void setDateOfService(LocalDate dateOfService) { this.dateOfService = dateOfService; }

    public LocalDate getDateOfDefault() { return dateOfDefault; }
    public void setDateOfDefault(LocalDate dateOfDefault) { this.dateOfDefault = dateOfDefault; }

    public LocalDate getLastPaymentDate() { return lastPaymentDate; }
    public void setLastPaymentDate(LocalDate lastPaymentDate) { this.lastPaymentDate = lastPaymentDate; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getContractFileKey() { return contractFileKey; }
    public void setContractFileKey(String contractFileKey) { this.contractFileKey = contractFileKey; }

    public ClaimStatus getStatus() { return status; }
    public void setStatus(ClaimStatus status) { this.status = status; }

    public String getReviewNotes() { return reviewNotes; }
    public void setReviewNotes(String reviewNotes) { this.reviewNotes = reviewNotes; }

    public String getMissingDocsJson() { return missingDocsJson; }
    public void setMissingDocsJson(String missingDocsJson) { this.missingDocsJson = missingDocsJson; }

    public User getReviewStartedByUser() { return reviewStartedByUser; }
    public void setReviewStartedByUser(User reviewStartedByUser) { this.reviewStartedByUser = reviewStartedByUser; }

    public Instant getReviewStartedAt() { return reviewStartedAt; }
    public void setReviewStartedAt(Instant reviewStartedAt) { this.reviewStartedAt = reviewStartedAt; }

    public User getReviewedByUser() { return reviewedByUser; }
    public void setReviewedByUser(User reviewedByUser) { this.reviewedByUser = reviewedByUser; }

    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
