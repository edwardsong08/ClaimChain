package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationPolicyIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@authorization-policy-test.local";
    private static final String APPROVED_PROVIDER_EMAIL = "provider-approved@authorization-policy-test.local";
    private static final String PENDING_PROVIDER_EMAIL = "provider-pending@authorization-policy-test.local";
    private static final String COLLECTION_EMAIL = "collector@authorization-policy-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@authorization-policy-test.local");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)", "%@authorization-policy-test.local");
        jdbcTemplate.update("DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@authorization-policy-test.local");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@authorization-policy-test.local");

        createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED, Instant.now());
        createUser(APPROVED_PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED, Instant.now());
        createUser(PENDING_PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.PENDING, null);
        createUser(COLLECTION_EMAIL, Role.COLLECTION_AGENCY, VerificationStatus.APPROVED, Instant.now());
    }

    @Test
    void adminEndpoints_requireAdminRole() throws Exception {
        String providerToken = loginAndGetAccessToken(APPROVED_PROVIDER_EMAIL, PASSWORD);
        String adminToken = loginAndGetAccessToken(ADMIN_EMAIL, PASSWORD);

        MvcResult noTokenResult = mockMvc.perform(get("/api/admin/unverified-users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(noTokenResult, "UNAUTHORIZED");

        MvcResult providerTokenResult = mockMvc.perform(
                        get("/api/admin/unverified-users")
                                .header("Authorization", "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(providerTokenResult, "FORBIDDEN");

        mockMvc.perform(
                        get("/api/admin/unverified-users")
                                .header("Authorization", "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void claimSubmission_requiresRoleAndApprovedVerification() throws Exception {
        String collectionToken = loginAndGetAccessToken(COLLECTION_EMAIL, PASSWORD);
        String pendingProviderToken = loginAndGetAccessToken(PENDING_PROVIDER_EMAIL, PASSWORD);
        String approvedProviderToken = loginAndGetAccessToken(APPROVED_PROVIDER_EMAIL, PASSWORD);

        String claimPayload = """
                {
                  "debtorAddress":"55 Debtor Way",
                  "debtorType":"CONSUMER",
                  "jurisdictionState":"ny",
                  "claimType":"SERVICES",
                  "disputeStatus":"NONE",
                  "clientName":"Client A",
                  "clientContact":"client@example.com",
                  "amount":123.45,
                  "dateOfDefault":"2026-01-15",
                  "debtType":"CONSUMER",
                  "clientAddress":"123 Test St",
                  "contactHistory":"Reminder sent by email",
                  "contractFileKey":"contract-key-policy-test"
                }
                """;

        MvcResult noTokenResult = mockMvc.perform(
                        post("/api/claims")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(noTokenResult, "UNAUTHORIZED");

        MvcResult collectionTokenResult = mockMvc.perform(
                        post("/api/claims")
                                .header("Authorization", "Bearer " + collectionToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(collectionTokenResult, "FORBIDDEN");

        MvcResult pendingProviderResult = mockMvc.perform(
                        post("/api/claims")
                                .header("Authorization", "Bearer " + pendingProviderToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(pendingProviderResult, "FORBIDDEN");

        MvcResult approvedProviderResult = mockMvc.perform(
                        post("/api/claims")
                                .header("Authorization", "Bearer " + approvedProviderToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andReturn();

        int successStatus = approvedProviderResult.getResponse().getStatus();
        assertThat(successStatus).isIn(200, 201);
    }

    private User createUser(String email, Role role, VerificationStatus status, Instant verifiedAt) {
        User user = new User();
        user.setName("Authorization Policy Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setVerificationStatus(status);
        user.setVerifiedAt(verifiedAt);
        return userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private void assertApiError(MvcResult result, String expectedCode) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getRequestId()).isNotBlank();
    }
}
