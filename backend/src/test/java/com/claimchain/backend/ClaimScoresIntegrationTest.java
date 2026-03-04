package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.ClaimStatus;
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
import com.claimchain.backend.service.ClaimScoringPersistenceService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClaimScoresIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@claim-scores-test.local";
    private static final String PROVIDER_EMAIL = "provider@claim-scores-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@claim-scores-test.local";

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
    private RulesetRepository rulesetRepository;

    @Autowired
    private ClaimScoreRepository claimScoreRepository;

    @Autowired
    private ClaimScoringPersistenceService claimScoringPersistenceService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User adminUser;
    private User providerUser;
    private Long claimId;
    private Long rulesetId;
    private String adminToken;
    private String providerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        adminUser = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        providerUser = createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);

        claimId = createClaim(providerUser).getId();
        rulesetId = createRuleset(adminUser).getId();

        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void recordsScoreRun_andAdminCanReadScores() throws Exception {
        claimScoringPersistenceService.recordScoreRun(
                claimId,
                rulesetId,
                7,
                true,
                82,
                "B",
                75,
                88,
                79,
                70,
                "{\"reason\":\"good docs\"}",
                "{\"snapshot\":\"v1\"}",
                adminUser.getId()
        );

        ClaimScore saved = claimScoreRepository.findFirstByClaimIdOrderByScoredAtDesc(claimId).orElseThrow();
        assertThat(saved.getClaim().getId()).isEqualTo(claimId);
        assertThat(saved.getRuleset().getId()).isEqualTo(rulesetId);
        assertThat(saved.getRulesetVersion()).isEqualTo(7);
        assertThat(saved.getScoreTotal()).isEqualTo(82);
        assertThat(saved.getGrade()).isEqualTo("B");
        assertThat(saved.isEligible()).isTrue();
        assertThat(saved.getScoredByUser()).isNotNull();
        assertThat(saved.getScoredByUser().getId()).isEqualTo(adminUser.getId());

        mockMvc.perform(
                        get("/api/admin/claims/{claimId}/scores", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].scoreTotal").value(82))
                .andExpect(jsonPath("$[0].grade").value("B"))
                .andExpect(jsonPath("$[0].eligible").value(true))
                .andExpect(jsonPath("$[0].rulesetId").value(rulesetId))
                .andExpect(jsonPath("$[0].rulesetVersion").value(7))
                .andExpect(jsonPath("$[0].subscoreEnforceability").value(75))
                .andExpect(jsonPath("$[0].subscoreDocumentation").value(88))
                .andExpect(jsonPath("$[0].subscoreCollectability").value(79))
                .andExpect(jsonPath("$[0].subscoreOperationalRisk").value(70))
                .andExpect(jsonPath("$[0].scoredAt").isNotEmpty());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'CLAIM_SCORED' AND entity_type = 'CLAIM' AND entity_id = ?",
                Integer.class,
                claimId
        );
        assertThat(auditCount).isEqualTo(1);
    }

    @Test
    void nonAdminCannotReadAdminScoresEndpoint() throws Exception {
        MvcResult forbiddenResult = mockMvc.perform(
                        get("/api/admin/claims/{claimId}/scores", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(forbiddenResult.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getRequestId()).isNotBlank();
    }

    private Claim createClaim(User owner) {
        Claim claim = new Claim();
        claim.setUser(owner);
        claim.setClientName("Scoring Claim");
        claim.setClientContact("scoring@example.com");
        claim.setClientAddress("900 Score Street");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Scoring history");
        claim.setAmountOwed(new BigDecimal("320.00"));
        claim.setDateOfDefault(LocalDate.of(2026, 2, 20));
        claim.setContractFileKey("score-claim-contract");
        claim.setStatus(ClaimStatus.APPROVED);
        return claimRepository.saveAndFlush(claim);
    }

    private Ruleset createRuleset(User createdBy) {
        Ruleset ruleset = new Ruleset();
        ruleset.setType(RulesetType.SCORING);
        ruleset.setStatus(RulesetStatus.ACTIVE);
        ruleset.setVersion(7);
        ruleset.setConfigJson("{\"weights\":{\"enforceability\":0.3}}");
        ruleset.setCreatedByUser(createdBy);
        return rulesetRepository.saveAndFlush(ruleset);
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Claim Score Test User");
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

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM claim_scores");
        jdbcTemplate.update("DELETE FROM document_jobs");
        jdbcTemplate.update("DELETE FROM claim_documents");
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
