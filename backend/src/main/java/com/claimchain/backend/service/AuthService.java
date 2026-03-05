package com.claimchain.backend.service;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.AdminBootstrapRequestDTO;
import com.claimchain.backend.dto.ForgotPasswordRequestDTO;
import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.dto.ResetPasswordRequestDTO;
import com.claimchain.backend.exception.AuthTokenException;
import com.claimchain.backend.exception.EmailAlreadyRegisteredException;
import com.claimchain.backend.model.AdminBootstrapState;
import com.claimchain.backend.model.PasswordResetToken;
import com.claimchain.backend.model.RefreshToken;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.AdminBootstrapStateRepository;
import com.claimchain.backend.repository.PasswordResetTokenRepository;
import com.claimchain.backend.repository.RefreshTokenRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.security.BootstrapProperties;
import com.claimchain.backend.security.JwtService;
import com.claimchain.backend.security.TokenHashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AdminBootstrapStateRepository adminBootstrapStateRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired
    private Environment environment;

    @Autowired
    private BootstrapProperties bootstrapProperties;

    @Value("${security.jwt.password-reset-expiration-millis:900000}")
    private long passwordResetExpirationMillis;

    public String register(RegisterRequest request, Role role) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        // Optional fields
        user.setBusinessName(request.getBusinessName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setEinOrLicense(request.getEinOrLicense());
        user.setBusinessType(request.getBusinessType());

        // Default to pending verification at signup
        user.setVerificationStatus(VerificationStatus.PENDING);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        return jwtService.generateToken(user);
    }

    /**
     * Login returns:
     * - accessToken (JWT)
     * - refreshToken (random secret, only stored hashed)
     */
    public Map<String, String> login(String email, String password) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        User user = userRepository.findByEmail(email);

        String accessToken = jwtService.generateToken(user);

        String refreshTokenRaw = generateRefreshTokenSecret();
        storeRefreshToken(user, refreshTokenRaw);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshTokenRaw
        );
    }

    /**
     * Refresh returns:
     * - new accessToken
     * - new refreshToken (rotated)
     */
    public Map<String, String> refresh(String refreshTokenRaw) {
        String hash = tokenHashService.sha256Base64Url(refreshTokenRaw);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthTokenException("REFRESH_TOKEN_INVALID", "Invalid refresh token."));

        if (existing.getRevokedAt() != null) {
            throw new AuthTokenException("REFRESH_TOKEN_REVOKED", "Refresh token revoked.");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthTokenException("REFRESH_TOKEN_EXPIRED", "Refresh token expired.");
        }

        User user = existing.getUser();

        // Revoke old token (rotation)
        existing.setRevokedAt(Instant.now());
        refreshTokenRepository.save(existing);

        // Issue new tokens
        String accessToken = jwtService.generateToken(user);
        String newRefreshTokenRaw = generateRefreshTokenSecret();
        storeRefreshToken(user, newRefreshTokenRaw);

        return Map.of(
                "accessToken", accessToken,
                "refreshToken", newRefreshTokenRaw
        );
    }

    /**
     * Logout (single-session): revoke the presented refresh token.
     * Security behavior:
     * - If token is not found, do nothing (avoid token validity oracle).
     * - If already revoked, do nothing.
     */
    public void logout(String refreshTokenRaw) {
        String hash = tokenHashService.sha256Base64Url(refreshTokenRaw);

        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(Instant.now());
                refreshTokenRepository.save(rt);
            }
        });
    }

    public void forgotPassword(ForgotPasswordRequestDTO req) {
        String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail);

        if (user == null) {
            return;
        }

        passwordResetTokenRepository.deleteByUser(user);

        String rawToken = generatePasswordResetTokenSecret();
        String tokenHash = tokenHashService.sha256Base64Url(rawToken);
        Instant expiresAt = Instant.now().plusMillis(passwordResetExpirationMillis);

        passwordResetTokenRepository.save(new PasswordResetToken(user, tokenHash, expiresAt));

        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            String requestId = MDC.get(RequestIdFilter.MDC_KEY);
            if (requestId != null) {
                log.warn("DEV ONLY forgot-password token issued: requestId={} email={} rawToken={}", requestId, normalizedEmail, rawToken);
            } else {
                log.warn("DEV ONLY forgot-password token issued: email={} rawToken={}", normalizedEmail, rawToken);
            }
        }
    }

    @Transactional
    public Map<String, Object> bootstrapAdmin(AdminBootstrapRequestDTO req) {
        String configuredToken = bootstrapProperties.getAdminToken();
        if (configuredToken == null || configuredToken.isBlank()) {
            throw new AuthTokenException("ADMIN_BOOTSTRAP_NOT_CONFIGURED", "Admin bootstrap token is not configured.");
        }

        if (!configuredToken.equals(req.getBootstrapToken().trim())) {
            throw new AuthTokenException("ADMIN_BOOTSTRAP_INVALID_TOKEN", "Invalid admin bootstrap token.");
        }

        AdminBootstrapState state = adminBootstrapStateRepository.findById(1)
                .orElseThrow(() -> new AuthTokenException("ADMIN_BOOTSTRAP_STATE_MISSING", "Admin bootstrap state is not initialized."));

        if (state.getUsedAt() != null) {
            throw new AuthTokenException("ADMIN_BOOTSTRAP_ALREADY_USED", "Admin bootstrap has already been used.");
        }

        if (userRepository.existsByRole(Role.ADMIN)) {
            throw new AuthTokenException("ADMIN_BOOTSTRAP_ALREADY_INITIALIZED", "An admin user already exists.");
        }

        String normalizedEmail = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw new AuthTokenException("ADMIN_BOOTSTRAP_EMAIL_TAKEN", "Email already registered.");
        }

        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(req.getPassword()));
        admin.setRole(Role.ADMIN);
        admin.setVerificationStatus(VerificationStatus.APPROVED);
        User savedAdmin = userRepository.save(admin);

        Instant now = Instant.now();
        state.setUsedAt(now);
        state.setUsedByUser(savedAdmin);
        adminBootstrapStateRepository.save(state);

        return Map.of(
                "id", savedAdmin.getId(),
                "email", savedAdmin.getEmail(),
                "role", savedAdmin.getRole().name()
        );
    }

    @Transactional
    public void resetPassword(ResetPasswordRequestDTO req) {
        String tokenRaw = req.getToken().trim();
        String tokenHash = tokenHashService.sha256Base64Url(tokenRaw);

        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new AuthTokenException("PASSWORD_RESET_TOKEN_INVALID", "Invalid password reset token."));

        if (resetToken.isUsed()) {
            throw new AuthTokenException("PASSWORD_RESET_TOKEN_USED", "Password reset token has already been used.");
        }

        Instant now = Instant.now();
        if (resetToken.isExpired(now)) {
            throw new AuthTokenException("PASSWORD_RESET_TOKEN_EXPIRED", "Password reset token expired.");
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        resetToken.setUsedAt(now);
        passwordResetTokenRepository.save(resetToken);

        refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
    }

    private void storeRefreshToken(User user, String refreshTokenRaw) {
        String refreshTokenHash = tokenHashService.sha256Base64Url(refreshTokenRaw);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(refreshTokenHash);
        rt.setExpiresAt(Instant.now().plusMillis(jwtService.getRefreshExpirationMillis()));
        rt.setRevokedAt(null);

        refreshTokenRepository.save(rt);
    }

    private String generateRefreshTokenSecret() {
        byte[] bytes = new byte[64]; // 512-bit random
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generatePasswordResetTokenSecret() {
        byte[] bytes = new byte[32]; // 256-bit random
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
