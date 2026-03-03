package com.claimchain.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AdminBootstrapRequestDTO {

    @NotBlank
    private String bootstrapToken;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 10, max = 72)
    private String password;

    public AdminBootstrapRequestDTO() {}

    public String getBootstrapToken() {
        return bootstrapToken;
    }

    public void setBootstrapToken(String bootstrapToken) {
        this.bootstrapToken = bootstrapToken;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
