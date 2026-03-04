package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.Ruleset;
import com.claimchain.backend.model.RulesetStatus;
import com.claimchain.backend.model.RulesetType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RulesetAdminIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@ruleset-admin-test.local";
    private static final String PROVIDER_EMAIL = "provider@ruleset-admin-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@ruleset-admin-test.local";

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
    void nonAdminGetsForbiddenForRulesetEndpoints() throws Exception {
        MvcResult forbidden = mockMvc.perform(
                        get("/api/admin/rulesets/SCORING")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(
                forbidden.getResponse().getContentAsString(),
                ApiErrorResponse.class
        );
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getRequestId()).isNotBlank();
    }

    @Test
    void adminCanCreateAndActivateRulesets_withSingleActivePerTypeAndAudit() throws Exception {
        Long draftOneId = createDraft("SCORING", "{\"threshold\":650}", 1);
        activateDraft(draftOneId, 1);

        Long draftTwoId = createDraft("SCORING", "{\"threshold\":700}", 2);
        activateDraft(draftTwoId, 2);

        List<Ruleset> scoringRulesets = rulesetRepository.findByTypeOrderByVersionDesc(RulesetType.SCORING);
        assertThat(scoringRulesets).hasSize(2);

        Ruleset latest = scoringRulesets.get(0);
        assertThat(latest.getId()).isEqualTo(draftTwoId);
        assertThat(latest.getStatus()).isEqualTo(RulesetStatus.ACTIVE);
        assertThat(latest.getVersion()).isEqualTo(2);

        Ruleset previous = scoringRulesets.stream()
                .filter(r -> r.getId().equals(draftOneId))
                .findFirst()
                .orElseThrow();
        assertThat(previous.getStatus()).isEqualTo(RulesetStatus.ARCHIVED);

        long activeCount = scoringRulesets.stream()
                .filter(r -> r.getStatus() == RulesetStatus.ACTIVE)
                .count();
        assertThat(activeCount).isEqualTo(1);

        mockMvc.perform(
                        get("/api/admin/rulesets/SCORING/active")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(draftTwoId))
                .andExpect(jsonPath("$.type").value("SCORING"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(2));

        Integer draftAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'RULESET_DRAFT_CREATED' AND entity_type = 'RULESET'",
                Integer.class
        );
        Integer activatedAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_events WHERE action = 'RULESET_ACTIVATED' AND entity_type = 'RULESET'",
                Integer.class
        );

        assertThat(draftAuditCount).isEqualTo(2);
        assertThat(activatedAuditCount).isEqualTo(2);

        String secondActivationMetadata = jdbcTemplate.queryForObject(
                "SELECT metadata_json FROM audit_events WHERE action = 'RULESET_ACTIVATED' AND entity_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                draftTwoId
        );
        assertThat(secondActivationMetadata).contains("\"priorActiveId\":" + draftOneId);
    }

    private Long createDraft(String type, String configJson, int expectedVersion) throws Exception {
        String payload = objectMapper.writeValueAsString(new DraftPayload(configJson));

        MvcResult result = mockMvc.perform(
                        post("/api/admin/rulesets/{type}/draft", type)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value(type))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(expectedVersion))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private void activateDraft(Long rulesetId, int expectedVersion) throws Exception {
        mockMvc.perform(
                        post("/api/admin/rulesets/{id}/activate", rulesetId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(rulesetId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(expectedVersion));
    }

    private User createUser(String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName("Ruleset Admin Test User");
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

    private record DraftPayload(String configJson) {
    }
}
