package com.claimchain.backend.dto;

import lombok.Data;

@Data
public class ClaimResponseDTO {
    private Long id;
    private String debtorName;
    private String debtorEmail;
    private String debtorPhone;
    private String clientName;        // ✅ newly added
    private String clientContact;     // ✅ newly added
    private String serviceDescription;
    private Double amountOwed;
    private String dateOfService;
    private String status;
    private String submittedAt;
    private String submittedBy;       // ✅ newly added
}
