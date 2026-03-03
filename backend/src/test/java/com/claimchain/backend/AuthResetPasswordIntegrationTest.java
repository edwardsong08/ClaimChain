package com.claimchain.backend;

import com.claimchain.backend.model.PasswordResetToken;
import com.claimchain.backend.model.RefreshToken;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.PasswordResetTokenRepository;
import com.claimchain.backend.repository.RefreshTokenRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.security.TokenHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthResetPasswordIntegrationTest {

    private static final String TEST_EMAIL = "reset-user@reset-password-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDb() {
        jdbcTemplate.update("DELETE FROM password_reset_tokens");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", "%@reset-password-test.local");
    }

    @Test
    void resetPassword_happyPath_marksTokenUsed_updatesPassword_andRevokesAllRefreshTokens() throws Exception {
        User user = createTestUser("OldPassword123!");
        String rawResetToken = "test-reset-token-happy-path";
        PasswordResetToken resetToken = createResetToken(user, rawResetToken, Instant.now().plusSeconds(900), null);

        createActiveRefreshToken(user, "refresh-happy-1");
        createActiveRefreshToken(user, "refresh-happy-2");

        String newPassword = "NewPassword123!";
        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + rawResetToken + "\",\"newPassword\":\"" + newPassword + "\"}")
                )
                .andExpect(status().isNoContent());

        PasswordResetToken reloadedToken = passwordResetTokenRepository.findById(resetToken.getId()).orElseThrow();
        assertThat(reloadedToken.getUsedAt()).isNotNull();

        User reloadedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(newPassword, reloadedUser.getPassword())).isTrue();

        long remainingActiveRefreshTokens = queryForLong(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL",
                user.getId()
        );
        assertThat(remainingActiveRefreshTokens).isZero();
    }

    @Test
    void resetPassword_withInvalidToken_returns400WithCodeAndRequestId() throws Exception {
        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"non-existent-token\",\"newPassword\":\"ValidPass123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void resetPassword_withUsedToken_returns400WithCodeAndRequestId() throws Exception {
        User user = createTestUser("OldPassword123!");
        String rawResetToken = "test-reset-token-used";
        createResetToken(user, rawResetToken, Instant.now().plusSeconds(900), Instant.now().minusSeconds(30));

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + rawResetToken + "\",\"newPassword\":\"ValidPass123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_USED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void resetPassword_withExpiredToken_returns400WithCodeAndRequestId() throws Exception {
        User user = createTestUser("OldPassword123!");
        String rawResetToken = "test-reset-token-expired";
        createResetToken(user, rawResetToken, Instant.now().minusSeconds(60), null);

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + rawResetToken + "\",\"newPassword\":\"ValidPass123!\"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    private User createTestUser(String rawPassword) {
        User user = new User();
        user.setName("Reset Password Test User");
        user.setEmail(TEST_EMAIL);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(Role.SERVICE_PROVIDER);
        return userRepository.save(user);
    }

    private PasswordResetToken createResetToken(User user, String rawToken, Instant expiresAt, Instant usedAt) {
        String tokenHash = tokenHashService.sha256Base64Url(rawToken);
        PasswordResetToken token = new PasswordResetToken(user, tokenHash, expiresAt);
        token.setUsedAt(usedAt);
        return passwordResetTokenRepository.save(token);
    }

    private void createActiveRefreshToken(User user, String rawSeed) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(tokenHashService.sha256Base64Url(rawSeed + "-" + System.nanoTime()));
        refreshToken.setExpiresAt(Instant.now().plusSeconds(3600));
        refreshToken.setRevokedAt(null);
        refreshTokenRepository.save(refreshToken);
    }

    private long queryForLong(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }
}
