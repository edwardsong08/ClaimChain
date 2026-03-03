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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.bootstrap.admin-token=test-bootstrap-token")
class AdminBootstrapIntegrationTest {

    private static final String TOKEN = "test-bootstrap-token";

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
        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_at = NULL, used_by_user_id = NULL WHERE id = 1");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IS NOT NULL");
        jdbcTemplate.update("DELETE FROM users WHERE role = 'ADMIN'");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@admin-bootstrap-test.local");
    }

    @Test
    @WithMockUser
    void bootstrapAdmin_success_createsVerifiedAdminAndMarksStateUsed() throws Exception {
        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"" + TOKEN + "\",\"email\":\"BOOTSTRAP-ADMIN@ADMIN-BOOTSTRAP-TEST.LOCAL\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.email").value("bootstrap-admin@admin-bootstrap-test.local"))
                .andExpect(jsonPath("$.role").value("ADMIN"));

        User admin = userRepository.findByEmail("bootstrap-admin@admin-bootstrap-test.local");
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getVerificationStatus()).isEqualTo(VerificationStatus.APPROVED);
        assertThat(passwordEncoder.matches("AdminPassword123!", admin.getPassword())).isTrue();

        Map<String, Object> stateRow = jdbcTemplate.queryForMap(
                "SELECT used_at, used_by_user_id FROM admin_bootstrap_state WHERE id = 1"
        );
        assertThat(stateRow.get("used_at")).isNotNull();
        Number usedByUserId = (Number) stateRow.get("used_by_user_id");
        assertThat(usedByUserId).isNotNull();
        assertThat(usedByUserId.longValue()).isEqualTo(admin.getId());
    }

    @Test
    @WithMockUser
    void bootstrapAdmin_invalidToken_returns400WithCodeAndRequestId() throws Exception {
        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"wrong-token\",\"email\":\"admin@admin-bootstrap-test.local\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ADMIN_BOOTSTRAP_INVALID_TOKEN"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser
    void bootstrapAdmin_secondAttemptAfterSuccess_returnsAlreadyUsed() throws Exception {
        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"" + TOKEN + "\",\"email\":\"first-admin@admin-bootstrap-test.local\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"" + TOKEN + "\",\"email\":\"second-admin@admin-bootstrap-test.local\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ADMIN_BOOTSTRAP_ALREADY_USED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    @WithMockUser
    void bootstrapAdmin_adminExistsButStateUnused_returnsAlreadyInitialized() throws Exception {
        User existingAdmin = new User();
        existingAdmin.setEmail("existing-admin@admin-bootstrap-test.local");
        existingAdmin.setPassword(passwordEncoder.encode("ExistingAdminPass123!"));
        existingAdmin.setRole(Role.ADMIN);
        existingAdmin.setVerificationStatus(VerificationStatus.APPROVED);
        userRepository.save(existingAdmin);

        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_at = NULL, used_by_user_id = NULL WHERE id = 1");

        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"" + TOKEN + "\",\"email\":\"new-admin@admin-bootstrap-test.local\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ADMIN_BOOTSTRAP_ALREADY_INITIALIZED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.bootstrap.admin-token=")
class AdminBootstrapNotConfiguredIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_at = NULL, used_by_user_id = NULL WHERE id = 1");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IS NOT NULL");
        jdbcTemplate.update("DELETE FROM users WHERE role = 'ADMIN'");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@admin-bootstrap-test.local");
    }

    @Test
    @WithMockUser
    void bootstrapAdmin_notConfigured_returns400WithCodeAndRequestId() throws Exception {
        mockMvc.perform(
                        post("/api/admin/bootstrap")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"bootstrapToken\":\"anything\",\"email\":\"admin@admin-bootstrap-test.local\",\"password\":\"AdminPassword123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ADMIN_BOOTSTRAP_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }
}
