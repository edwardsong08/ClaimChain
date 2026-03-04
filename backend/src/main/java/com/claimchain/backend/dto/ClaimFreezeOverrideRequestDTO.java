package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ClaimFreezeOverrideRequestDTO {

    @NotBlank
    @Size(max = 1000)
    private String reason;

    public ClaimFreezeOverrideRequestDTO() {
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
