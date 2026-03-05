package com.claimchain.backend.service;

import com.claimchain.backend.config.StripeProperties;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.stereotype.Service;

@Service
public class StripeWebhookVerifierImpl implements StripeWebhookVerifier {

    private final StripeProperties stripeProperties;

    public StripeWebhookVerifierImpl(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    @Override
    public VerifiedStripeEvent verifyAndConstructEvent(String payload, String stripeSignatureHeader) {
        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook payload is blank.");
        }
        if (stripeSignatureHeader == null || stripeSignatureHeader.isBlank()) {
            throw new IllegalArgumentException("Missing Stripe-Signature header.");
        }

        try {
            Event event = Webhook.constructEvent(payload, stripeSignatureHeader, stripeProperties.requireWebhookSecret());
            return new VerifiedStripeEvent(event.getId(), event.getType());
        } catch (SignatureVerificationException e) {
            throw new IllegalArgumentException("Invalid Stripe webhook signature.");
        }
    }
}