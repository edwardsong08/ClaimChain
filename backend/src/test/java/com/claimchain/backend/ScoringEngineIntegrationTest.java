package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.ClaimScore;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.model.User;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScoringEngineIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@scoring-engine-test.local";
    private static final String PROVIDER_EMAIL = "provider@scoring-engine-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@scoring-engine-test.local";

    private static final String SCORING_CONFIG_V1 = """
            {
              "schemaVersion": 1,
              "eligibility": {
                "requiredClaimStatus": "APPROVED",
                "requiredDocTypes": ["INVOICE", "CONTRACT"],
                "minExtractionSuccessRate": 0.6,
                "blockActiveDisputes": true
              },
              "weights": {
                "enforceability": 0.35,
                "documentation": 0.30,
                "collectability": 0.25,
                "operationalRisk": 0.10
              },
              "gradeBands": [
                {"grade":"A","minScore":85},
                {"grade":"B","minScore":70},
                {"grade":"C","minScore":55},
                {"grade":"D","minScore":40},
                {"grade":"F","minScore":0}
              ],
              "caps": {
                "enforceabilityMax": 35,
                "documentationMax": 30,
                "collectabilityMax": 25,
                "operationalRiskMax": 10
              },
              "rules": [
                {"id":"JURISDICTION_KNOWN","group":"enforceability","when":{"jurisdictionKnown":true},"points":12,"reason":"Jurisdiction provided"},
                {"id":"DEBT_AGE_RECENT","group":"enforceability","when":{"debtAgeDaysLte":365},"points":8,"reason":"Debt age favorable"},
                {"id":"EXTRACTION_HIGH","group":"documentation","when":{"extractionSuccessRateGte":0.8},"points":9,"reason":"Extraction success high"},
                {"id":"DEBTOR_BUSINESS","group":"collectability","when":{"debtorTypeEquals":"BUSINESS"},"points":7,"reason":"Business debtor"},
                {"id":"AMOUNT_BUCKET","group":"collectability","when":{"currentAmountBetween":[1000,4999]},"points":6,"reason":"Amount in target band"},
                {"id":"DOC_COUNT_BASE","group":"operationalRisk","when":{"docCountGte":2},"points":4,"reason":"Document count manageable"}
              ]
            }
            """;

    private static final String SCORING_CONFIG_V2 = """
            {
              "schemaVersion": 1,
              "eligibility": {
                "requiredClaimStatus": "APPROVED",
                "requiredDocTypes": ["INVOICE", "CONTRACT"],
                "minExtractionSuccessRate": 0.6,
                "blockActiveDisputes": true
              },
              "weights": {
                "enforceability": 0.35,
                "documentation": 0.30,
                "collectability": 0.25,
                "operationalRisk": 0.10
              },
              "gradeBands": [
                {"grade":"A","minScore":85},
                {"grade":"B","minScore":70},
                {"grade":"C","minScore":55},
                {"grade":"D","minScore":40},
                {"grade":"F","minScore":0}
              ],
              "caps": {
                "enforceabilityMax": 35,
                "documentationMax": 30,
                "collectabilityMax": 25,
                "operationalRiskMax": 10
              },
              "rules": [
                {"id":"JURISDICTION_KNOWN","group":"enforceability","when":{"jurisdictionKnown":true},"points":5,"reason":"Jurisdiction provided"},
                {"id":"DEBT_AGE_RECENT","group":"enforceability","when":{"debtAgeDaysLte":365},"points":4,"reason":"Debt age favorable"},
                {"id":"EXTRACTION_HIGH","group":"documentation","when":{"extractionSuccessRateGte":0.8},"points":6,"reason":"Extraction success high"},
                {"id":"DEBTOR_BUSINESS","group":"collectability","when":{"debtorTypeEquals":"BUSINESS"},"points":3,"reason":"Business debtor"},
                {"id":"AMOUNT_BUCKET","group":"collectability","when":{"currentAmountBetween":[1000,4999]},"points":2,"reason":"Amount in target band"},
                {"id":"DOC_COUNT_BASE","group":"operationalRisk","when":{"docCountGte":2},"points":1,"reason":"Document count manageable"}
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RulesetRepository rulesetRepository;

    @Autowired
    private ClaimScoreRepository claimScoreRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String providerToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);

        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
    }

    @Test
    void approvalTriggersScoring_andRescoreUsesNewActiveVersion() throws Exception {
        Ruleset v1 = createAndActivateScoringRuleset(SCORING_CONFIG_V1, 1);
        Long claimId = createClaim("NONE");
        uploadRequiredDocuments(claimId);
        runDocumentJobs(10);

        startReview(claimId);
        approve(claimId);

        List<ClaimScore> afterApproval = claimScoreRepository.findByClaimIdOrderByScoredAtDesc(claimId);
        assertThat(afterApproval).hasSize(1);
        ClaimScore firstScore = afterApproval.get(0);
        assertThat(firstScore.getRuleset().getId()).isEqualTo(v1.getId());
        assertThat(firstScore.getRulesetVersion()).isEqualTo(1);
        assertThat(firstScore.isEligible()).isTrue();
        assertThat(firstScore.getScoreTotal()).isGreaterThan(0);
        JsonNode firstExplainability = objectMapper.readTree(firstScore.getExplainabilityJson());
        assertThat(firstExplainability.path("trigger").asText()).isEqualTo("APPROVAL");

        createAndActivateScoringRuleset(SCORING_CONFIG_V2, 2);

        mockMvc.perform(
                        post("/api/admin/claims/{id}/rescore", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isNoContent());

        List<ClaimScore> afterRescore = claimScoreRepository.findByClaimIdOrderByScoredAtDesc(claimId);
        assertThat(afterRescore).hasSize(2);
        ClaimScore latest = afterRescore.get(0);
        ClaimScore previous = afterRescore.get(1);

        assertThat(latest.getRulesetVersion()).isEqualTo(2);
        assertThat(latest.getScoreTotal()).isNotEqualTo(previous.getScoreTotal());
        JsonNode latestExplainability = objectMapper.readTree(latest.getExplainabilityJson());
        assertThat(latestExplainability.path("trigger").asText()).isEqualTo("ADMIN_RESCORE");

        Integer rescoreAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'CLAIM_RESCORED' AND entity_type = 'CLAIM' AND entity_id = ?",
                Integer.class,
                claimId
        );
        assertThat(rescoreAuditCount).isEqualTo(1);
        String rescoreAuditMetadata = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM audit_events WHERE action = 'CLAIM_RESCORED' AND entity_type = 'CLAIM' AND entity_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class,
                claimId
        );
        assertThat(rescoreAuditMetadata).contains("\"trigger\":\"ADMIN_RESCORE\"");
    }

    @Test
    void approvalWithActiveDispute_recordsIneligibleScore() throws Exception {
        createAndActivateScoringRuleset(SCORING_CONFIG_V1, 1);
        Long claimId = createClaim("ACTIVE");
        uploadRequiredDocuments(claimId);
        runDocumentJobs(10);

        startReview(claimId);
        approve(claimId);

        List<ClaimScore> scores = claimScoreRepository.findByClaimIdOrderByScoredAtDesc(claimId);
        assertThat(scores).hasSize(1);
        ClaimScore score = scores.get(0);
        assertThat(score.isEligible()).isFalse();
        assertThat(score.getScoreTotal()).isEqualTo(0);
        assertThat(score.getGrade()).isEqualTo("F");
    }

    @Test
    void nonAdminRescoreEndpoint_isForbidden() throws Exception {
        createAndActivateScoringRuleset(SCORING_CONFIG_V1, 1);
        Long claimId = createClaim("NONE");

        MvcResult forbiddenResult = mockMvc.perform(
                        post("/api/admin/claims/{id}/rescore", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(
                forbiddenResult.getResponse().getContentAsString(),
                ApiErrorResponse.class
        );
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getRequestId()).isNotBlank();
    }

    private Ruleset createAndActivateScoringRuleset(String configJson, int expectedVersion) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of("configJson", configJson));

        MvcResult draftResult = mockMvc.perform(
                        post("/api/admin/rulesets/{type}/draft", "SCORING")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.version").value(expectedVersion))
                .andReturn();

        Long rulesetId = objectMapper.readTree(draftResult.getResponse().getContentAsString()).path("id").asLong();

        mockMvc.perform(
                        post("/api/admin/rulesets/{id}/activate", rulesetId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(expectedVersion));

        return rulesetRepository.findById(rulesetId).orElseThrow();
    }

    private Long createClaim(String disputeStatus) throws Exception {
        String payload = """
                {
                  "debtorName":"Debtor Co",
                  "debtorEmail":"debtor@example.com",
                  "debtorPhone":"555-0100",
                  "debtorAddress":"101 Debtor Ln",
                  "debtorType":"BUSINESS",
                  "jurisdictionState":"NY",
                  "claimType":"INVOICE",
                  "disputeStatus":"%s",
                  "clientName":"Client Scoring",
                  "clientContact":"client@example.com",
                  "clientAddress":"500 Client Rd",
                  "debtType":"COMMERCIAL",
                  "contactHistory":"Initial reminder sent",
                  "amount":2500.00,
                  "originalAmount":3000.00,
                  "dateOfDefault":"2026-01-15",
                  "lastPaymentDate":"2025-12-15",
                  "contractFileKey":"scoring-contract-key"
                }
                """.formatted(disputeStatus);

        MvcResult claimResult = mockMvc.perform(
                        post("/api/claims")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn();

        JsonNode body = objectMapper.readTree(claimResult.getResponse().getContentAsString());
        return body.path("id").asLong();
    }

    private void uploadRequiredDocuments(Long claimId) throws Exception {
        MockMultipartFile invoice = new MockMultipartFile("file", "invoice.png", "image/png", minimalPngBytes());
        MockMultipartFile contract = new MockMultipartFile("file", "contract.png", "image/png", minimalPngBytes());

        mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(invoice)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(contract)
                                .param("documentType", "CONTRACT")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isCreated());
    }

    private void runDocumentJobs(int limit) throws Exception {
        mockMvc.perform(
                        post("/api/admin/jobs/run-document-jobs")
                                .param("limit", String.valueOf(limit))
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    private void startReview(Long claimId) throws Exception {
        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/start-review", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    private void approve(Long claimId) throws Exception {
        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/decision", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVE\",\"notes\":\"Scoring approval\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Scoring Engine Test User");
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

    private byte[] minimalPngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jxX8AAAAASUVORK5CYII="
        );
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
