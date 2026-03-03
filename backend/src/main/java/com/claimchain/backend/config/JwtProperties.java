package com.claimchain.backend.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "security.jwt")
public class JwtProperties {

    @NotBlank
    private String secret;

    @Min(60_000) // minimum 1 minute
    private long expirationMillis;

    @Min(60_000) // minimum 1 minute
    private long refreshExpirationMillis;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }

    public void setExpirationMillis(long expirationMillis) {
        this.expirationMillis = expirationMillis;
    }

    public long getRefreshExpirationMillis() {
        return refreshExpirationMillis;
    }

    public void setRefreshExpirationMillis(long refreshExpirationMillis) {
        this.refreshExpirationMillis = refreshExpirationMillis;
    }
}