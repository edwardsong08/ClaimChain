package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RejectUserRequestDTO {

    @NotBlank
    @Size(max = 500)
    private String reason;

    public RejectUserRequestDTO() {}

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
