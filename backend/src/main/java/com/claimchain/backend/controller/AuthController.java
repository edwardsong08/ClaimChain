package com.claimchain.backend.controller;

import com.claimchain.backend.dto.LoginRequestDTO;
import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.dto.RegisterRequestDTO;
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

        String token = authService.register(registerRequest, role);
        return ResponseEntity.status(201).body(Map.of("token", token));
    }

    @PostMapping("/login")
    public Map<String, String> login(@Valid @RequestBody LoginRequestDTO request) {
        String email = request.getEmail();
        String password = request.getPassword();

        String token = authService.login(email, password);
        return Map.of("token", token);
    }
}
