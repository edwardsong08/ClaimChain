package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminClaimReviewIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@claim-review-test.local";
    private static final String PROVIDER_EMAIL = "provider@claim-review-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@claim-review-test.local";

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
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String providerToken;
    private User adminUser;
    private User providerUser;
    private Long submittedClaimAId;
    private Long submittedClaimBId;
    private Long approvedClaimId;
    private Long packagedClaimId;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        adminUser = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        providerUser = createUser(PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);

        submittedClaimAId = createClaim(providerUser, ClaimStatus.SUBMITTED, "Submitted Client A").getId();
        submittedClaimBId = createClaim(providerUser, ClaimStatus.SUBMITTED, "Submitted Client B").getId();
        approvedClaimId = createClaim(providerUser, ClaimStatus.APPROVED, "Approved Client Existing").getId();
        packagedClaimId = createClaim(providerUser, ClaimStatus.PACKAGED, "Packaged Client Existing").getId();

        adminToken = loginAndGetAccessToken(ADMIN_EMAIL);
        providerToken = loginAndGetAccessToken(PROVIDER_EMAIL);
    }

    @Test
    void nonAdminGets403ForQueueStartReviewAndDecision() throws Exception {
        MvcResult listForbidden = mockMvc.perform(
                        get("/api/admin/claims")
                                .param("status", "SUBMITTED")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(listForbidden, "FORBIDDEN");

        MvcResult decisionForbidden = mockMvc.perform(
                        post("/api/admin/claims/{claimId}/decision", submittedClaimAId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVE\"}")
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(decisionForbidden, "FORBIDDEN");

        MvcResult startReviewForbidden = mockMvc.perform(
                        post("/api/admin/claims/{claimId}/start-review", submittedClaimAId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(startReviewForbidden, "FORBIDDEN");

        MvcResult overrideFreezeForbidden = mockMvc.perform(
                        post("/api/admin/claims/{claimId}/override-freeze", submittedClaimAId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"not allowed\"}")
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(overrideFreezeForbidden, "FORBIDDEN");
    }

    @Test
    void adminCanListSubmittedClaims() throws Exception {
        MvcResult listResult = mockMvc.perform(
                        get("/api/admin/claims")
                                .param("status", "SUBMITTED")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(body.isArray()).isTrue();

        Set<Long> ids = new HashSet<>();
        for (JsonNode node : body) {
            ids.add(node.get("id").asLong());
            assertThat(node.get("status").asText()).isEqualTo("SUBMITTED");
        }

        assertThat(ids).contains(submittedClaimAId, submittedClaimBId);
        assertThat(ids).doesNotContain(approvedClaimId);
    }

    @Test
    void adminCanStartReviewForSubmittedClaim_setsStartFields_andCreatesAuditEvent() throws Exception {
        startReview(submittedClaimAId);

        Claim updated = claimRepository.findById(submittedClaimAId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.UNDER_REVIEW);
        assertThat(updated.getReviewStartedByUser()).isNotNull();
        assertThat(updated.getReviewStartedByUser().getId()).isEqualTo(adminUser.getId());
        assertThat(updated.getReviewStartedAt()).isNotNull();

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'CLAIM_REVIEW_STARTED' AND entity_type = 'CLAIM' AND entity_id = ?",
                Integer.class,
                submittedClaimAId
        );
        assertThat(auditCount).isEqualTo(1);

        mockMvc.perform(
                        get("/api/admin/claims")
                                .param("status", "UNDER_REVIEW")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(submittedClaimAId))
                .andExpect(jsonPath("$[0].status").value("UNDER_REVIEW"));
    }

    @Test
    void decisionRejectsSubmittedClaim_andSucceedsAfterStartReview() throws Exception {
        MvcResult invalidDecisionResult = mockMvc.perform(
                        post("/api/admin/claims/{claimId}/decision", submittedClaimAId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVE\",\"notes\":\"Ready for approval\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(invalidDecisionResult, "VALIDATION_ERROR");

        startReview(submittedClaimAId);

        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/decision", submittedClaimAId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"APPROVE\",\"notes\":\"Ready for approval\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(submittedClaimAId))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Claim updated = claimRepository.findById(submittedClaimAId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(updated.getReviewNotes()).isEqualTo("Ready for approval");
        assertThat(updated.getReviewedByUser()).isNotNull();
        assertThat(updated.getReviewedByUser().getId()).isEqualTo(adminUser.getId());
        assertThat(updated.getReviewedAt()).isNotNull();

        Integer decisionAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'CLAIM_REVIEW_DECISION' AND entity_type = 'CLAIM' AND entity_id = ?",
                Integer.class,
                submittedClaimAId
        );
        assertThat(decisionAuditCount).isEqualTo(1);
    }

    @Test
    void adminCanRejectUnderReviewClaim_andStoresReviewDetails() throws Exception {
        startReview(submittedClaimBId);

        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/decision", submittedClaimBId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"decision\":\"REJECT\",\"notes\":\"Missing evidence\",\"missingDocs\":[\"proof-of-address.pdf\",\"invoice.pdf\"]}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(submittedClaimBId))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Claim updated = claimRepository.findById(submittedClaimBId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertThat(updated.getReviewNotes()).isEqualTo("Missing evidence");
        assertThat(updated.getMissingDocsJson()).contains("proof-of-address.pdf", "invoice.pdf");
        assertThat(updated.getReviewedByUser()).isNotNull();
        assertThat(updated.getReviewedByUser().getId()).isEqualTo(adminUser.getId());
        assertThat(updated.getReviewedAt()).isNotNull();

        String metadataJson = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM audit_events WHERE action = 'CLAIM_REVIEW_DECISION' AND entity_type = 'CLAIM' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                submittedClaimBId
        );
        assertThat(metadataJson).contains("\"decision\":\"REJECT\"", "\"newStatus\":\"REJECTED\"", "\"missingDocsCount\":2");
    }

    @Test
    void providerCannotModifyFrozenClaimFields() throws Exception {
        MvcResult frozenResult = mockMvc.perform(
                        put("/api/claims/{id}", packagedClaimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "debtorAddress":"200 Freeze Road",
                                          "debtorType":"BUSINESS",
                                          "jurisdictionState":"tx",
                                          "claimType":"SERVICES",
                                          "disputeStatus":"POSSIBLE",
                                          "clientName":"Updated Frozen Client",
                                          "clientContact":"updated@example.com",
                                          "amount":500.00,
                                          "dateOfDefault":"2026-02-15",
                                          "debtType":"COMMERCIAL",
                                          "clientAddress":"200 Freeze Road",
                                          "contactHistory":"Updated history",
                                          "contractFileKey":"updated-key"
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        assertApiError(frozenResult, "CLAIM_FROZEN");

        Claim persisted = claimRepository.findById(packagedClaimId).orElseThrow();
        assertThat(persisted.getClientName()).isEqualTo("Packaged Client Existing");
    }

    @Test
    void adminOverrideFreezeEndpoint_returns204_andWritesAuditEvent() throws Exception {
        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/override-freeze", packagedClaimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "reason":"Need compliance-approved correction before rescoring"
                                        }
                                        """)
                )
                .andExpect(status().isNoContent());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'CLAIM_FREEZE_OVERRIDE_REQUESTED' AND entity_type = 'CLAIM' AND entity_id = ?",
                Integer.class,
                packagedClaimId
        );
        assertThat(auditCount).isEqualTo(1);

        String metadataJson = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM audit_events WHERE action = 'CLAIM_FREEZE_OVERRIDE_REQUESTED' AND entity_type = 'CLAIM' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                packagedClaimId
        );
        assertThat(metadataJson).contains("Need compliance-approved correction before rescoring", "\"status\":\"PACKAGED\"");
    }

    private void startReview(Long claimId) throws Exception {
        mockMvc.perform(
                        post("/api/admin/claims/{claimId}/start-review", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(claimId))
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));
    }

    private Claim createClaim(User owner, ClaimStatus status, String clientName) {
        Claim claim = new Claim();
        claim.setUser(owner);
        claim.setClientName(clientName);
        claim.setClientContact(clientName.toLowerCase().replace(" ", ".") + "@example.com");
        claim.setClientAddress("100 Review Lane");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Initial contact logged");
        claim.setAmountOwed(new BigDecimal("250.00"));
        claim.setDateOfDefault(LocalDate.of(2026, 1, 10));
        claim.setContractFileKey("contract-" + UUID.randomUUID());
        claim.setSubmittedAt(LocalDateTime.now());
        claim.setStatus(status);
        return claimRepository.saveAndFlush(claim);
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Claim Review Test User");
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
        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getRequestId()).isNotBlank();
    }

    private void cleanupTestData() {
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
