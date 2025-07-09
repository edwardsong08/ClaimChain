package com.claimchain.backend.service;

import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private UserRepository userRepository;

    public ClaimResponseDTO createClaim(ClaimRequestDTO dto, String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found with email: " + email);

        if (!user.isVerified()) {
            throw new RuntimeException("User is not verified to submit claims.");
        }

        Claim claim = new Claim();
        claim.setClientName(dto.getClientName());
        claim.setClientContact(dto.getClientContact());
        claim.setClientAddress(dto.getClientAddress());
        claim.setDebtType(dto.getDebtType());
        claim.setContactHistory(dto.getContactHistory());
        claim.setAmountOwed(dto.getAmount());
        claim.setDateOfDefault(dto.getDateOfDefault());
        claim.setContractFileKey(dto.getContractFileKey());
        claim.setUser(user);
        claim.setSubmittedAt(LocalDateTime.now());

        Claim saved = claimRepository.save(claim);
        return mapToDTO(saved);
    }

    public List<ClaimResponseDTO> getClaimsForUser(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found with email: " + email);

        List<Claim> claims = claimRepository.findByUser(user);
        return claims.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private ClaimResponseDTO mapToDTO(Claim claim) {
        ClaimResponseDTO dto = new ClaimResponseDTO();

        dto.setId(claim.getId());
        dto.setClientName(claim.getClientName());
        dto.setClientContact(claim.getClientContact());
        dto.setClientAddress(claim.getClientAddress());
        dto.setDebtType(claim.getDebtType());
        dto.setContactHistory(claim.getContactHistory());
        dto.setAmount(claim.getAmountOwed());
        dto.setDateOfDefault(claim.getDateOfDefault() != null ? claim.getDateOfDefault().toString() : null);
        dto.setContractFileKey(claim.getContractFileKey());
        dto.setStatus(claim.getStatus());
        dto.setSubmittedAt(claim.getSubmittedAt().toString());
        dto.setSubmittedBy(claim.getUser().getName());

        return dto;
    }
}
