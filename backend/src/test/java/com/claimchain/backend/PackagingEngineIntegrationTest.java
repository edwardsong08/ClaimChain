package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.DebtorType;
import com.claimchain.backend.model.DisputeStatus;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.Package;
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
import com.claimchain.backend.repository.PackageRepository;
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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PackagingEngineIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@packaging-engine-test.local";
    private static final String PROVIDER_EMAIL = "provider@packaging-engine-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@packaging-engine-test.local";

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
    private PackageRepository packageRepository;

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
    void buildPackagePersistsReadyPackage_andPackagesIncludedClaims_withDiversificationAndReasons() throws Exception {
        Ruleset packagingRuleset = createPackagingRuleset(adminUser, 1, defaultPackagingConfig(3));

        Claim c1 = createApprovedClaim("Claim One", new BigDecimal("900.00"), "NY", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 1, 10, 0));
        Claim c2 = createApprovedClaim("Claim Two", new BigDecimal("800.00"), "CA", DebtorType.BUSINESS, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 2, 10, 0));
        Claim c3 = createApprovedClaim("Claim Three", new BigDecimal("700.00"), "TX", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 3, 10, 0));
        Claim c4 = createApprovedClaim("Claim Four", new BigDecimal("600.00"), "NY", DebtorType.BUSINESS, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 4, 10, 0));
        Claim excludedDisputed = createApprovedClaim("Claim Five", new BigDecimal("500.00"), "FL", DebtorType.CONSUMER, DisputeStatus.ACTIVE, LocalDateTime.of(2026, 2, 5, 10, 0));

        addSuccessfulRequiredDocuments(c1);
        addSuccessfulRequiredDocuments(c2);
        addSuccessfulRequiredDocuments(c3);
        addSuccessfulRequiredDocuments(c4);
        addSuccessfulRequiredDocuments(excludedDisputed);

        recordScore(c1, 95, "A");
        recordScore(c2, 90, "A");
        recordScore(c3, 85, "B");
        recordScore(c4, 80, "B");
        recordScore(excludedDisputed, 75, "C");

        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "notes":"auto-built package"
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.rulesetId").value(packagingRuleset.getId()))
                .andExpect(jsonPath("$.rulesetVersion").value(packagingRuleset.getVersion()))
                .andExpect(jsonPath("$.totalClaims").value(4))
                .andExpect(jsonPath("$.totalFaceValue").value(3000.00))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long packageId = body.get("packageId").asLong();
        List<Long> includedClaimIds = new ArrayList<>();
        body.get("claimIds").forEach(node -> includedClaimIds.add(node.asLong()));

        Package savedPackage = packageRepository.findByIdWithClaims(packageId).orElseThrow();
        assertThat(savedPackage.getStatus()).isEqualTo(PackageStatus.READY);
        assertThat(savedPackage.getRuleset()).isNotNull();
        assertThat(savedPackage.getRuleset().getId()).isEqualTo(packagingRuleset.getId());
        assertThat(savedPackage.getRulesetVersion()).isEqualTo(packagingRuleset.getVersion());
        assertThat(savedPackage.getTotalClaims()).isEqualTo(4);
        assertThat(savedPackage.getTotalFaceValue()).isEqualByComparingTo("3000.00");

        List<Claim> includedClaims = claimRepository.findAllById(includedClaimIds);
        assertThat(includedClaims).hasSize(4);
        assertThat(includedClaims).allMatch(claim -> claim.getStatus() == ClaimStatus.PACKAGED);

        Claim excludedAfter = claimRepository.findById(excludedDisputed.getId()).orElseThrow();
        assertThat(excludedAfter.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        Map<String, Long> jurisdictionCounts = includedClaims.stream()
                .collect(Collectors.groupingBy(
                        claim -> claim.getJurisdictionState() == null ? "UNKNOWN" : claim.getJurisdictionState(),
                        Collectors.counting()
                ));
        Map<String, Long> debtorTypeCounts = includedClaims.stream()
                .collect(Collectors.groupingBy(
                        claim -> claim.getDebtorType() == null ? "UNKNOWN" : claim.getDebtorType().name(),
                        Collectors.counting()
                ));

        double jurisdictionCap = 0.67d;
        double debtorTypeCap = 0.67d;
        for (Long count : jurisdictionCounts.values()) {
            assertThat(((double) count) / includedClaims.size()).isLessThanOrEqualTo(jurisdictionCap + 1e-9d);
        }
        for (Long count : debtorTypeCounts.values()) {
            assertThat(((double) count) / includedClaims.size()).isLessThanOrEqualTo(debtorTypeCap + 1e-9d);
        }

        List<String> includedReasonJsons = jdbcTemplate.query(
                "SELECT included_reason_json FROM package_claims WHERE package_id = ? ORDER BY id",
                (rs, rowNum) -> rs.getString(1),
                packageId
        );
        assertThat(includedReasonJsons).hasSize(4);
        for (String reasonJson : includedReasonJsons) {
            JsonNode reason = objectMapper.readTree(reasonJson);
            assertThat(reason.has("scoreTotal")).isTrue();
            assertThat(reason.has("grade")).isTrue();
            assertThat(reason.has("packagingRulesetId")).isTrue();
            assertThat(reason.has("packagingRulesetVersion")).isTrue();
            assertThat(reason.has("eligibilityThresholds")).isTrue();
            assertThat(reason.has("diversification")).isTrue();
            assertThat(reason.get("requiredDocTypesPresent").asBoolean()).isTrue();
        }

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'PACKAGE_CREATED' AND entity_type = 'PACKAGE' AND entity_id = ?",
                Integer.class,
                packageId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void buildPackageDryRunPreviewsSelection_withoutPersistingOrFreezingClaims() throws Exception {
        createPackagingRuleset(adminUser, 1, defaultPackagingConfig(2));

        Claim c1 = createApprovedClaim("Dry Run One", new BigDecimal("400.00"), "NY", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 1, 9, 0));
        Claim c2 = createApprovedClaim("Dry Run Two", new BigDecimal("300.00"), "CA", DebtorType.BUSINESS, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 2, 9, 0));

        addSuccessfulRequiredDocuments(c1);
        addSuccessfulRequiredDocuments(c2);
        recordScore(c1, 88, "B");
        recordScore(c2, 82, "B");

        mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "dryRun": true
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.packageId").doesNotExist())
                .andExpect(jsonPath("$.totalClaims").value(2))
                .andExpect(jsonPath("$.claimIds[0]").value(c1.getId()))
                .andExpect(jsonPath("$.claimIds[1]").value(c2.getId()));

        Integer packageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM packages", Integer.class);
        Integer packageClaimsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM package_claims", Integer.class);
        assertThat(packageCount).isEqualTo(0);
        assertThat(packageClaimsCount).isEqualTo(0);
        assertThat(claimRepository.findById(c1.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claimRepository.findById(c2.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void dryRunReturns200WithBuildableFalseWhenConstraintsCannotBeMet_withoutPersisting() throws Exception {
        createPackagingRuleset(adminUser, 1, defaultPackagingConfig(4));

        Claim c1 = createApprovedClaim("Dry Run Fail One", new BigDecimal("450.00"), "NY", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 1, 12, 0));
        Claim c2 = createApprovedClaim("Dry Run Fail Two", new BigDecimal("350.00"), "CA", DebtorType.BUSINESS, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 2, 12, 0));
        Claim c3 = createApprovedClaim("Dry Run Fail Three", new BigDecimal("300.00"), "TX", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 3, 12, 0));

        addSuccessfulRequiredDocuments(c1);
        addSuccessfulRequiredDocuments(c2);
        addSuccessfulRequiredDocuments(c3);
        recordScore(c1, 90, "A");
        recordScore(c2, 84, "B");
        recordScore(c3, 80, "B");

        mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "dryRun": true
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.buildable").value(false))
                .andExpect(jsonPath("$.failureReasons").isArray())
                .andExpect(jsonPath("$.failureReasons[0]").value(org.hamcrest.Matchers.containsString("minClaims")))
                .andExpect(jsonPath("$.totalClaims").value(3))
                .andExpect(jsonPath("$.claimIds[0]").value(c1.getId()))
                .andExpect(jsonPath("$.claimIds[1]").value(c2.getId()))
                .andExpect(jsonPath("$.claimIds[2]").value(c3.getId()));

        Integer packageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM packages", Integer.class);
        Integer packageClaimsCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM package_claims", Integer.class);
        assertThat(packageCount).isEqualTo(0);
        assertThat(packageClaimsCount).isEqualTo(0);
        assertThat(claimRepository.findById(c1.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claimRepository.findById(c2.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(claimRepository.findById(c3.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void buildPackageFailsWith409WhenSizingConstraintsCannotBeMet() throws Exception {
        createPackagingRuleset(adminUser, 1, defaultPackagingConfig(4));

        Claim c1 = createApprovedClaim("Fail One", new BigDecimal("350.00"), "NY", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 1, 11, 0));
        Claim c2 = createApprovedClaim("Fail Two", new BigDecimal("340.00"), "CA", DebtorType.BUSINESS, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 2, 11, 0));
        Claim c3 = createApprovedClaim("Fail Three", new BigDecimal("330.00"), "TX", DebtorType.CONSUMER, DisputeStatus.NONE, LocalDateTime.of(2026, 2, 3, 11, 0));

        addSuccessfulRequiredDocuments(c1);
        addSuccessfulRequiredDocuments(c2);
        addSuccessfulRequiredDocuments(c3);
        recordScore(c1, 92, "A");
        recordScore(c2, 88, "B");
        recordScore(c3, 84, "B");

        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PACKAGE_BUILD_FAILED"))
                .andExpect(jsonPath("$.details").isArray())
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getDetails()).anyMatch(detail -> detail.contains("minClaims"));

        Integer packageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM packages", Integer.class);
        assertThat(packageCount).isEqualTo(0);
    }

    @Test
    void nonAdminCannotBuildPackage() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/admin/packages/build")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getRequestId()).isNotBlank();
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

    private Claim createApprovedClaim(
            String clientName,
            BigDecimal amount,
            String jurisdictionState,
            DebtorType debtorType,
            DisputeStatus disputeStatus,
            LocalDateTime submittedAt
    ) {
        Claim claim = new Claim();
        claim.setUser(providerUser);
        claim.setClientName(clientName);
        claim.setClientContact(clientName.toLowerCase().replace(" ", ".") + "@example.com");
        claim.setClientAddress("1 Test Drive");
        claim.setDebtType("COMMERCIAL");
        claim.setContactHistory("History " + clientName);
        claim.setDateOfDefault(LocalDate.of(2026, 1, 15));
        claim.setContractFileKey("contract-" + UUID.randomUUID());
        claim.setAmountOwed(amount);
        claim.setCurrentAmount(amount);
        claim.setOriginalAmount(amount);
        claim.setJurisdictionState(jurisdictionState);
        claim.setDebtorType(debtorType);
        claim.setDisputeStatus(disputeStatus);
        claim.setStatus(ClaimStatus.APPROVED);
        claim.setSubmittedAt(submittedAt);
        return claimRepository.saveAndFlush(claim);
    }

    private void addSuccessfulRequiredDocuments(Claim claim) {
        createDocument(claim, DocumentType.INVOICE, ExtractionStatus.SUCCEEDED);
        createDocument(claim, DocumentType.CONTRACT, ExtractionStatus.SUCCEEDED);
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

    private void recordScore(Claim claim, int scoreTotal, String grade) {
        ClaimScore score = new ClaimScore();
        score.setClaim(claim);
        score.setRuleset(scoringRuleset);
        score.setRulesetVersion(scoringRuleset.getVersion());
        score.setEligible(true);
        score.setScoreTotal(scoreTotal);
        score.setGrade(grade);
        score.setExplainabilityJson("{\"reason\":\"test\"}");
        score.setFeatureSnapshotJson("{\"snapshot\":\"test\"}");
        score.setScoredAt(Instant.now().plusMillis(scoreTotal));
        score.setScoredByUser(adminUser);
        claimScoreRepository.saveAndFlush(score);
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Packaging Engine Test User");
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

    private String defaultPackagingConfig(int minClaims) {
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
                    "minClaims": %d,
                    "maxClaims": 4,
                    "minTotalFaceValue": 1000,
                    "maxTotalFaceValue": 3000
                  },
                  "diversification": {
                    "maxPctPerJurisdiction": 0.67,
                    "maxPctPerDebtorType": 0.67
                  },
                  "selectionStrategy": {
                    "mode": "BEST_FIRST"
                  }
                }
                """.formatted(minClaims);
    }

    private void cleanupTestData() {
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
