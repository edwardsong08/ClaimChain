package com.claimchain.backend.dto;

public class RegisterRequest {

    private String name;
    private String email;
    private String password;
    private String role; // Accept "SERVICE_PROVIDER" or "COLLECTION_AGENCY"
    private String businessName;

    // Optional for now – can be completed later for verification
    private String phone;
    private String address;
    private String einOrLicense;
    private String businessType;

    public RegisterRequest() {}

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEinOrLicense() { return einOrLicense; }
    public void setEinOrLicense(String einOrLicense) { this.einOrLicense = einOrLicense; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
}
