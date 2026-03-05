package com.claimchain.backend.controller;

import com.claimchain.backend.service.PurchaseService;
import com.claimchain.backend.service.StripeWebhookVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/stripe")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookVerifier stripeWebhookVerifier;
    private final PurchaseService purchaseService;

    public StripeWebhookController(
            StripeWebhookVerifier stripeWebhookVerifier,
            PurchaseService purchaseService
    ) {
        this.stripeWebhookVerifier = stripeWebhookVerifier;
        this.purchaseService = purchaseService;
    }

    @PostMapping
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String stripeSignatureHeader
    ) {
        StripeWebhookVerifier.VerifiedStripeEvent verifiedEvent;
        try {
            verifiedEvent = stripeWebhookVerifier.verifyAndConstructEvent(payload, stripeSignatureHeader);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        try {
            purchaseService.processVerifiedWebhookEvent(verifiedEvent, payload);
        } catch (Exception ex) {
            log.warn("Stripe webhook processing failed for eventId={}", verifiedEvent.getEventId());
        }

        return ResponseEntity.ok().build();
    }
}
