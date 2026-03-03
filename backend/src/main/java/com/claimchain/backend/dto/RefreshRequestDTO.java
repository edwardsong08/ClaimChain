package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class RefreshRequestDTO {

    @NotBlank
    private String refreshToken;

    public RefreshRequestDTO() {}

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}