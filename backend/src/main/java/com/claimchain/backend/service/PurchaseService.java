package com.claimchain.backend.service;

import com.claimchain.backend.config.StripeProperties;
import com.claimchain.backend.model.BuyerEntitlement;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.PurchaseEvent;
import com.claimchain.backend.model.PurchaseStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.BuyerEntitlementRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.PurchaseEventRepository;
import com.claimchain.backend.repository.PurchaseRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.StripeException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final PurchaseEventRepository purchaseEventRepository;
    private final PackageRepository packageRepository;
    private final BuyerEntitlementRepository buyerEntitlementRepository;
    private final UserRepository userRepository;
    private final StripeClientService stripeClientService;
    private final StripeProperties stripeProperties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            PurchaseEventRepository purchaseEventRepository,
            PackageRepository packageRepository,
            BuyerEntitlementRepository buyerEntitlementRepository,
            UserRepository userRepository,
            StripeClientService stripeClientService,
            StripeProperties stripeProperties,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseEventRepository = purchaseEventRepository;
        this.packageRepository = packageRepository;
        this.buyerEntitlementRepository = buyerEntitlementRepository;
        this.userRepository = userRepository;
        this.stripeClientService = stripeClientService;
        this.stripeProperties = stripeProperties;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CheckoutResult createCheckout(Long packageId, Long buyerUserId, String idempotencyKeyRaw) {
        User buyer = requireBuyerById(buyerUserId);
        Package packageEntity = requireListedPackage(packageId);
        requirePackagePriced(packageEntity);

        if (buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(packageEntity.getId(), buyer.getId())) {
            throw new PackageService.PackageConflictException("ALREADY_ENTITLED", "Buyer already has access to this package.");
        }

        String idempotencyKey = normalizeIdempotencyKey(idempotencyKeyRaw);
        if (idempotencyKey != null) {
            Purchase existing = purchaseRepository
                    .findByBuyerUserIdAndIdempotencyKey(buyer.getId(), idempotencyKey)
                    .orElse(null);
            if (existing != null) {
                return checkoutFromExistingIfReusable(existing, packageEntity.getId());
            }
        }

        Purchase purchase = new Purchase();
        purchase.setPackageEntity(packageEntity);
        purchase.setBuyerUser(buyer);
        purchase.setStatus(PurchaseStatus.PENDING);
        purchase.setAmountCents(packageEntity.getPriceCents());
        purchase.setCurrency(resolveCurrency(packageEntity));
        purchase.setIdempotencyKey(idempotencyKey);

        Purchase savedPurchase;
        try {
            savedPurchase = purchaseRepository.save(purchase);
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                Purchase existing = purchaseRepository
                        .findByBuyerUserIdAndIdempotencyKey(buyer.getId(), idempotencyKey)
                        .orElse(null);
                if (existing != null) {
                    return checkoutFromExistingIfReusable(existing, packageEntity.getId());
                }
            }
            throw ex;
        }

        StripeClientService.CheckoutSessionResult session;
        try {
            session = stripeClientService.createCheckoutSession(
                    savedPurchase,
                    packageEntity,
                    buyer,
                    stripeProperties.requireSuccessUrl(),
                    stripeProperties.requireCancelUrl()
            );
        } catch (StripeException ex) {
            throw new IllegalStateException("Failed to create Stripe Checkout session.", ex);
        }

        savedPurchase.setStripeCheckoutSessionId(session.getSessionId());
        purchaseRepository.save(savedPurchase);

        return new CheckoutResult(savedPurchase.getId(), session.getSessionId(), session.getCheckoutUrl());
    }

    @Transactional
    public void processVerifiedWebhookEvent(StripeWebhookVerifier.VerifiedStripeEvent verifiedEvent, String payloadJson) {
        if (verifiedEvent == null || verifiedEvent.getEventId() == null || verifiedEvent.getEventId().isBlank()) {
            return;
        }
        if (purchaseEventRepository.existsByStripeEventId(verifiedEvent.getEventId())) {
            return;
        }

        Purchase purchase = resolvePurchaseFromPayload(verifiedEvent.getEventType(), payloadJson);

        PurchaseEvent purchaseEvent = new PurchaseEvent();
        purchaseEvent.setStripeEventId(verifiedEvent.getEventId());
        purchaseEvent.setEventType(verifiedEvent.getEventType());
        purchaseEvent.setPayloadJson(payloadJson == null ? "{}" : payloadJson);
        purchaseEvent.setPurchase(purchase);
        try {
            purchaseEventRepository.save(purchaseEvent);
        } catch (DataIntegrityViolationException ex) {
            // Event replay race: another transaction already persisted this event id.
            return;
        }

        if (!"checkout.session.completed".equals(verifiedEvent.getEventType()) || purchase == null) {
            return;
        }

        if (purchase.getStatus() == PurchaseStatus.PAID) {
            return;
        }

        String paymentIntentId = extractText(payloadJson, "data", "object", "payment_intent");
        purchase.setStatus(PurchaseStatus.PAID);
        if (paymentIntentId != null && !paymentIntentId.isBlank()) {
            purchase.setStripePaymentIntentId(paymentIntentId);
        }
        purchaseRepository.save(purchase);

        Package packageEntity = purchase.getPackageEntity();
        User buyer = purchase.getBuyerUser();

        boolean entitlementCreated = false;
        if (!buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(packageEntity.getId(), buyer.getId())) {
            BuyerEntitlement entitlement = new BuyerEntitlement();
            entitlement.setPackageEntity(packageEntity);
            entitlement.setBuyerUser(buyer);
            try {
                buyerEntitlementRepository.save(entitlement);
                entitlementCreated = true;
            } catch (DataIntegrityViolationException ex) {
                entitlementCreated = false;
            }
        }

        boolean packageSoldTransitioned = false;
        if (packageEntity.getStatus() == PackageStatus.LISTED) {
            packageEntity.setStatus(PackageStatus.SOLD);
            packageRepository.save(packageEntity);
            packageSoldTransitioned = true;
        }

        recordPurchasePaidAudit(purchase);
        if (entitlementCreated) {
            recordEntitlementGrantedAudit(packageEntity, buyer, purchase);
        }
        if (packageSoldTransitioned) {
            recordPackageSoldAudit(packageEntity, purchase);
        }
    }

    private CheckoutResult checkoutFromExistingIfReusable(Purchase existingPurchase, Long requestedPackageId) {
        Long existingPackageId = existingPurchase.getPackageEntity() == null
                ? null
                : existingPurchase.getPackageEntity().getId();
        if (existingPurchase.getStatus() == PurchaseStatus.PENDING
                && existingPackageId != null
                && existingPackageId.equals(requestedPackageId)) {
            String sessionId = existingPurchase.getStripeCheckoutSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                try {
                    StripeClientService.CheckoutSessionResult replacementSession = stripeClientService.createCheckoutSession(
                            existingPurchase,
                            existingPurchase.getPackageEntity(),
                            existingPurchase.getBuyerUser(),
                            stripeProperties.requireSuccessUrl(),
                            stripeProperties.requireCancelUrl()
                    );
                    existingPurchase.setStripeCheckoutSessionId(replacementSession.getSessionId());
                    purchaseRepository.save(existingPurchase);
                    return new CheckoutResult(
                            existingPurchase.getId(),
                            replacementSession.getSessionId(),
                            replacementSession.getCheckoutUrl()
                    );
                } catch (StripeException ex) {
                    throw new IllegalStateException("Failed to create Stripe Checkout session.", ex);
                }
            }
            String checkoutUrl;
            try {
                checkoutUrl = stripeClientService.getCheckoutSessionUrl(sessionId);
            } catch (StripeException ex) {
                throw new IllegalStateException("Failed to retrieve Stripe Checkout session.", ex);
            }
            return new CheckoutResult(existingPurchase.getId(), sessionId, checkoutUrl);
        }

        throw new PackageService.PackageConflictException(
                "IDEMPOTENCY_KEY_REUSE",
                "Idempotency key already used for a different purchase."
        );
    }

    private User requireBuyerById(Long buyerUserId) {
        if (buyerUserId == null) {
            throw new PackageService.PackageValidationException("BUYER_REQUIRED", "Buyer user is required.");
        }
        User buyer = userRepository.findById(buyerUserId)
                .orElseThrow(() -> new PackageService.PackageValidationException("BUYER_REQUIRED", "Buyer user not found."));
        if (buyer.getRole() != Role.COLLECTION_AGENCY) {
            throw new PackageService.PackageValidationException("BUYER_REQUIRED", "Buyer role required.");
        }
        return buyer;
    }

    private Package requireListedPackage(Long packageId) {
        if (packageId == null) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }
        Package packageEntity = packageRepository.findById(packageId)
                .orElseThrow(() -> new PackageService.PackageNotFoundException("Package not found."));
        if (packageEntity.getStatus() != PackageStatus.LISTED) {
            throw new PackageService.PackageNotFoundException("Package not found.");
        }
        return packageEntity;
    }

    private void requirePackagePriced(Package packageEntity) {
        Long priceCents = packageEntity.getPriceCents();
        if (priceCents == null || priceCents <= 0L) {
            throw new PackageService.PackageConflictException(
                    "PACKAGE_NOT_PRICED",
                    "Package is not priced for checkout."
            );
        }
    }

    private String resolveCurrency(Package packageEntity) {
        String currency = packageEntity.getCurrency();
        if (currency == null || currency.trim().isEmpty()) {
            return "usd";
        }
        return currency.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIdempotencyKey(String idempotencyKeyRaw) {
        if (idempotencyKeyRaw == null) {
            return null;
        }
        String normalized = idempotencyKeyRaw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 100) {
            throw new PackageService.PackageValidationException(
                    "IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be <= 100 characters."
            );
        }
        return normalized;
    }

    private Purchase resolvePurchaseFromPayload(String eventType, String payloadJson) {
        if (!"checkout.session.completed".equals(eventType)) {
            return null;
        }
        String sessionId = extractText(payloadJson, "data", "object", "id");
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return purchaseRepository.findByStripeCheckoutSessionId(sessionId).orElse(null);
    }

    private String extractText(String payloadJson, String... path) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payloadJson);
            for (String field : path) {
                node = node.path(field);
            }
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            String value = node.asText(null);
            return value == null || value.isBlank() ? null : value;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void recordPurchasePaidAudit(Purchase purchase) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purchaseId", purchase.getId());
        metadata.put("packageId", purchase.getPackageEntity() == null ? null : purchase.getPackageEntity().getId());
        metadata.put("buyerUserId", purchase.getBuyerUser() == null ? null : purchase.getBuyerUser().getId());
        metadata.put("amountCents", purchase.getAmountCents());
        metadata.put("currency", purchase.getCurrency());
        metadata.put("stripeCheckoutSessionId", purchase.getStripeCheckoutSessionId());
        metadata.put("stripePaymentIntentId", purchase.getStripePaymentIntentId());

        auditService.record(
                null,
                "SYSTEM",
                "PURCHASE_PAID",
                "PURCHASE",
                purchase.getId(),
                toJson(metadata)
        );
    }

    private void recordEntitlementGrantedAudit(Package packageEntity, User buyer, Purchase purchase) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purchaseId", purchase.getId());
        metadata.put("packageId", packageEntity.getId());
        metadata.put("buyerUserId", buyer.getId());
        metadata.put("grantedAt", Instant.now());

        auditService.record(
                null,
                "SYSTEM",
                "ENTITLEMENT_GRANTED",
                "PACKAGE",
                packageEntity.getId(),
                toJson(metadata)
        );
    }

    private void recordPackageSoldAudit(Package packageEntity, Purchase purchase) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("purchaseId", purchase.getId());
        metadata.put("packageId", packageEntity.getId());
        metadata.put("fromStatus", PackageStatus.LISTED.name());
        metadata.put("toStatus", PackageStatus.SOLD.name());

        auditService.record(
                null,
                "SYSTEM",
                "PACKAGE_SOLD",
                "PACKAGE",
                packageEntity.getId(),
                toJson(metadata)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize purchase audit metadata.", ex);
        }
    }

    public static class CheckoutResult {
        private final Long purchaseId;
        private final String checkoutSessionId;
        private final String checkoutUrl;

        public CheckoutResult(Long purchaseId, String checkoutSessionId, String checkoutUrl) {
            this.purchaseId = purchaseId;
            this.checkoutSessionId = checkoutSessionId;
            this.checkoutUrl = checkoutUrl;
        }

        public Long getPurchaseId() {
            return purchaseId;
        }

        public String getCheckoutSessionId() {
            return checkoutSessionId;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }
    }
}