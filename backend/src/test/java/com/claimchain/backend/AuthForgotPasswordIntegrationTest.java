package com.claimchain.backend;

import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthForgotPasswordIntegrationTest {

    private static final String EXISTING_EMAIL = "existing@forgot-password-test.local";
    private static final String MISSING_EMAIL = "missing@forgot-password-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@forgot-password-test.local");
    }

    @Test
    void forgotPassword_withExistingEmail_returns204AndCreatesResetTokenRow() throws Exception {
        User user = new User();
        user.setName("Forgot Password Existing User");
        user.setEmail(EXISTING_EMAIL);
        user.setPassword("test-password-hash");
        user.setRole(Role.SERVICE_PROVIDER);
        User savedUser = userRepository.save(user);

        Instant now = Instant.now();

        mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"EXISTING@FORGOT-PASSWORD-TEST.LOCAL\"}")
                )
                .andExpect(status().isNoContent());

        long tokenCountForUser = queryForLong(
                "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
                savedUser.getId()
        );
        assertThat(tokenCountForUser).isEqualTo(1L);

        Instant expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM password_reset_tokens WHERE user_id = ? ORDER BY created_at DESC LIMIT 1",
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("expires_at");
                    return ts.toInstant();
                },
                savedUser.getId()
        );
        assertThat(expiresAt).isAfter(now);
    }

    @Test
    void forgotPassword_withNonExistingEmail_returns204WithoutEnumerationSignal() throws Exception {
        long beforeTokenCount = queryForLong("SELECT COUNT(*) FROM password_reset_tokens");

        mockMvc.perform(
                        post("/api/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"missing@forgot-password-test.local\"}")
                )
                .andExpect(status().isNoContent());

        long afterTokenCount = queryForLong("SELECT COUNT(*) FROM password_reset_tokens");
        assertThat(afterTokenCount).isEqualTo(beforeTokenCount);

        long matchingUserCount = queryForLong(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                MISSING_EMAIL
        );
        assertThat(matchingUserCount).isZero();
    }

    private long queryForLong(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }
}
