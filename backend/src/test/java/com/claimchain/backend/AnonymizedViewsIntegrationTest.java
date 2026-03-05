package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.AnonymizedClaimView;
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
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.AnonymizedClaimViewRepository;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnonymizedViewsIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@anonymized-views-test.local";
    private static final String PROVIDER_EMAIL = "provider@anonymized-views-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@anonymized-views-test.local";

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
    private AnonymizedClaimViewRepository anonymizedClaimViewRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User providerUser;
    private String adminToken;
    private String providerToken;
    private Ruleset scoringRuleset;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();
        adminUser = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        providerUser = createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
        scoringRuleset = createScoringRuleset(adminUser);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void generateAndListAnonymizedViews_createsBuyerSafeProjectionPerClaim() throws Exception {
        createPackagingRuleset(adminUser, 1, packagingConfigForTwoClaims());

        Claim c1 = createApprovedClaim(
                "Debtor One PII",
                "debtor-one-pii@example.com",
                "123 Secret Ave",
                new BigDecimal("1200.00"),
                "NY",
                DebtorType.CONSUMER,
                ClaimType.SERVICES,
                DisputeStatus.NONE,
                LocalDateTime.of(2026, 2, 1, 10, 0)
        );
        Claim c2 = createApprovedClaim(
                "Debtor Two PII",
                "debtor-two-pii@example.com",
                "456 Secret Ave",
                new BigDecimal("26000.00"),
                "CA",
                DebtorType.BUSINESS,
                ClaimType.INVOICE,
                DisputeStatus.POSSIBLE,
                LocalDateTime.of(2026, 2, 2, 10, 0)
        );

        addDocumentsForClaim(c1, true);
        addDocumentsForClaim(c2, false);
        recordScore(c1, 93, "A");
        recordScore(c2, 86, "B");

        long packageId = buildPackage();

        mockMvc.perform(
                        post("/api/admin/packages/{id}/anonymized-views/generate", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        List<AnonymizedClaimView> views = anonymizedClaimViewRepository.findByPackageIdOrderByScoreTotalDesc(packageId);
        assertThat(views).hasSize(2);
        assertThat(views.get(0).getScoreTotal()).isGreaterThanOrEqualTo(views.get(1).getScoreTotal());

        AnonymizedClaimView c1View = views.stream()
                .filter(v -> v.getClaim() != null && c1.getId().equals(v.getClaim().getId()))
                .findFirst()
                .orElseThrow();
        assertThat(c1View.getAmountBand()).isEqualTo("1000-4999");
        assertThat(c1View.getDocTypesPresent()).contains("INVOICE", "CONTRACT");
        assertThat(c1View.getExtractionSuccessRate()).isEqualTo(2.0d / 3.0d);

        MvcResult listResult = mockMvc.perform(
                        get("/api/admin/packages/{id}/anonymized-views", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].scoreTotal").isNumber())
                .andExpect(jsonPath("$[0].docTypesPresent", containsString("INVOICE")))
                .andExpect(jsonPath("$[0].docTypesPresent", containsString("CONTRACT")))
                .andReturn();

        String responseBody = listResult.getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain("Debtor One PII");
        assertThat(responseBody).doesNotContain("debtor-one-pii@example.com");
        assertThat(responseBody).doesNotContain("123 Secret Ave");
        assertThat(responseBody).doesNotContain("Debtor Two PII");
        assertThat(responseBody).doesNotContain("debtor-two-pii@example.com");
        assertThat(responseBody).doesNotContain("456 Secret Ave");
    }

    @Test
    void nonAdminCannotGenerateOrReadAnonymizedViews() throws Exception {
        createPackagingRuleset(adminUser, 1, packagingConfigForTwoClaims());
        Claim c1 = createApprovedClaim(
                "Forbidden Debtor One",
                "forbidden-one@example.com",
                "1 Forbidden Way",
                new BigDecimal("1500.00"),
                "NY",
                DebtorType.CONSUMER,
                ClaimType.SERVICES,
                DisputeStatus.NONE,
                LocalDateTime.of(2026, 2, 1, 11, 0)
        );
        Claim c2 = createApprovedClaim(
                "Forbidden Debtor Two",
                "forbidden-two@example.com",
                "2 Forbidden Way",
                new BigDecimal("1800.00"),
                "CA",
                DebtorType.BUSINESS,
                ClaimType.INVOICE,
                DisputeStatus.NONE,
                LocalDateTime.of(2026, 2, 2, 11, 0)
        );

        addDocumentsForClaim(c1, false);
        addDocumentsForClaim(c2, false);
        recordScore(c1, 90, "A");
        recordScore(c2, 88, "B");
        long packageId = buildPackage();

        MvcResult generateForbidden = mockMvc.perform(
                        post("/api/admin/packages/{id}/anonymized-views/generate", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(generateForbidden, "FORBIDDEN");

        MvcResult listForbidden = mockMvc.perform(
                        get("/api/admin/packages/{id}/anonymized-views", packageId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(listForbidden, "FORBIDDEN");
    }

    private long buildPackage() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"notes\":\"anonymized views package\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.packageId").isNumber())
                .andExpect(jsonPath("$.totalClaims").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("packageId").asLong();
    }

    private void addDocumentsForClaim(Claim claim, boolean includeFailedDoc) {
        createDocument(claim, DocumentType.INVOICE, ExtractionStatus.SUCCEEDED);
        createDocument(claim, DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED);
        if (includeFailedDoc) {
            createDocument(claim, DocumentType.CORRESPONDENCE, ExtractionStatus.FAILED);
        }
    }

    private void createDocument(Claim claim, DocumentType documentType, ExtractionStatus extractionStatus) {
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
        claimDocumentRepository.saveAndFlush(document);
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
        score.setExplainabilityJson("{\"reason\":\"integration-test\"}");
        score.setFeatureSnapshotJson("{\"snapshot\":\"integration-test\"}");
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

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Anonymized Views Test User");
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

    private String packagingConfigForTwoClaims() {
        return """
                {
                  "schemaVersion": 1,
                  "eligibility": {
                    "minScore": 60,
                    "minGrade": "C",
                    "requiredDocTypes": ["INVOICE", "CONTRACT"],
                    "minExtractionSuccessRate": 0.5,
                    "excludeDisputeStatuses": ["ACTIVE"]
                  },
                  "packageSizing": {
                    "minClaims": 2,
                    "maxClaims": 2,
                    "minTotalFaceValue": 1000,
                    "maxTotalFaceValue": 100000
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

    private void assertApiError(MvcResult result, String expectedCode) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getRequestId()).isNotBlank();
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
