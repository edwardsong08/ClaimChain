package com.claimchain.backend.controller;

import com.claimchain.backend.dto.ForgotPasswordRequestDTO;
import com.claimchain.backend.dto.LoginRequestDTO;
import com.claimchain.backend.dto.LogoutRequestDTO;
import com.claimchain.backend.dto.RefreshRequestDTO;
import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.dto.RegisterRequestDTO;
import com.claimchain.backend.dto.ResetPasswordRequestDTO;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequestDTO request) {
        // Validate role
        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: must be SERVICE_PROVIDER or COLLECTION_AGENCY");
        }

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setName(request.getName());
        registerRequest.setEmail(request.getEmail());
        registerRequest.setPassword(request.getPassword());
        registerRequest.setRole(request.getRole());
        registerRequest.setBusinessName(request.getBusinessName());
        registerRequest.setPhone(request.getPhone());
        registerRequest.setAddress(request.getAddress());
        registerRequest.setEinOrLicense(request.getEinOrLicense());
        registerRequest.setBusinessType(request.getBusinessType());

        // Access token only (registration behavior unchanged for now)
        String token = authService.register(registerRequest, role);
        return ResponseEntity.status(201).body(Map.of("token", token));
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequestDTO request) {
        String email = request.getEmail();
        String password = request.getPassword();

        // Now returns access + refresh
        return authService.login(email, password);
    }

    @PostMapping("/refresh")
    public Map<String, String> refresh(@Valid @RequestBody RefreshRequestDTO request) {
        return authService.refresh(request.getRefreshToken());
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDTO request) {
        authService.forgotPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequestDTO request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
