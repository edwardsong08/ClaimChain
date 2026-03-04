package com.claimchain.backend;

import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

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

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@verification-test.local");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@verification-test.local");
    }

    @Test
    void unverifiedUsers_returnsOnlyPendingUsers() throws Exception {
        createUser("pending@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.PENDING);
        createUser("approved@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        createUser("rejected@verification-test.local", Role.SERVICE_PROVIDER, VerificationStatus.REJECTED);

        mockMvc.perform(
                        get("/api/admin/unverified-users")
                                .with(user(ADMIN_EMAIL).roles("ADMIN"))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[?(@.email=='pending@verification-test.local')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.email=='approved@verification-test.local')]").isEmpty())
                .andExpect(jsonPath("$[?(@.email=='rejected@verification-test.local')]").isEmpty());
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
