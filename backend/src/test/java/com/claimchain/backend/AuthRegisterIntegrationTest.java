package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRegisterIntegrationTest {

    private static final String TEST_EMAIL_PATTERN = "%@register-conflict-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void duplicateEmailAcrossRolesReturns409EmailAlreadyRegistered() throws Exception {
        String firstPayload = """
                {
                  "name":"Provider User",
                  "email":"duplicate@register-conflict-test.local",
                  "password":"Password123!",
                  "role":"SERVICE_PROVIDER"
                }
                """;

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstPayload)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").isString());

        String secondPayload = """
                {
                  "name":"Buyer User",
                  "email":"DUPLICATE@register-conflict-test.local",
                  "password":"Password123!",
                  "role":"COLLECTION_AGENCY"
                }
                """;

        MvcResult conflictResult = mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(secondPayload)
                )
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(conflictResult.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getMessage()).isEqualTo(
                "This email already has an account. Please sign in. If you need access as both Provider and Buyer, request an additional role."
        );
        assertThat(error.getDetails()).contains("email: duplicate@register-conflict-test.local");
        assertThat(error.getRequestId()).isNotBlank();
    }

    private void cleanupTestData() {
        jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
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
