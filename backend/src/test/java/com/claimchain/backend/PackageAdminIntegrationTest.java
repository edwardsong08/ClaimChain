package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.Package;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Purchase;
import com.claimchain.backend.model.PurchaseStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.PackageRepository;
import com.claimchain.backend.repository.PurchaseRepository;
import com.claimchain.backend.repository.UserRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackageAdminIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@package-admin-test.local";
    private static final String PROVIDER_EMAIL = "provider@package-admin-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@package-admin-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private PackageRepository packageRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String providerToken;
    private User providerUser;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        providerUser = createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);

        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void adminCanCreateDraftPackage() throws Exception {
        mockMvc.perform(
                        post("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "notes":"Initial package draft"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalClaims").value(0))
                .andExpect(jsonPath("$.totalFaceValue").value(0))
                .andExpect(jsonPath("$.price").isEmpty());
    }

    @Test
    void addApprovedClaimUpdatesTotalsAndClaimStatus_andDuplicateAddConflicts() throws Exception {
        Long packageId = createDraftPackage("Step 8.1 package");
        Long claimId = createApprovedClaim(providerUser, new BigDecimal("275.50"), null).getId();

        mockMvc.perform(
                        post("/api/admin/packages/{id}/claims/{claimId}", packageId, claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        Package savedPackage = packageRepository.findByIdWithClaims(packageId).orElseThrow();
        assertThat(savedPackage.getTotalClaims()).isEqualTo(1);
        assertThat(savedPackage.getTotalFaceValue()).isEqualByComparingTo("275.50");

        Claim savedClaim = claimRepository.findById(claimId).orElseThrow();
        assertThat(savedClaim.getStatus()).isEqualTo(ClaimStatus.PACKAGED);

        mockMvc.perform(
                        get("/api/admin/packages/{id}", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(packageId))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.totalClaims").value(1))
                .andExpect(jsonPath("$.totalFaceValue").value(275.50))
                .andExpect(jsonPath("$.claimIds[0]").value(claimId));

        MvcResult duplicateAddResult = mockMvc.perform(
                        post("/api/admin/packages/{id}/claims/{claimId}", packageId, claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(duplicateAddResult, "PACKAGE_CLAIM_DUPLICATE");

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'PACKAGE_CLAIM_ADDED' AND entity_type = 'PACKAGE' AND entity_id = ?",
                Integer.class,
                packageId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void nonAdminCannotCreateOrAddPackageClaims() throws Exception {
        Long packageId = createDraftPackage("forbidden test");
        Long claimId = createApprovedClaim(providerUser, new BigDecimal("100.00"), new BigDecimal("100.00")).getId();

        MvcResult createForbidden = mockMvc.perform(
                        post("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(createForbidden, "FORBIDDEN");

        MvcResult addForbidden = mockMvc.perform(
                        post("/api/admin/packages/{id}/claims/{claimId}", packageId, claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(addForbidden, "FORBIDDEN");
    }

    @Test
    void adminCanSetPackagePrice_forReadyPackage_andAdminListDetailIncludeUpdatedPrice() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.READY);

        mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "price": 199.99
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(packageId))
                .andExpect(jsonPath("$.price").value(199.99));

        Package savedPackage = packageRepository.findById(packageId).orElseThrow();
        assertThat(savedPackage.getPriceCents()).isEqualTo(19999L);

        mockMvc.perform(
                        get("/api/admin/packages/{id}", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(packageId))
                .andExpect(jsonPath("$.price").value(199.99));

        mockMvc.perform(
                        get("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(packageId))
                .andExpect(jsonPath("$[0].price").value(199.99));
    }

    @Test
    void adminCanSetPackagePrice_forUnlistedPackage() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.READY);

        mockMvc.perform(
                        post("/api/admin/packages/{id}/list", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/admin/packages/{id}/unlist", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":151.25}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(packageId))
                .andExpect(jsonPath("$.price").value(151.25));
    }

    @Test
    void pricingListedPackageIsRejected() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.READY);
        mockMvc.perform(
                        post("/api/admin/packages/{id}/list", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        MvcResult conflict = mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":88.00}")
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = readApiError(conflict);
        assertThat(error.getCode()).isEqualTo("PACKAGE_STATUS_INVALID");
        assertThat(error.getDetails()).contains("currentStatus=LISTED");
        assertThat(error.getDetails()).contains("hint=Unlist package before repricing.");
    }

    @Test
    void pricingSoldPackageIsRejected() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.SOLD);

        MvcResult conflict = mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":88.00}")
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = readApiError(conflict);
        assertThat(error.getCode()).isEqualTo("PACKAGE_STATUS_INVALID");
        assertThat(error.getDetails()).contains("currentStatus=SOLD");
    }

    @Test
    void nonAdminCannotSetPackagePrice() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.READY);

        MvcResult forbidden = mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":10.00}")
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertApiError(forbidden, "FORBIDDEN");
    }

    @Test
    void negativePackagePriceIsRejected() throws Exception {
        Long packageId = createPackageWithStatus(PackageStatus.READY);

        MvcResult invalid = mockMvc.perform(
                        post("/api/admin/packages/{id}/price", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"price\":-0.01}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertApiError(invalid, "VALIDATION_ERROR");
    }

    @Test
    void soldPackageIncludesPurchaserInfoInAdminListAndDetail() throws Exception {
        User buyer = createUser("buyer@package-admin-test.local", Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);
        Long packageId = createPackageWithStatus(PackageStatus.SOLD);
        Package soldPackage = packageRepository.findById(packageId).orElseThrow();

        Purchase paidPurchase = new Purchase();
        paidPurchase.setPackageEntity(soldPackage);
        paidPurchase.setBuyerUser(buyer);
        paidPurchase.setStatus(PurchaseStatus.PAID);
        paidPurchase.setAmountCents(19999L);
        paidPurchase.setCurrency("usd");
        purchaseRepository.saveAndFlush(paidPurchase);

        mockMvc.perform(
                        get("/api/admin/packages/{id}", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(packageId))
                .andExpect(jsonPath("$.status").value(PackageStatus.SOLD.name()))
                .andExpect(jsonPath("$.purchaserUserId").value(buyer.getId()))
                .andExpect(jsonPath("$.purchaserEmail").value(buyer.getEmail()))
                .andExpect(jsonPath("$.purchasedAt").isNotEmpty());

        mockMvc.perform(
                        get("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(packageId))
                .andExpect(jsonPath("$[0].status").value(PackageStatus.SOLD.name()))
                .andExpect(jsonPath("$[0].purchaserUserId").value(buyer.getId()))
                .andExpect(jsonPath("$[0].purchaserEmail").value(buyer.getEmail()))
                .andExpect(jsonPath("$[0].purchasedAt").isNotEmpty());
    }

    private Long createDraftPackage(String notes) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"notes\":\"" + notes + "\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private Long createPackageWithStatus(PackageStatus status) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL);
        assertThat(admin).isNotNull();

        Package packageEntity = new Package();
        packageEntity.setCreatedByUser(admin);
        packageEntity.setStatus(status);
        packageEntity.setNotes("status-" + status.name().toLowerCase());
        packageEntity.setTotalClaims(0);
        packageEntity.setTotalFaceValue(BigDecimal.ZERO);
        return packageRepository.saveAndFlush(packageEntity).getId();
    }

    private Claim createApprovedClaim(User owner, BigDecimal amountOwed, BigDecimal currentAmount) {
        Claim claim = new Claim();
        claim.setUser(owner);
        claim.setClientName("Package Test Client");
        claim.setClientContact("package-test-client@example.com");
        claim.setClientAddress("500 Package Way");
        claim.setDebtType("COMMERCIAL");
        claim.setContactHistory("Collection attempt log");
        claim.setAmountOwed(amountOwed);
        claim.setCurrentAmount(currentAmount);
        claim.setOriginalAmount(amountOwed);
        claim.setDateOfDefault(LocalDate.of(2026, 2, 10));
        claim.setContractFileKey("contract-" + UUID.randomUUID());
        claim.setSubmittedAt(LocalDateTime.now());
        claim.setStatus(ClaimStatus.APPROVED);
        return claimRepository.saveAndFlush(claim);
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Package Admin Test User");
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

    private void assertApiError(MvcResult result, String expectedCode) throws Exception {
        ApiErrorResponse error = readApiError(result);
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getRequestId()).isNotBlank();
    }

    private ApiErrorResponse readApiError(MvcResult result) throws Exception {
        return objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
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
