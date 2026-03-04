package com.claimchain.backend.service;

import com.claimchain.backend.dto.AdminClaimDecisionRequestDTO;
import com.claimchain.backend.dto.ClaimDocumentResponseDTO;
import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.DocumentUploadResponseDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.ClaimStatus;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.claimchain.backend.security.AuthorizationService;
import com.claimchain.backend.security.FilenameSanitizer;
import com.claimchain.backend.storage.StorageService;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ClaimService {

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ClaimDocumentRepository claimDocumentRepository;

    @Autowired
    private DocumentJobRepository documentJobRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilenameSanitizer filenameSanitizer;

    @Value("${documents.max-bytes:10000000}")
    private long maxDocumentBytes;

    @Value("${documents.allowed-types:application/pdf,image/png,image/jpeg}")
    private String allowedTypesCsv;

    private final Tika tika = new Tika();

    public ClaimResponseDTO createClaim(ClaimRequestDTO dto, String email) {
        User user = userRepository.findByEmail(email);
        if (user == null) throw new RuntimeException("User not found with email: " + email);

        if (user.getVerificationStatus() != VerificationStatus.APPROVED) {
            throw new AccessDeniedException("User is not verified to submit claims.");
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
        ClaimStatus initialStatus = ClaimStatus.SUBMITTED;
        if (!isValidTransition(null, initialStatus)) {
            throw new IllegalStateException("Invalid initial claim status transition.");
        }
        claim.setStatus(initialStatus);
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

    public List<ClaimResponseDTO> getClaimsByStatusForAdmin(String statusValue) {
        ClaimStatus status = parseClaimStatus(statusValue);
        return claimRepository.findByStatusOrderBySubmittedAtDesc(status)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public ClaimResponseDTO startReview(Long claimId, String adminEmail) {
        User admin = requireAdminUser(adminEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));

        ClaimStatus priorStatus = claim.getStatus();
        ClaimStatus newStatus = ClaimStatus.UNDER_REVIEW;

        if (!isValidTransition(priorStatus, newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid claim status transition from "
                            + (priorStatus == null ? "null" : priorStatus.name())
                            + " to "
                            + newStatus.name()
            );
        }

        claim.setStatus(newStatus);
        claim.setReviewStartedByUser(admin);
        claim.setReviewStartedAt(Instant.now());
        Claim saved = claimRepository.save(claim);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("priorStatus", priorStatus == null ? null : priorStatus.name());
        metadata.put("newStatus", newStatus.name());

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "CLAIM_REVIEW_STARTED",
                "CLAIM",
                saved.getId(),
                toJson(metadata)
        );

        return mapToDTO(saved);
    }

    public ClaimResponseDTO reviewClaim(Long claimId, AdminClaimDecisionRequestDTO request, String adminEmail) {
        if (request == null) {
            throw new IllegalArgumentException("Decision request is required.");
        }

        User admin = requireAdminUser(adminEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found."));

        ClaimStatus priorStatus = claim.getStatus();
        ClaimStatus newStatus = decisionToStatus(request.getDecision());

        if (priorStatus != ClaimStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("Claim must be UNDER_REVIEW before decision.");
        }

        if (!isValidTransition(priorStatus, newStatus)) {
            throw new IllegalArgumentException(
                    "Invalid claim status transition from "
                            + (priorStatus == null ? "null" : priorStatus.name())
                            + " to "
                            + newStatus.name()
            );
        }

        List<String> missingDocs = normalizeMissingDocs(request.getMissingDocs());
        claim.setStatus(newStatus);
        claim.setReviewNotes(normalizeReviewNotes(request.getNotes()));
        claim.setMissingDocsJson(missingDocs.isEmpty() ? null : toJson(missingDocs));
        claim.setReviewedByUser(admin);
        claim.setReviewedAt(Instant.now());

        Claim saved = claimRepository.save(claim);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", normalizeDecision(request.getDecision()));
        metadata.put("priorStatus", priorStatus == null ? null : priorStatus.name());
        metadata.put("newStatus", newStatus.name());
        metadata.put("missingDocsCount", missingDocs.size());

        auditService.record(
                admin.getId(),
                admin.getRole() == null ? null : admin.getRole().name(),
                "CLAIM_REVIEW_DECISION",
                "CLAIM",
                saved.getId(),
                toJson(metadata)
        );

        return mapToDTO(saved);
    }

    public ClaimResponseDTO getClaimById(Long claimId, String requesterEmail) {
        User requester = authorizationService.requireUser(requesterEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(ClaimNotFoundException::new);

        authorizationService.requireClaimAccess(claim, requester);
        return mapToDTO(claim);
    }

    public DocumentUploadResponseDTO uploadClaimDocument(
            Long claimId,
            MultipartFile file,
            String documentTypeValue,
            String requesterEmail
    ) {
        User requester = authorizationService.requireUser(requesterEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(ClaimNotFoundException::new);
        authorizationService.requireClaimAccess(claim, requester);
        DocumentType documentType = parseDocumentType(documentTypeValue);

        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("DOCUMENT_EMPTY", "File must not be empty.");
        }
        byte[] bytes = readFileBytesWithLimit(file);
        if (bytes.length == 0) {
            throw new DocumentValidationException("DOCUMENT_EMPTY", "File must not be empty.");
        }

        String originalFilename = filenameSanitizer.sanitize(file.getOriginalFilename());
        String declaredContentType = normalizeContentType(file.getContentType());
        String sniffedContentType = normalizeContentType(tika.detect(bytes, originalFilename));

        Set<String> allowedTypes = parseAllowedTypes();
        if (!allowedTypes.contains(sniffedContentType)) {
            throw new DocumentValidationException(
                    "DOCUMENT_TYPE_NOT_ALLOWED",
                    "Document type is not allowed."
            );
        }

        String storageKey = "claims/" + claimId + "/" + UUID.randomUUID();
        storageService.save(new ByteArrayInputStream(bytes), storageKey);

        ClaimDocument document = new ClaimDocument();
        document.setClaim(claim);
        document.setUploadedByUser(requester);
        document.setOriginalFilename(originalFilename);
        document.setContentType(declaredContentType);
        document.setSniffedContentType(sniffedContentType);
        document.setSizeBytes((long) bytes.length);
        document.setStorageKey(storageKey);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setDocumentType(documentType);
        ClaimDocument savedDocument = claimDocumentRepository.save(document);

        DocumentJob job = new DocumentJob();
        job.setDocument(savedDocument);
        job.setJobType(JobType.TIKA_EXTRACT);
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        documentJobRepository.save(job);

        if (!"text/plain".equals(sniffedContentType)) {
            DocumentJob malwareScanJob = new DocumentJob();
            malwareScanJob.setDocument(savedDocument);
            malwareScanJob.setJobType(JobType.MALWARE_SCAN);
            malwareScanJob.setStatus(JobStatus.QUEUED);
            malwareScanJob.setAttemptCount(0);
            malwareScanJob.setMaxAttempts(3);
            documentJobRepository.save(malwareScanJob);
        }

        DocumentUploadResponseDTO response = new DocumentUploadResponseDTO();
        response.setDocId(savedDocument.getId());
        response.setStatus(savedDocument.getStatus().name());
        response.setFilename(savedDocument.getOriginalFilename());
        response.setSize(savedDocument.getSizeBytes());
        response.setSniffedType(savedDocument.getSniffedContentType());
        return response;
    }

    public List<ClaimDocumentResponseDTO> listClaimDocuments(Long claimId, String requesterEmail) {
        User requester = authorizationService.requireUser(requesterEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(ClaimNotFoundException::new);
        authorizationService.requireClaimAccess(claim, requester);

        return claimDocumentRepository.findByClaimId(claimId).stream()
                .map(this::mapDocumentToDTO)
                .collect(Collectors.toList());
    }

    public DocumentDownloadDescriptor prepareDocumentDownload(Long docId, String requesterEmail) {
        User requester = authorizationService.requireUser(requesterEmail);
        ClaimDocument document = claimDocumentRepository.findById(docId)
                .orElseThrow(DocumentNotFoundException::new);

        authorizationService.requireClaimAccess(document.getClaim(), requester);

        if (!storageService.exists(document.getStorageKey())) {
            throw new DocumentNotFoundException();
        }

        String contentType = document.getSniffedContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = document.getContentType();
        }

        return new DocumentDownloadDescriptor(
                document.getStorageKey(),
                filenameSanitizer.sanitize(document.getOriginalFilename()),
                normalizeContentType(contentType),
                document.getSizeBytes()
        );
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
        dto.setStatus(claim.getStatus() != null ? claim.getStatus().name() : null);
        dto.setSubmittedAt(claim.getSubmittedAt().toString());
        dto.setSubmittedBy(claim.getUser().getName());

        return dto;
    }

    private ClaimDocumentResponseDTO mapDocumentToDTO(ClaimDocument document) {
        ClaimDocumentResponseDTO dto = new ClaimDocumentResponseDTO();
        dto.setId(document.getId());
        dto.setFilename(document.getOriginalFilename());
        dto.setContentType(document.getContentType());
        dto.setSniffedContentType(document.getSniffedContentType());
        dto.setSizeBytes(document.getSizeBytes());
        dto.setStatus(document.getStatus().name());
        dto.setDocumentType(document.getDocumentType() == null ? null : document.getDocumentType().name());
        dto.setCreatedAt(document.getCreatedAt());
        return dto;
    }

    private boolean isValidTransition(ClaimStatus from, ClaimStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            return to == ClaimStatus.SUBMITTED;
        }
        if (from == to) {
            return true;
        }

        return switch (from) {
            case SUBMITTED -> to == ClaimStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> to == ClaimStatus.APPROVED || to == ClaimStatus.REJECTED;
            case APPROVED -> to == ClaimStatus.PACKAGED;
            case PACKAGED -> to == ClaimStatus.LISTED;
            case LISTED -> to == ClaimStatus.SOLD;
            case REJECTED, SOLD -> false;
        };
    }

    private ClaimStatus parseClaimStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("status is required.");
        }
        try {
            return ClaimStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid status: " + value);
        }
    }

    private DocumentType parseDocumentType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new DocumentValidationException("DOCUMENT_TYPE_REQUIRED", "documentType is required");
        }
        try {
            return DocumentType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new DocumentValidationException("DOCUMENT_TYPE_INVALID", "Invalid documentType: " + value);
        }
    }

    private ClaimStatus decisionToStatus(String decisionValue) {
        String normalized = normalizeDecision(decisionValue);
        return switch (normalized) {
            case "APPROVE" -> ClaimStatus.APPROVED;
            case "REJECT" -> ClaimStatus.REJECTED;
            default -> throw new IllegalArgumentException("decision must be APPROVE or REJECT.");
        };
    }

    private String normalizeDecision(String decisionValue) {
        return decisionValue == null ? "" : decisionValue.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeReviewNotes(String notes) {
        if (notes == null) {
            return null;
        }
        String trimmed = notes.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> normalizeMissingDocs(List<String> missingDocs) {
        if (missingDocs == null || missingDocs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>();
        for (String item : missingDocs) {
            if (item == null) {
                continue;
            }
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize JSON payload.", ex);
        }
    }

    private User requireAdminUser(String email) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        User admin = normalizedEmail == null ? null : userRepository.findByEmail(normalizedEmail);
        if (admin == null) {
            throw new IllegalArgumentException("Admin user not found.");
        }
        if (admin.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Admin role required.");
        }
        return admin;
    }

    private Set<String> parseAllowedTypes() {
        return Arrays.stream(allowedTypesCsv.split(","))
                .map(this::normalizeContentType)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String normalizeContentType(String value) {
        if (value == null || value.isBlank()) {
            return "application/octet-stream";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private byte[] readFileBytesWithLimit(MultipartFile file) {
        long maxPlusOne = maxDocumentBytes + 1;
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream input = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0L;

            while (total < maxPlusOne) {
                int nextReadLength = (int) Math.min(buffer.length, maxPlusOne - total);
                int read = input.read(buffer, 0, nextReadLength);
                if (read == -1) {
                    break;
                }

                output.write(buffer, 0, read);
                total += read;
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file.", e);
        }

        byte[] bytes = output.toByteArray();
        if (bytes.length > maxDocumentBytes) {
            throw new DocumentValidationException(
                    "DOCUMENT_TOO_LARGE",
                    "File exceeds maximum allowed size."
            );
        }

        return bytes;
    }

    public static class ClaimNotFoundException extends RuntimeException {
    }

    public static class DocumentNotFoundException extends RuntimeException {
    }

    public static class DocumentValidationException extends RuntimeException {
        private final String code;

        public DocumentValidationException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    public static class DocumentDownloadDescriptor {
        private final String storageKey;
        private final String originalFilename;
        private final String contentType;
        private final Long sizeBytes;

        public DocumentDownloadDescriptor(String storageKey, String originalFilename, String contentType, Long sizeBytes) {
            this.storageKey = storageKey;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.sizeBytes = sizeBytes;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public String getContentType() {
            return contentType;
        }

        public Long getSizeBytes() {
            return sizeBytes;
        }
    }
}
