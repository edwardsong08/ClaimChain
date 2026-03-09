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
    private String debtorAddress;
    private String debtorType;
    private String jurisdictionState;
    private String claimType;
    private String disputeStatus;

    // Client fields
    private String clientName;
    private String clientContact;
    private String clientAddress;

    // Claim metadata
    private String debtType;
    private String contactHistory;
    private BigDecimal originalAmount;
    private BigDecimal currentAmount;
    private BigDecimal amount;
    private String dateOfDefault;
    private String lastPaymentDate;
    private String contractFileKey;

    // Other
    private String serviceDescription;
    private String status;
    private String submittedAt;
    private String submittedBy;

    // Latest score (if available)
    private Boolean eligible;
    private Integer scoreTotal;
    private String grade;
    private Integer subscoreEnforceability;
    private Integer subscoreDocumentation;
    private Integer subscoreCollectability;
    private Integer subscoreOperationalRisk;
    private Double extractionSuccessRate;
    private String explainabilityJson;
    private String featureSnapshotJson;
    private String scoredAt;
    private String scoreTrigger;
}
