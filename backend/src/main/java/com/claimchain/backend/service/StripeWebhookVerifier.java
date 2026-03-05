package com.claimchain.backend.service;

public interface StripeWebhookVerifier {

    VerifiedStripeEvent verifyAndConstructEvent(String payload, String stripeSignatureHeader);

    class VerifiedStripeEvent {
        private final String eventId;
        private final String eventType;

        public VerifiedStripeEvent(String eventId, String eventType) {
            this.eventId = eventId;
            this.eventType = eventType;
        }

        public String getEventId() {
            return eventId;
        }

        public String getEventType() {
            return eventType;
        }
    }
}
