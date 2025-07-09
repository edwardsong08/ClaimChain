package com.claimchain.backend.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public class ClaimRequestDTO {

    @NotBlank
    private String clientName;

    @NotBlank
    private String clientContact;

    @NotNull
    @DecimalMin(value = "0.01", inclusive = true)
    private BigDecimal amount;

    @NotNull
    private LocalDate dateOfDefault;

    @NotBlank
    private String debtType;

    @NotBlank
    private String clientAddress;

    @NotBlank
    private String contactHistory;

    // Scaffold for tomorrow — this can be used later to link uploaded file
    private String contractFileKey;

    public ClaimRequestDTO() {}

    // Getters and Setters

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientContact() { return clientContact; }
    public void setClientContact(String clientContact) { this.clientContact = clientContact; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getDateOfDefault() { return dateOfDefault; }
    public void setDateOfDefault(LocalDate dateOfDefault) { this.dateOfDefault = dateOfDefault; }

    public String getDebtType() { return debtType; }
    public void setDebtType(String debtType) { this.debtType = debtType; }

    public String getClientAddress() { return clientAddress; }
    public void setClientAddress(String clientAddress) { this.clientAddress = clientAddress; }

    public String getContactHistory() { return contactHistory; }
    public void setContactHistory(String contactHistory) { this.contactHistory = contactHistory; }

    public String getContractFileKey() { return contractFileKey; }
    public void setContractFileKey(String contractFileKey) { this.contractFileKey = contractFileKey; }
}
