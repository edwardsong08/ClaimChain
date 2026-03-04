package com.claimchain.backend.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ClaimRequestDTO {

    private String debtorName;
    private String debtorEmail;
    private String debtorPhone;

    @NotBlank
    private String debtorAddress;

    @NotNull
    private String debtorType;

    @NotBlank
    private String jurisdictionState;

    @NotNull
    private String claimType;

    @NotNull
    private String disputeStatus;

    @NotBlank
    private String clientName;

    @NotBlank
    private String clientContact;

    @NotBlank
    private String clientAddress;

    @NotBlank
    private String debtType;

    @NotBlank
    private String contactHistory;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    @JsonAlias("amount")
    private BigDecimal currentAmount;

    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal originalAmount;

    @NotNull
    private LocalDate dateOfDefault;

    private LocalDate dateOfService;
    private LocalDate lastPaymentDate;

    private String contractFileKey;

    public ClaimRequestDTO() {
    }

    public String getDebtorName() {
        return debtorName;
    }

    public void setDebtorName(String debtorName) {
        this.debtorName = debtorName;
    }

    public String getDebtorEmail() {
        return debtorEmail;
    }

    public void setDebtorEmail(String debtorEmail) {
        this.debtorEmail = debtorEmail;
    }

    public String getDebtorPhone() {
        return debtorPhone;
    }

    public void setDebtorPhone(String debtorPhone) {
        this.debtorPhone = debtorPhone;
    }

    public String getDebtorAddress() {
        return debtorAddress;
    }

    public void setDebtorAddress(String debtorAddress) {
        this.debtorAddress = debtorAddress;
    }

    public String getDebtorType() {
        return debtorType;
    }

    public void setDebtorType(String debtorType) {
        this.debtorType = debtorType;
    }

    public String getJurisdictionState() {
        return jurisdictionState;
    }

    public void setJurisdictionState(String jurisdictionState) {
        this.jurisdictionState = jurisdictionState;
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

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getClientContact() {
        return clientContact;
    }

    public void setClientContact(String clientContact) {
        this.clientContact = clientContact;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getDebtType() {
        return debtType;
    }

    public void setDebtType(String debtType) {
        this.debtType = debtType;
    }

    public String getContactHistory() {
        return contactHistory;
    }

    public void setContactHistory(String contactHistory) {
        this.contactHistory = contactHistory;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }

    public BigDecimal getAmount() {
        return currentAmount;
    }

    public void setAmount(BigDecimal amount) {
        this.currentAmount = amount;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public void setOriginalAmount(BigDecimal originalAmount) {
        this.originalAmount = originalAmount;
    }

    public LocalDate getDateOfDefault() {
        return dateOfDefault;
    }

    public void setDateOfDefault(LocalDate dateOfDefault) {
        this.dateOfDefault = dateOfDefault;
    }

    public LocalDate getDateOfService() {
        return dateOfService;
    }

    public void setDateOfService(LocalDate dateOfService) {
        this.dateOfService = dateOfService;
    }

    public LocalDate getLastPaymentDate() {
        return lastPaymentDate;
    }

    public void setLastPaymentDate(LocalDate lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public String getContractFileKey() {
        return contractFileKey;
    }

    public void setContractFileKey(String contractFileKey) {
        this.contractFileKey = contractFileKey;
    }
}
