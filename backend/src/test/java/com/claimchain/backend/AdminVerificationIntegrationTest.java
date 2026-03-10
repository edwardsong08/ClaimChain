package com.claimchain.backend;

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
import java.util.Iterator;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminVerificationIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@verification-test.local";

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
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@verification-test.local");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)", "%@verification-test.local");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@verification-test.local");
    }

    @Test
    void unverifiedUsers_returnsOnlyPendingUsers() throws Exception {
        createUser("pending@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);
        createUser("approved@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        createUser("rejected@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.REJECTED);

        MvcResult result = mockMvc.perform(
                        get("/api/admin/unverified-users")
                                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[?(@.email=='pending@verification-test.local')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.email=='approved@verification-test.local')]").isEmpty())
                .andExpect(jsonPath("$[?(@.email=='rejected@verification-test.local')]").isEmpty())
                .andExpect(jsonPath("$[0].verificationStatus").value("PENDING"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode pendingUser = findUserByEmail(body, "pending@verification-test.local");
        assertThat(pendingUser).isNotNull();
        assertThat(pendingUser.has("id")).isTrue();
        assertThat(pendingUser.has("name")).isTrue();
        assertThat(pendingUser.has("email")).isTrue();
        assertThat(pendingUser.has("role")).isTrue();
        assertThat(pendingUser.has("verificationStatus")).isTrue();
        assertThat(pendingUser.has("password")).isFalse();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"password\"");
    }

    @Test
    void allUsers_returnsSafeDtoFields_andOrdersNewestFirst() throws Exception {
        User adminVerifier = createUser("verifier@verification-test.local", Role.ADMIN, VerificationStatus.APPROVED);
        User providerPending = createUser("provider-pending@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);
        User buyerApproved = createUser("buyer-approved@verification-test.local", Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);
        buyerApproved.setPhone("555-0102");
        buyerApproved.setAddress("22 Example Ave");
        buyerApproved.setEinOrLicense("EIN-123");
        buyerApproved.setBusinessType("AGENCY");
        buyerApproved.setBusinessName("Buyer Approved LLC");
        buyerApproved.setVerifiedAt(Instant.parse("2026-03-01T10:00:00Z"));
        buyerApproved.setVerifiedBy(adminVerifier);
        userRepository.saveAndFlush(buyerApproved);

        User providerRejected = createUser("provider-rejected@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.REJECTED);
        providerRejected.setRejectedAt(Instant.parse("2026-03-02T10:00:00Z"));
        providerRejected.setRejectReason("Missing documentation");
        userRepository.saveAndFlush(providerRejected);

        MvcResult result = mockMvc.perform(
                        get("/api/admin/users")
                                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode pendingNode = findUserByEmail(body, providerPending.getEmail());
        JsonNode approvedNode = findUserByEmail(body, buyerApproved.getEmail());
        JsonNode rejectedNode = findUserByEmail(body, providerRejected.getEmail());

        assertThat(pendingNode).isNotNull();
        assertThat(approvedNode).isNotNull();
        assertThat(rejectedNode).isNotNull();

        assertThat(approvedNode.path("verificationStatus").asText()).isEqualTo("APPROVED");
        assertThat(approvedNode.path("verifiedByUserId").asLong()).isEqualTo(adminVerifier.getId());
        assertThat(approvedNode.path("verifiedByEmail").asText()).isEqualTo(adminVerifier.getEmail());
        assertThat(approvedNode.path("phone").asText()).isEqualTo("555-0102");
        assertThat(approvedNode.path("address").asText()).isEqualTo("22 Example Ave");
        assertThat(approvedNode.path("einOrLicense").asText()).isEqualTo("EIN-123");
        assertThat(approvedNode.path("businessType").asText()).isEqualTo("AGENCY");
        assertThat(approvedNode.path("businessName").asText()).isEqualTo("Buyer Approved LLC");

        assertThat(rejectedNode.path("verificationStatus").asText()).isEqualTo("REJECTED");
        assertThat(rejectedNode.path("rejectReason").asText()).isEqualTo("Missing documentation");

        assertThat(indexOfEmail(body, providerRejected.getEmail())).isLessThan(indexOfEmail(body, buyerApproved.getEmail()));
        assertThat(indexOfEmail(body, buyerApproved.getEmail())).isLessThan(indexOfEmail(body, providerPending.getEmail()));

        assertThat(result.getResponse().getContentAsString()).doesNotContain("\"password\"");
    }

    @Test
    void allUsers_forNonAdmin_isForbidden() throws Exception {
        mockMvc.perform(
                        get("/api/admin/users")
                                .with(user("provider@verification-test.local").roles("SERVICE_PROVIDER"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void verifyUser_setsApprovedAuditMetadata() throws Exception {
        User admin = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        User target = createUser("target-pending@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);

        mockMvc.perform(
                        post("/api/admin/verify-user/{userId}", target.getId())
                                .with(user(admin.getEmail()).roles("ADMIN"))
                )
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT verification_status, verified_at, verified_by, rejected_at, reject_reason FROM users WHERE id = ?",
                target.getId()
        );
        assertThat(row.get("verification_status")).isEqualTo("APPROVED");
        assertThat(row.get("verified_at")).isNotNull();
        assertThat(((Number) row.get("verified_by")).longValue()).isEqualTo(admin.getId());
        assertThat(row.get("rejected_at")).isNull();
        assertThat(row.get("reject_reason")).isNull();
    }

    @Test
    void rejectUser_setsRejectedAuditMetadata() throws Exception {
        User admin = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        User target = createUser("target-reject@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);

        mockMvc.perform(
                        post("/api/admin/reject-user/{userId}", target.getId())
                                .with(user(admin.getEmail()).roles("ADMIN"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\":\"Insufficient compliance documentation\"}")
                )
                .andExpect(status().isOk());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT verification_status, verified_at, verified_by, rejected_at, reject_reason FROM users WHERE id = ?",
                target.getId()
        );
        assertThat(row.get("verification_status")).isEqualTo("REJECTED");
        assertThat(row.get("rejected_at")).isNotNull();
        assertThat(row.get("reject_reason")).isEqualTo("Insufficient compliance documentation");
        assertThat(((Number) row.get("verified_by")).longValue()).isEqualTo(admin.getId());
        assertThat(row.get("verified_at")).isNull();
    }

    @Test
    void claimSubmission_pendingDenied_thenApprovedSucceeds() throws Exception {
        User admin = createUser(ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        User provider = createUser("provider@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);

        String claimPayload = """
                {
                  "debtorAddress":"77 Underwriting Blvd",
                  "debtorType":"BUSINESS",
                  "jurisdictionState":"ca",
                  "claimType":"INVOICE",
                  "disputeStatus":"NONE",
                  "clientName":"Client A",
                  "clientContact":"client@example.com",
                  "amount":123.45,
                  "dateOfDefault":"2026-01-15",
                  "debtType":"CONSUMER",
                  "clientAddress":"123 Test St",
                  "contactHistory":"Reminder sent by email",
                  "contractFileKey":"contract-key-1"
                }
                """;

        mockMvc.perform(
                        post("/api/claims")
                                .with(user(provider.getEmail()).roles("SERVICE_PROVIDER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());

        mockMvc.perform(
                        post("/api/admin/verify-user/{userId}", provider.getId())
                                .with(user(admin.getEmail()).roles("ADMIN"))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/claims")
                                .with(user(provider.getEmail()).roles("SERVICE_PROVIDER"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(claimPayload)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber());
    }

    private JsonNode findUserByEmail(JsonNode users, String email) {
        if (users == null || !users.isArray()) {
            return null;
        }
        for (JsonNode node : users) {
            if (email.equals(node.path("email").asText())) {
                return node;
            }
        }
        return null;
    }

    private int indexOfEmail(JsonNode users, String email) {
        if (users == null || !users.isArray()) {
            return -1;
        }
        Iterator<JsonNode> iterator = users.elements();
        int index = 0;
        while (iterator.hasNext()) {
            JsonNode node = iterator.next();
            if (email.equals(node.path("email").asText())) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private User createUser(String email, Role role, VerificationStatus status) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password123!"));
        user.setRole(role);
        user.setVerificationStatus(status);
        return userRepository.save(user);
    }
}
