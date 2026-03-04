package com.claimchain.backend.controller;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    @Autowired
    private ClaimService claimService;

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @PostMapping
    public ResponseEntity<ClaimResponseDTO> createClaim(
            @Valid @RequestBody ClaimRequestDTO requestDTO,
            Principal principal) {

        ClaimResponseDTO responseDTO = claimService.createClaim(requestDTO, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
    }

    @GetMapping
    public ResponseEntity<List<ClaimResponseDTO>> getUserClaims(Principal principal) {
        List<ClaimResponseDTO> claims = claimService.getClaimsForUser(principal.getName());
        return ResponseEntity.ok(claims);
    }

    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER','ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<ClaimResponseDTO> getClaimById(@PathVariable Long id, Principal principal) {
        ClaimResponseDTO claim = claimService.getClaimById(id, principal.getName());
        return ResponseEntity.ok(claim);
    }

    @ExceptionHandler(ClaimService.ClaimNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleClaimNotFound(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);

        ApiErrorResponse body = new ApiErrorResponse(
                "NOT_FOUND",
                "Resource not found",
                List.of("Claim not found"),
                Instant.now(),
                requestId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
