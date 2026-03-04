package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;

public class RulesetRequestDTO {

    @NotBlank(message = "configJson is required")
    private String configJson;

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }
}
