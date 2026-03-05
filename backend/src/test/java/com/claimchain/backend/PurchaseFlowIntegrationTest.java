package com.claimchain.backend;

import com.claimchain.backend.model.BuyerEntitlement;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.PurchaseStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.BuyerEntitlementRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.PurchaseRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.service.StripeClientService;
import com.claimchain.backend.service.StripeWebhookVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PurchaseFlowIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@purchase-flow-test.local";
    private static final String BUYER_EMAIL = "buyer@purchase-flow-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@purchase-flow-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private BuyerEntitlementRepository buyerEntitlementRepository;

    @MockitoBean
    private StripeClientService stripeClientService;

    @MockitoBean
    private StripeWebhookVerifier stripeWebhookVerifier;

    private User adminUser;
    private User buyerUser;
    private String buyerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();
        adminUser = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        buyerUser = createUser(BUYER_EMAIL, Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);
        buyerToken = loginAndGetAccessToken(BUYER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void buyerCheckoutCreatesPendingPurchaseAndReturnsSession() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, 125_000L, "usd");
        when(stripeClientService.createCheckoutSession(any(Purchase.class), any(Package.class), any(User.class), anyString(), anyString()))
                .thenReturn(new StripeClientService.CheckoutSessionResult("cs_test_happy", "https://checkout.stripe.test/happy"));

        MvcResult result = mockMvc.perform(
                        post("/api/buyer/packages/{id}/checkout", listedPackage.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.purchaseId").isNumber())
                .andExpect(jsonPath("$.checkoutSessionId").value("cs_test_happy"))
                .andExpect(jsonPath("$.checkoutUrl").value("https://checkout.stripe.test/happy"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Long purchaseId = body.get("purchaseId").asLong();
        Purchase savedPurchase = purchaseRepository.findById(purchaseId).orElseThrow();
        assertThat(savedPurchase.getStatus()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(savedPurchase.getPackageEntity().getId()).isEqualTo(listedPackage.getId());
        assertThat(savedPurchase.getBuyerUser().getId()).isEqualTo(buyerUser.getId());
        assertThat(savedPurchase.getStripeCheckoutSessionId()).isEqualTo("cs_test_happy");
        assertThat(savedPurchase.getAmountCents()).isEqualTo(125_000L);
        assertThat(savedPurchase.getCurrency()).isEqualTo("usd");
    }

    @Test
    void checkoutIdempotencyReturnsSamePurchaseAndSession() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, 99_500L, "usd");
        when(stripeClientService.createCheckoutSession(any(Purchase.class), any(Package.class), any(User.class), anyString(), anyString()))
                .thenReturn(new StripeClientService.CheckoutSessionResult("cs_test_idem", "https://checkout.stripe.test/idem"));
        when(stripeClientService.getCheckoutSessionUrl("cs_test_idem"))
                .thenReturn("https://checkout.stripe.test/idem");

        String idempotencyKey = "idem-checkout-key";
        MvcResult firstResult = mockMvc.perform(
                        post("/api/buyer/packages/{id}/checkout", listedPackage.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                                .header("Idempotency-Key", idempotencyKey)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        MvcResult secondResult = mockMvc.perform(
                        post("/api/buyer/packages/{id}/checkout", listedPackage.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                                .header("Idempotency-Key", idempotencyKey)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(secondResult.getResponse().getContentAsString());
        assertThat(secondBody.get("purchaseId").asLong()).isEqualTo(firstBody.get("purchaseId").asLong());
        assertThat(secondBody.get("checkoutSessionId").asText()).isEqualTo(firstBody.get("checkoutSessionId").asText());
        assertThat(secondBody.get("checkoutUrl").asText()).isEqualTo(firstBody.get("checkoutUrl").asText());

        Integer purchaseCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchases", Integer.class);
        assertThat(purchaseCount).isEqualTo(1);
        verify(stripeClientService, times(1)).createCheckoutSession(any(Purchase.class), any(Package.class), any(User.class), anyString(), anyString());
        verify(stripeClientService, times(1)).getCheckoutSessionUrl("cs_test_idem");
    }

    @Test
    void checkoutFailsWhenPackageIsNotPriced() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, null, "usd");

        mockMvc.perform(
                        post("/api/buyer/packages/{id}/checkout", listedPackage.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PACKAGE_NOT_PRICED"));
    }

    @Test
    void checkoutFailsWhenBuyerAlreadyEntitled() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, 77_700L, "usd");
        BuyerEntitlement entitlement = new BuyerEntitlement();
        entitlement.setPackageEntity(listedPackage);
        entitlement.setBuyerUser(buyerUser);
        buyerEntitlementRepository.saveAndFlush(entitlement);

        mockMvc.perform(
                        post("/api/buyer/packages/{id}/checkout", listedPackage.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ALREADY_ENTITLED"));
    }

    @Test
    void webhookCompletionMarksPurchasePaidGrantsEntitlementAndSellsPackage() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, 110_000L, "usd");
        Purchase purchase = createPendingPurchase(listedPackage, buyerUser, "cs_webhook_complete");

        when(stripeWebhookVerifier.verifyAndConstructEvent(anyString(), anyString()))
                .thenReturn(new StripeWebhookVerifier.VerifiedStripeEvent("evt_complete_1", "checkout.session.completed"));

        String payload = """
                {
                  "id": "evt_payload_1",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_webhook_complete",
                      "payment_intent": "pi_complete_1"
                    }
                  }
                }
                """;

        mockMvc.perform(
                        post("/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=1,v1=test-signature")
                                .content(payload)
                )
                .andExpect(status().isOk());

        Purchase savedPurchase = purchaseRepository.findById(purchase.getId()).orElseThrow();
        assertThat(savedPurchase.getStatus()).isEqualTo(PurchaseStatus.PAID);
        assertThat(savedPurchase.getStripePaymentIntentId()).isEqualTo("pi_complete_1");
        assertThat(buyerEntitlementRepository.existsByPackageEntityIdAndBuyerUserId(listedPackage.getId(), buyerUser.getId())).isTrue();

        Package updatedPackage = packageRepository.findById(listedPackage.getId()).orElseThrow();
        assertThat(updatedPackage.getStatus()).isEqualTo(PackageStatus.SOLD);

        Integer purchaseEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_events WHERE stripe_event_id = ?",
                Integer.class,
                "evt_complete_1"
        );
        assertThat(purchaseEventCount).isEqualTo(1);
        assertAuditCount("PURCHASE_PAID", "PURCHASE", purchase.getId(), 1);
        assertAuditCount("ENTITLEMENT_GRANTED", "PACKAGE", listedPackage.getId(), 1);
        assertAuditCount("PACKAGE_SOLD", "PACKAGE", listedPackage.getId(), 1);
    }

    @Test
    void webhookReplayIsDedupedWithoutDuplicateEntitlementOrAuditEvents() throws Exception {
        Package listedPackage = createPackage(adminUser, PackageStatus.LISTED, 89_000L, "usd");
        Purchase purchase = createPendingPurchase(listedPackage, buyerUser, "cs_webhook_replay");

        when(stripeWebhookVerifier.verifyAndConstructEvent(anyString(), anyString()))
                .thenReturn(new StripeWebhookVerifier.VerifiedStripeEvent("evt_replay_1", "checkout.session.completed"));

        String payload = """
                {
                  "id": "evt_payload_replay",
                  "type": "checkout.session.completed",
                  "data": {
                    "object": {
                      "id": "cs_webhook_replay",
                      "payment_intent": "pi_replay_1"
                    }
                  }
                }
                """;

        mockMvc.perform(
                        post("/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=1,v1=test-signature")
                                .content(payload)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/webhooks/stripe")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Stripe-Signature", "t=1,v1=test-signature")
                                .content(payload)
                )
                .andExpect(status().isOk());

        Integer purchaseEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_events WHERE stripe_event_id = ?",
                Integer.class,
                "evt_replay_1"
        );
        assertThat(purchaseEventCount).isEqualTo(1);

        Integer entitlementCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM buyer_entitlements WHERE package_id = ? AND buyer_user_id = ?",
                Integer.class,
                listedPackage.getId(),
                buyerUser.getId()
        );
        assertThat(entitlementCount).isEqualTo(1);

        assertAuditCount("PURCHASE_PAID", "PURCHASE", purchase.getId(), 1);
        assertAuditCount("ENTITLEMENT_GRANTED", "PACKAGE", listedPackage.getId(), 1);
        assertAuditCount("PACKAGE_SOLD", "PACKAGE", listedPackage.getId(), 1);
    }

    private Package createPackage(User createdBy, PackageStatus status, Long priceCents, String currency) {
        Package packageEntity = new Package();
        packageEntity.setStatus(status);
        packageEntity.setCreatedByUser(createdBy);
        packageEntity.setTotalClaims(1);
        packageEntity.setTotalFaceValue(new BigDecimal("1000.00"));
        packageEntity.setPriceCents(priceCents);
        packageEntity.setCurrency(currency);
        packageEntity.setNotes("purchase-flow-test");
        return packageRepository.saveAndFlush(packageEntity);
    }

    private Purchase createPendingPurchase(Package packageEntity, User buyer, String checkoutSessionId) {
        Purchase purchase = new Purchase();
        purchase.setPackageEntity(packageEntity);
        purchase.setBuyerUser(buyer);
        purchase.setStatus(PurchaseStatus.PENDING);
        purchase.setAmountCents(packageEntity.getPriceCents());
        purchase.setCurrency(packageEntity.getCurrency());
        purchase.setStripeCheckoutSessionId(checkoutSessionId);
        return purchaseRepository.saveAndFlush(purchase);
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Purchase Flow Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setVerificationStatus(verificationStatus);
        if (verificationStatus == VerificationStatus.APPROVED) {
            user.setVerifiedAt(Instant.now());
        }
        return userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private void assertAuditCount(String action, String entityType, Long entityId, int expectedCount) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = ? AND entity_type = ? AND entity_id = ?",
                Integer.class,
                action,
                entityType,
                entityId
        );
        assertThat(count).isEqualTo(expectedCount);
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM purchase_events");
        jdbcTemplate.update("DELETE FROM purchases");
        jdbcTemplate.update("DELETE FROM anonymized_claim_views");
        jdbcTemplate.update("DELETE FROM buyer_entitlements");
        jdbcTemplate.update("DELETE FROM package_claims");
        jdbcTemplate.update("DELETE FROM packages");
        jdbcTemplate.update("DELETE FROM document_jobs");
        jdbcTemplate.update("DELETE FROM claim_documents");
        jdbcTemplate.update("DELETE FROM claim_scores");
        jdbcTemplate.update("DELETE FROM rulesets");
        jdbcTemplate.update("DELETE FROM audit_events");

        jdbcTemplate.update(
                "UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", TEST_EMAIL_PATTERN);
    }
}