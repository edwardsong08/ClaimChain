package com.claimchain.backend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String debtorName;
    private String debtorEmail;
    private String debtorPhone;

    private String clientName;
    private String clientContact;
    private String clientAddress;
    private String debtType;
    private String contactHistory;

    private String serviceDescription;
    private BigDecimal amountOwed;

    private LocalDate dateOfService;
    private LocalDate dateOfDefault;

    private String documentUrl; // For public viewing (optional)
    private String contractFileKey; // 🔑 S3 object key for backend

    private String status = "PENDING";
    private Integer riskScore; // placeholder for next step

    private LocalDateTime submittedAt = LocalDateTime.now();

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    public Claim() {}

    // Getters and Setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDebtorName() { return debtorName; }
    public void setDebtorName(String debtorName) { this.debtorName = debtorName; }

    public String getDebtorEmail() { return debtorEmail; }
    public void setDebtorEmail(String debtorEmail) { this.debtorEmail = debtorEmail; }

    public String getDebtorPhone() { return debtorPhone; }
    public void setDebtorPhone(String debtorPhone) { this.debtorPhone = debtorPhone; }

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

    public LocalDate getDateOfService() { return dateOfService; }
    public void setDateOfService(LocalDate dateOfService) { this.dateOfService = dateOfService; }

    public LocalDate getDateOfDefault() { return dateOfDefault; }
    public void setDateOfDefault(LocalDate dateOfDefault) { this.dateOfDefault = dateOfDefault; }

    public String getDocumentUrl() { return documentUrl; }
    public void setDocumentUrl(String documentUrl) { this.documentUrl = documentUrl; }

    public String getContractFileKey() { return contractFileKey; }
    public void setContractFileKey(String contractFileKey) { this.contractFileKey = contractFileKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}
