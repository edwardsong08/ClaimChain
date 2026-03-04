package com.claimchain.backend;

import com.claimchain.backend.model.Claim;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long claimId;
    private String adminToken;
    private String providerAToken;
    private String providerBToken;
    private String collectionToken;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)", "%@claim-ownership-test.local");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@claim-ownership-test.local");

        User admin = createUser("Claim Ownership Admin", ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
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

        adminToken = loginAndGetAccessToken(admin.getEmail());
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
