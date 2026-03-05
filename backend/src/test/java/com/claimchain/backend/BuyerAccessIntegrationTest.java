package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.ClaimType;
import com.claimchain.backend.model.DebtorType;
import com.claimchain.backend.model.DisputeStatus;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.PackageStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.RulesetRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BuyerAccessIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@buyer-access-test.local";
    private static final String PROVIDER_EMAIL = "provider@buyer-access-test.local";
    private static final String BUYER_EMAIL = "buyer@buyer-access-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@buyer-access-test.local";

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
    private ClaimScoreRepository claimScoreRepository;

    @Autowired
    private ClaimDocumentRepository claimDocumentRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User providerUser;
    private User buyerUser;
    private String adminToken;
    private String providerToken;
    private String buyerToken;
    private Ruleset scoringRuleset;

    private Long listedPackageId;
    private Long readyPackageId;
    private Long listedClaimId;
    private Long readyClaimId;
    private Long listedDocId;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();
        adminUser = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        providerUser = createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        buyerUser = createUser(BUYER_EMAIL, Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);

        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
        buyerToken = loginAndGetAccessToken(BUYER_EMAIL);

        scoringRuleset = createScoringRuleset(adminUser);
        createPackagingRuleset(adminUser, 1, buyerPackagingConfig());

        Claim listedClaim = createApprovedClaim(
                "Listed Debtor PII",
                "listed-debtor-pii@example.com",
                "111 Hidden Lane",
                new BigDecimal("1200.00"),
                "NY",
                DebtorType.CONSUMER,
                ClaimType.SERVICES,
                DisputeStatus.NONE,
                LocalDateTime.of(2026, 2, 1, 9, 0)
        );
        Claim readyClaim = createApprovedClaim(
                "Ready Debtor PII",
                "ready-debtor-pii@example.com",
                "222 Hidden Lane",
                new BigDecimal("900.00"),
                "CA",
                DebtorType.BUSINESS,
                ClaimType.INVOICE,
                DisputeStatus.NONE,
                LocalDateTime.of(2026, 2, 2, 9, 0)
        );

        listedDocId = addSuccessfulRequiredDocuments(listedClaim).get(0).getId();
        addSuccessfulRequiredDocuments(readyClaim);
        recordScore(listedClaim, 95, "A");
        recordScore(readyClaim, 80, "B");
        listedClaimId = listedClaim.getId();
        readyClaimId = readyClaim.getId();

        BuildResponse firstBuild = buildOnePackageAsAdmin();
        assertThat(firstBuild.claimIds).containsExactly(listedClaimId);
        listedPackageId = firstBuild.packageId;

        mockMvc.perform(
                        post("/api/admin/packages/{id}/anonymized-views/generate", listedPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/admin/packages/{id}/list", listedPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        BuildResponse secondBuild = buildOnePackageAsAdmin();
        readyPackageId = secondBuild.packageId;
        assertThat(secondBuild.claimIds).containsExactly(readyClaim.getId());
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void buyerCanListAndReadListedPackagesWithAnonymizedViews_only() throws Exception {
        MvcResult listResult = mockMvc.perform(
                        get("/api/buyer/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode listBody = objectMapper.readTree(listResult.getResponse().getContentAsString());
        List<Long> returnedPackageIds = new ArrayList<>();
        listBody.forEach(node -> returnedPackageIds.add(node.get("id").asLong()));
        assertThat(returnedPackageIds).contains(listedPackageId);
        assertThat(returnedPackageIds).doesNotContain(readyPackageId);

        MvcResult detailResult = mockMvc.perform(
                        get("/api/buyer/packages/{id}", listedPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(listedPackageId))
                .andExpect(jsonPath("$.totalClaims").value(1))
                .andExpect(jsonPath("$.claims").isArray())
                .andExpect(jsonPath("$.claims[0].claimId").value(listedClaimId))
                .andExpect(jsonPath("$.claims[0].jurisdictionState").value("NY"))
                .andExpect(jsonPath("$.claims[0].docTypesPresent").isString())
                .andReturn();

        String responseBody = detailResult.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Listed Debtor PII");
        assertThat(responseBody).doesNotContain("listed-debtor-pii@example.com");
        assertThat(responseBody).doesNotContain("111 Hidden Lane");
    }

    @Test
    void buyerGets404ForNonListedPackageDetail() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/buyer/packages/{id}", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertApiError(result, "NOT_FOUND");
    }

    @Test
    void adminCanListAndUnlistReadyPackage_andBuyerVisibilityTracksStatus() throws Exception {
        mockMvc.perform(
                        post("/api/admin/packages/{id}/anonymized-views/generate", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/admin/packages/{id}/list", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        MvcResult buyerListAfterList = mockMvc.perform(
                        get("/api/buyer/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode buyerListBody = objectMapper.readTree(buyerListAfterList.getResponse().getContentAsString());
        List<Long> visiblePackageIds = new ArrayList<>();
        buyerListBody.forEach(node -> visiblePackageIds.add(node.get("id").asLong()));
        assertThat(visiblePackageIds).contains(readyPackageId);

        mockMvc.perform(
                        get("/api/buyer/packages/{id}", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(readyPackageId))
                .andExpect(jsonPath("$.claims").isArray());

        mockMvc.perform(
                        post("/api/admin/packages/{id}/unlist", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        MvcResult buyerAfterUnlist = mockMvc.perform(
                        get("/api/buyer/packages/{id}", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(buyerAfterUnlist, "NOT_FOUND");
    }

    @Test
    void invalidListingTransitionsReturn409PackageStatusInvalid() throws Exception {
        Long draftPackageId = createDraftPackageAsAdmin();

        MvcResult listDraft = mockMvc.perform(
                        post("/api/admin/packages/{id}/list", draftPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        ApiErrorResponse listDraftError = readApiError(listDraft);
        assertThat(listDraftError.getCode()).isEqualTo("PACKAGE_STATUS_INVALID");
        assertThat(listDraftError.getDetails()).contains("currentStatus=DRAFT");

        MvcResult unlistReady = mockMvc.perform(
                        post("/api/admin/packages/{id}/unlist", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        ApiErrorResponse unlistReadyError = readApiError(unlistReady);
        assertThat(unlistReadyError.getCode()).isEqualTo("PACKAGE_STATUS_INVALID");
        assertThat(unlistReadyError.getDetails()).contains("currentStatus=READY");
    }

    @Test
    void buyerCannotAccessRawClaimsDocumentsOrAdminEndpoints() throws Exception {
        MvcResult claimResult = mockMvc.perform(
                        get("/api/claims/{id}", listedClaimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andReturn();
        int claimStatus = claimResult.getResponse().getStatus();
        assertThat(claimStatus).isIn(403, 404);

        MvcResult downloadResult = mockMvc.perform(
                        get("/api/documents/{docId}/download", listedDocId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(downloadResult, "FORBIDDEN");

        MvcResult adminResult = mockMvc.perform(
                        get("/api/admin/unverified-users")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(adminResult, "FORBIDDEN");
    }

    @Test
    void providerCannotAccessBuyerEndpoints() throws Exception {
        MvcResult listResult = mockMvc.perform(
                        get("/api/buyer/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(listResult, "FORBIDDEN");

        MvcResult detailResult = mockMvc.perform(
                        get("/api/buyer/packages/{id}", listedPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(detailResult, "FORBIDDEN");
    }

    @Test
    void entitledBuyerCanExportPackageAsAttachment_withOnlyAnonymizedFields_andAuditRecorded() throws Exception {
        mockMvc.perform(
                        post("/api/admin/packages/{id}/anonymized-views/generate", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());
        grantEntitlement(readyPackageId, buyerUser.getId());

        MvcResult exportResult = mockMvc.perform(
                        get("/api/buyer/packages/{id}/export", readyPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"claimchain-package-" + readyPackageId + ".json\""
                ))
                .andReturn();

        JsonNode exportBody = objectMapper.readTree(exportResult.getResponse().getContentAsString());
        assertThat(exportBody.has("package")).isTrue();
        assertThat(exportBody.get("package").get("id").asLong()).isEqualTo(readyPackageId);
        assertThat(exportBody.get("package").get("status").asText()).isEqualTo(PackageStatus.READY.name());
        assertThat(exportBody.get("claims").isArray()).isTrue();
        assertThat(exportBody.get("claims").size()).isEqualTo(1);
        assertThat(exportBody.get("claims").get(0).get("claimId").asLong()).isEqualTo(readyClaimId);
        assertThat(exportBody.get("claims").get(0).has("jurisdictionState")).isTrue();
        assertThat(exportBody.get("claims").get(0).has("docTypesPresent")).isTrue();

        String responseBody = exportResult.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Ready Debtor PII");
        assertThat(responseBody).doesNotContain("ready-debtor-pii@example.com");
        assertThat(responseBody).doesNotContain("222 Hidden Lane");
        assertThat(responseBody).doesNotContain("debtorName");
        assertThat(responseBody).doesNotContain("debtorEmail");
        assertThat(responseBody).doesNotContain("debtorAddress");

        Integer exportAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'PACKAGE_EXPORTED' AND entity_type = 'PACKAGE' AND entity_id = ?",
                Integer.class,
                readyPackageId
        );
        assertThat(exportAuditCount).isEqualTo(1);
        String exportAuditMetadata = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM audit_events WHERE action = 'PACKAGE_EXPORTED' AND entity_type = 'PACKAGE' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                readyPackageId
        );
        assertThat(exportAuditMetadata).contains("\"buyerUserId\":" + buyerUser.getId());
        assertThat(exportAuditMetadata).contains("\"packageId\":" + readyPackageId);
        assertThat(exportAuditMetadata).contains("\"claimCount\":1");
    }

    @Test
    void buyerExportReturns404WhenNotEntitled() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/buyer/packages/{id}/export", listedPackageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertApiError(result, "NOT_FOUND");
        Integer exportAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'PACKAGE_EXPORTED' AND entity_type = 'PACKAGE' AND entity_id = ?",
                Integer.class,
                listedPackageId
        );
        assertThat(exportAuditCount).isEqualTo(0);
    }

    private BuildResponse buildOnePackageAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"notes\":\"buyer-access-test\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.packageId").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long packageId = body.get("packageId").asLong();
        List<Long> claimIds = new ArrayList<>();
        body.get("claimIds").forEach(node -> claimIds.add(node.asLong()));
        return new BuildResponse(packageId, claimIds);
    }

    private Long createDraftPackageAsAdmin() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"notes\":\"draft-for-transition-test\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(PackageStatus.DRAFT.name()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private List<ClaimDocument> addSuccessfulRequiredDocuments(Claim claim) {
        ClaimDocument invoice = createDocument(claim, DocumentType.INVOICE, ExtractionStatus.SUCCEEDED);
        ClaimDocument contract = createDocument(claim, DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED);
        return List.of(invoice, contract);
    }

    private ClaimDocument createDocument(Claim claim, DocumentType documentType, ExtractionStatus extractionStatus) {
        ClaimDocument document = new ClaimDocument();
        document.setClaim(claim);
        document.setUploadedByUser(providerUser);
        document.setOriginalFilename(documentType.name().toLowerCase() + ".pdf");
        document.setContentType("application/pdf");
        document.setSniffedContentType("application/pdf");
        document.setSizeBytes(1024L);
        document.setStorageKey("tests/" + claim.getId() + "/" + UUID.randomUUID());
        document.setStatus(DocumentStatus.READY);
        document.setDocumentType(documentType);
        document.setExtractionStatus(extractionStatus);
        return claimDocumentRepository.saveAndFlush(document);
    }

    private Claim createApprovedClaim(
            String debtorName,
            String debtorEmail,
            String debtorAddress,
            BigDecimal amount,
            String jurisdictionState,
            DebtorType debtorType,
            ClaimType claimType,
            DisputeStatus disputeStatus,
            LocalDateTime submittedAt
    ) {
        Claim claim = new Claim();
        claim.setUser(providerUser);
        claim.setDebtorName(debtorName);
        claim.setDebtorEmail(debtorEmail);
        claim.setDebtorAddress(debtorAddress);
        claim.setClientName("Client " + debtorName);
        claim.setClientContact("client-" + debtorName.replace(" ", "-").toLowerCase() + "@example.com");
        claim.setClientAddress("Provider Address");
        claim.setDebtType("COMMERCIAL");
        claim.setContactHistory("Contact history");
        claim.setContractFileKey("contract-" + UUID.randomUUID());
        claim.setDateOfDefault(LocalDate.of(2026, 1, 15));
        claim.setAmountOwed(amount);
        claim.setCurrentAmount(amount);
        claim.setOriginalAmount(amount);
        claim.setJurisdictionState(jurisdictionState);
        claim.setDebtorType(debtorType);
        claim.setClaimType(claimType);
        claim.setDisputeStatus(disputeStatus);
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setSubmittedAt(submittedAt);
        return claimRepository.saveAndFlush(claim);
    }

    private void recordScore(Claim claim, int scoreTotal, String grade) {
        ClaimScore score = new ClaimScore();
        score.setClaim(claim);
        score.setRuleset(scoringRuleset);
        score.setRulesetVersion(scoringRuleset.getVersion());
        score.setEligible(true);
        score.setScoreTotal(scoreTotal);
        score.setGrade(grade);
        score.setExplainabilityJson("{\"reason\":\"buyer-access-test\"}");
        score.setFeatureSnapshotJson("{\"snapshot\":\"buyer-access-test\"}");
        score.setScoredAt(Instant.now().plusMillis(scoreTotal));
        score.setScoredByUser(adminUser);
        claimScoreRepository.saveAndFlush(score);
    }

    private Ruleset createScoringRuleset(User createdBy) {
        Ruleset ruleset = new Ruleset();
        ruleset.setType(RulesetType.SCORING);
        ruleset.setStatus(RulesetStatus.ACTIVE);
        ruleset.setVersion(1);
        ruleset.setConfigJson("{\"weights\":{\"enforceability\":0.3}}");
        ruleset.setCreatedByUser(createdBy);
        return rulesetRepository.saveAndFlush(ruleset);
    }

    private Ruleset createPackagingRuleset(User createdBy, int version, String configJson) {
        Ruleset ruleset = new Ruleset();
        ruleset.setType(RulesetType.PACKAGING);
        ruleset.setStatus(RulesetStatus.ACTIVE);
        ruleset.setVersion(version);
        ruleset.setConfigJson(configJson);
        ruleset.setCreatedByUser(createdBy);
        return rulesetRepository.saveAndFlush(ruleset);
    }

    private String buyerPackagingConfig() {
        return """
                {
                  "schemaVersion": 1,
                  "eligibility": {
                    "minScore": 60,
                    "minGrade": "C",
                    "requiredDocTypes": ["INVOICE", "CONTRACT"],
                    "minExtractionSuccessRate": 1.0,
                    "excludeDisputeStatuses": ["ACTIVE"]
                  },
                  "packageSizing": {
                    "minClaims": 1,
                    "maxClaims": 1,
                    "minTotalFaceValue": 500,
                    "maxTotalFaceValue": 50000
                  },
                  "diversification": {
                    "maxPctPerJurisdiction": 1.0,
                    "maxPctPerDebtorType": 1.0
                  },
                  "selectionStrategy": {
                    "mode": "BEST_FIRST"
                  }
                }
                """;
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Buyer Access Test User");
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

    private void grantEntitlement(Long packageId, Long buyerUserId) {
        jdbcTemplate.update(
                "INSERT INTO buyer_entitlements(package_id, buyer_user_id) VALUES (?, ?)",
                packageId,
                buyerUserId
        );
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

    private static class BuildResponse {
        private final Long packageId;
        private final List<Long> claimIds;

        private BuildResponse(Long packageId, List<Long> claimIds) {
            this.packageId = packageId;
            this.claimIds = claimIds;
        }
    }
}
