package com.claimchain.backend.controller;

import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.service.AuthService;
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
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        // Validate role
        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: must be SERVICE_PROVIDER or COLLECTION_AGENCY"));
        }

        String token = authService.register(request, role);
        return ResponseEntity.status(201).body(Map.of("token", token));
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String password = body.get("password");

        String token = authService.login(email, password);
        return Map.of("token", token);
    }
}
