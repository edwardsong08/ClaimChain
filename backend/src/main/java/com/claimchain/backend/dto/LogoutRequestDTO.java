package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class LogoutRequestDTO {

    @NotBlank
    private String refreshToken;

    public LogoutRequestDTO() {}

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}