package com.claimchain.backend.service;

import com.claimchain.backend.dto.RegisterRequest;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

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

    public String login(String email, String password) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        );

        User user = userRepository.findByEmail(email);
        return jwtService.generateToken(user);
    }
}
