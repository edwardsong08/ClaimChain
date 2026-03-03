package com.claimchain.backend.service;

import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.exception.AuthTokenException;
import com.claimchain.backend.model.RefreshToken;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.RefreshTokenRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.security.JwtService;
import com.claimchain.backend.security.TokenHashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenHashService tokenHashService;

    public String register(RegisterRequest request, Role role) {
        if (userRepository.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("Email already registered.");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        // Optional fields
        user.setBusinessName(request.getBusinessName());
        user.setPhone(request.getPhone());
        user.setAddress(request.getAddress());
        user.setEinOrLicense(request.getEinOrLicense());
        user.setBusinessType(request.getBusinessType());

        // Default to unverified at signup
        user.setVerified(false);

        userRepository.save(user);
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
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}