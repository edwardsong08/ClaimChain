package com.claimchain.backend.controller;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.dto.ClaimDocumentResponseDTO;
import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.DocumentUploadResponseDTO;
import com.claimchain.backend.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
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

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @PostMapping("/{id}/documents")
    public ResponseEntity<DocumentUploadResponseDTO> uploadClaimDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) String documentType,
            Principal principal
    ) {
        DocumentUploadResponseDTO response = claimService.uploadClaimDocument(id, file, documentType, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER','ADMIN')")
    @GetMapping("/{id}/documents")
    public ResponseEntity<List<ClaimDocumentResponseDTO>> listClaimDocuments(
            @PathVariable Long id,
            Principal principal
    ) {
        List<ClaimDocumentResponseDTO> documents = claimService.listClaimDocuments(id, principal.getName());
        return ResponseEntity.ok(documents);
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

    @ExceptionHandler(ClaimService.DocumentValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleDocumentValidation(
            ClaimService.DocumentValidationException ex,
            HttpServletRequest request
    ) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);

        ApiErrorResponse body = new ApiErrorResponse(
                ex.getCode(),
                ex.getMessage(),
                List.of(ex.getMessage()),
                Instant.now(),
                requestId
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
