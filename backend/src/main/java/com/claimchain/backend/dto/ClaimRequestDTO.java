package com.claimchain.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ClaimRequestDTO {

    @NotBlank
    private String debtorName;

    @Email
    @NotBlank
    private String debtorEmail;

    @NotBlank
    private String debtorPhone;

    @NotBlank
    private String serviceDescription;

    @NotBlank
    private String amountOwed;

    @NotBlank
    private String dateOfService;

    // Getters and Setters
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

    public String getServiceDescription() {
        return serviceDescription;
    }

    public void setServiceDescription(String serviceDescription) {
        this.serviceDescription = serviceDescription;
    }

    public String getAmountOwed() {
        return amountOwed;
    }

    public void setAmountOwed(String amountOwed) {
        this.amountOwed = amountOwed;
    }

    public String getDateOfService() {
        return dateOfService;
    }

    public void setDateOfService(String dateOfService) {
        this.dateOfService = dateOfService;
    }
}
