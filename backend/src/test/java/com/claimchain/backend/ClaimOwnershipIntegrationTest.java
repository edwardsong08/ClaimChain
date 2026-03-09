package com.claimchain.backend;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimScoreRepository;
import com.claimchain.backend.repository.RulesetRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimOwnershipIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@claim-ownership-test.local";
    private static final String PROVIDER_A_EMAIL = "provider-a@claim-ownership-test.local";
    private static final String PROVIDER_B_EMAIL = "provider-b@claim-ownership-test.local";
    private static final String COLLECTION_EMAIL = "collector@claim-ownership-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimScoreRepository claimScoreRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long claimId;
    private User adminUser;
    private String adminToken;
    private String providerAToken;
    private String providerBToken;
    private String collectionToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM claim_scores");
        jdbcTemplate.update("DELETE FROM rulesets");
        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@claim-ownership-test.local");

        adminUser = createUser("Claim Ownership Admin", ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        User providerA = createUser("Provider A", PROVIDER_A_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        User providerB = createUser("Provider B", PROVIDER_B_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        createUser("Collector", COLLECTION_EMAIL, Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);

        Claim claim = new Claim();
        claim.setUser(providerA);
        claim.setClientName("Claim Owner Client");
        claim.setClientContact("client-owner@example.com");
        claim.setClientAddress("101 Owner St");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Reminder email sent");
        claim.setAmountOwed(new BigDecimal("250.00"));
        claim.setDateOfDefault(LocalDate.of(2026, 1, 15));
        claim.setContractFileKey("owner-contract-key");
        claimId = claimRepository.save(claim).getId();

        adminToken = loginAndGetAccessToken(adminUser.getEmail());
        providerAToken = loginAndGetAccessToken(providerA.getEmail());
        providerBToken = loginAndGetAccessToken(providerB.getEmail());
        collectionToken = loginAndGetAccessToken(COLLECTION_EMAIL);
    }

    @Test
    void getClaimById_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/claims/{id}", claimId))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void getClaimById_withCollectionAgencyToken_returns403() throws Exception {
        mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + collectionToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void getClaimById_withOwnerProviderToken_returns200() throws Exception {
        mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + providerAToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(claimId.intValue()))
                .andExpect(jsonPath("$.submittedBy").value("Provider A"));
    }

    @Test
    void getClaimById_withNonOwnerProviderToken_returns404() throws Exception {
        mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + providerBToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void getClaimById_withAdminToken_returns200() throws Exception {
        mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(claimId.intValue()))
                .andExpect(jsonPath("$.submittedBy").value("Provider A"));
    }

    @Test
    void getClaimById_returnsNullScoreFieldsWhenNoScoreExists() throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + providerAToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("scoreTotal").isMissingNode() || body.path("scoreTotal").isNull()).isTrue();
        assertThat(body.path("grade").isMissingNode() || body.path("grade").isNull()).isTrue();
        assertThat(body.path("scoredAt").isMissingNode() || body.path("scoredAt").isNull()).isTrue();
    }

    @Test
    void getClaimById_includesLatestScoreFieldsWhenScoreExists() throws Exception {
        Ruleset ruleset = createActiveScoringRuleset(adminUser);
        Claim claim = claimRepository.findById(claimId).orElseThrow();
        recordScore(claim, ruleset, 78, "B", true, Instant.parse("2026-03-01T10:00:00Z"), "APPROVAL", 0.92d);

        MvcResult result = mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + providerAToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("scoreTotal").asInt()).isEqualTo(78);
        assertThat(body.path("grade").asText()).isEqualTo("B");
        assertThat(body.path("eligible").asBoolean()).isTrue();
        assertThat(body.path("subscoreEnforceability").asInt()).isEqualTo(25);
        assertThat(body.path("subscoreDocumentation").asInt()).isEqualTo(20);
        assertThat(body.path("subscoreCollectability").asInt()).isEqualTo(18);
        assertThat(body.path("subscoreOperationalRisk").asInt()).isEqualTo(15);
        assertThat(body.path("scoreTrigger").asText()).isEqualTo("APPROVAL");
        assertThat(body.path("scoredAt").asText()).isEqualTo("2026-03-01T10:00:00Z");
        assertThat(body.path("extractionSuccessRate").asDouble()).isEqualTo(0.92d);
    }

    @Test
    void getClaimById_usesMostRecentScoreWhenMultipleScoresExist() throws Exception {
        Ruleset ruleset = createActiveScoringRuleset(adminUser);
        Claim claim = claimRepository.findById(claimId).orElseThrow();

        recordScore(claim, ruleset, 61, "C", true, Instant.parse("2026-03-01T10:00:00Z"), "APPROVAL", 0.64d);
        recordScore(claim, ruleset, 84, "B", true, Instant.parse("2026-03-01T11:00:00Z"), "ADMIN_RESCORE", 0.88d);

        MvcResult result = mockMvc.perform(
                        get("/api/claims/{id}", claimId)
                                .header("Authorization", "Bearer " + providerAToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("scoreTotal").asInt()).isEqualTo(84);
        assertThat(body.path("grade").asText()).isEqualTo("B");
        assertThat(body.path("scoreTrigger").asText()).isEqualTo("ADMIN_RESCORE");
        assertThat(body.path("scoredAt").asText()).isEqualTo("2026-03-01T11:00:00Z");
        assertThat(body.path("extractionSuccessRate").asDouble()).isEqualTo(0.88d);
    }

    private Ruleset createActiveScoringRuleset(User createdBy) {
        Ruleset ruleset = new Ruleset();
        ruleset.setType(RulesetType.SCORING);
        ruleset.setStatus(RulesetStatus.ACTIVE);
        ruleset.setVersion(1);
        ruleset.setConfigJson("{\"eligibility\":{\"requiredClaimStatus\":\"APPROVED\"}}");
        ruleset.setCreatedByUser(createdBy);
        return rulesetRepository.saveAndFlush(ruleset);
    }

    private void recordScore(
            Claim claim,
            Ruleset ruleset,
            int scoreTotal,
            String grade,
            boolean eligible,
            Instant scoredAt,
            String trigger,
            double extractionSuccessRate
    ) {
        ClaimScore score = new ClaimScore();
        score.setClaim(claim);
        score.setRuleset(ruleset);
        score.setRulesetVersion(ruleset.getVersion());
        score.setEligible(eligible);
        score.setScoreTotal(scoreTotal);
        score.setGrade(grade);
        score.setSubscoreEnforceability(25);
        score.setSubscoreDocumentation(20);
        score.setSubscoreCollectability(18);
        score.setSubscoreOperationalRisk(15);
        score.setExplainabilityJson("{\"trigger\":\"" + trigger + "\",\"contributions\":[],\"eligibleReasons\":[]}");
        score.setFeatureSnapshotJson("{\"extractionSuccessRate\":" + extractionSuccessRate + "}");
        score.setScoredAt(scoredAt);
        score.setScoredByUser(adminUser);
        claimScoreRepository.saveAndFlush(score);
    }

    private User createUser(String name, String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName(name);
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

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
