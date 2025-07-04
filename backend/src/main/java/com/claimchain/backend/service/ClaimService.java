package com.claimchain.backend.service;

import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.User;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private UserRepository userRepository;

    // Called by controller using explicit email
    public ClaimResponseDTO createClaim(ClaimRequestDTO dto, String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found with email: " + email);

        Claim claim = new Claim();
        claim.setDebtorName(dto.getDebtorName());
        claim.setDebtorEmail(dto.getDebtorEmail());
        claim.setDebtorPhone(dto.getDebtorPhone());
        claim.setServiceDescription(dto.getServiceDescription());
        claim.setAmountOwed(new BigDecimal(dto.getAmountOwed()));
        claim.setDateOfService(LocalDate.parse(dto.getDateOfService()));
        claim.setUser(user);
        claim.setSubmittedAt(LocalDateTime.now());

        Claim saved = claimRepository.save(claim);
        return mapToDTO(saved);
    }

    // Called by controller using explicit email
    public List<ClaimResponseDTO> getClaimsForUser(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found with email: " + email);

        List<Claim> claims = claimRepository.findByUser(user);
        return claims.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private ClaimResponseDTO mapToDTO(Claim claim) {
        ClaimResponseDTO dto = new ClaimResponseDTO();
        dto.setId(claim.getId());
        dto.setDebtorName(claim.getDebtorName());
        dto.setDebtorEmail(claim.getDebtorEmail());
        dto.setDebtorPhone(claim.getDebtorPhone());
        dto.setServiceDescription(claim.getServiceDescription());
        dto.setAmountOwed(claim.getAmountOwed().doubleValue());
        dto.setDateOfService(claim.getDateOfService().toString());
        dto.setStatus(claim.getStatus());
        dto.setSubmittedAt(claim.getSubmittedAt().toString());
        dto.setSubmittedBy(claim.getUser().getName());
        return dto;
    }
}
