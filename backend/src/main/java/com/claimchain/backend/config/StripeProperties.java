package com.claimchain.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "claimchain.stripe")
public class StripeProperties {

    private String secretKey = "";
    private String webhookSecret = "";
    private String successUrl = "";
    private String cancelUrl = "";

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public String getSuccessUrl() {
        return successUrl;
    }

    public void setSuccessUrl(String successUrl) {
        this.successUrl = successUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public String requireSecretKey() {
        return requireNonBlank(secretKey, "claimchain.stripe.secretKey");
    }

    public String requireWebhookSecret() {
        return requireNonBlank(webhookSecret, "claimchain.stripe.webhookSecret");
    }

    public String requireSuccessUrl() {
        return requireNonBlank(successUrl, "claimchain.stripe.successUrl");
    }

    public String requireCancelUrl() {
        return requireNonBlank(cancelUrl, "claimchain.stripe.cancelUrl");
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(fieldName + " must be configured.");
        }
        return value.trim();
    }
}
