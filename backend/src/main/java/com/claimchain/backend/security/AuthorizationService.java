package com.claimchain.backend.security;

import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.service.ClaimService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;

    public AuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User requireUser(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new AccessDeniedException("Requester is not authorized.");
        }

        User user = userRepository.findByEmail(normalizedEmail);
        if (user == null) {
            throw new AccessDeniedException("Requester is not authorized.");
        }
        return user;
    }

    public boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    public boolean isServiceProvider(User user) {
        return user != null && user.getRole() == Role.SERVICE_PROVIDER;
    }

    public boolean isCollectionAgency(User user) {
        return user != null && user.getRole() == Role.COLLECTION_AGENCY;
    }

    public void requireClaimAccess(Claim claim, User requester) {
        if (isAdmin(requester)) {
            return;
        }

        if (isCollectionAgency(requester)) {
            throw new AccessDeniedException("You do not have permission to access this resource.");
        }

        if (isServiceProvider(requester)) {
            String ownerEmail = claim.getUser() == null ? null : normalizeEmail(claim.getUser().getEmail());
            String requesterEmail = normalizeEmail(requester.getEmail());

            if (ownerEmail == null || requesterEmail == null || !ownerEmail.equals(requesterEmail)) {
                throw new ClaimService.ClaimNotFoundException();
            }
            return;
        }

        throw new AccessDeniedException("You do not have permission to access this resource.");
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
