package com.claimchain.backend.service;

import com.claimchain.backend.config.StripeProperties;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.User;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class StripeClientService {

    private final StripeProperties stripeProperties;

    public StripeClientService(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    public CheckoutSessionResult createCheckoutSession(
            Purchase purchase,
            Package pkg,
            User buyer,
            String successUrl,
            String cancelUrl
    ) throws StripeException {
        applyApiKey();
        String resolvedSuccessUrl = ensureSessionIdPlaceholder(successUrl);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(resolvedSuccessUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(resolveCurrency(pkg))
                                                .setUnitAmount(pkg.getPriceCents())
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("ClaimChain Package #" + pkg.getId())
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("purchaseId", String.valueOf(purchase.getId()))
                .putMetadata("packageId", String.valueOf(pkg.getId()))
                .putMetadata("buyerUserId", String.valueOf(buyer.getId()))
                .build();

        Session session = Session.create(params);
        return new CheckoutSessionResult(session.getId(), session.getUrl());
    }

    public String getCheckoutSessionUrl(String checkoutSessionId) throws StripeException {
        applyApiKey();
        Session session = Session.retrieve(checkoutSessionId);
        return session.getUrl();
    }

    private String ensureSessionIdPlaceholder(String successUrl) {
        if (successUrl.contains("{CHECKOUT_SESSION_ID}")) {
            return successUrl;
        }
        return successUrl + (successUrl.contains("?") ? "&" : "?") + "session_id={CHECKOUT_SESSION_ID}";
    }

    private void applyApiKey() {
        Stripe.apiKey = stripeProperties.requireSecretKey();
    }

    private String resolveCurrency(Package pkg) {
        String currency = pkg.getCurrency();
        if (currency == null || currency.trim().isEmpty()) {
            return "usd";
        }
        return currency.trim().toLowerCase(Locale.ROOT);
    }

    public static class CheckoutSessionResult {
        private final String sessionId;
        private final String checkoutUrl;

        public CheckoutSessionResult(String sessionId, String checkoutUrl) {
            this.sessionId = sessionId;
            this.checkoutUrl = checkoutUrl;
        }

        public String getSessionId() {
            return sessionId;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }
    }
}
