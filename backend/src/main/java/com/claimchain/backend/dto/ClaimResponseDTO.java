package com.claimchain.backend.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClaimResponseDTO {
    private Long id;

    // Debtor fields (optional/legacy)
    private String debtorName;
    private String debtorEmail;
    private String debtorPhone;

    // Client fields
    private String clientName;
    private String clientContact;
    private String clientAddress;

    // Claim metadata
    private String debtType;
    private String contactHistory;
    private BigDecimal amount;
    private String dateOfDefault;
    private String contractFileKey;

    // Other
    private String serviceDescription;
    private String status;
    private String submittedAt;
    private String submittedBy;
}
