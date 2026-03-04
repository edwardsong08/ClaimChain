package com.claimchain.backend.service;

import com.claimchain.backend.dto.ClaimDocumentResponseDTO;
import com.claimchain.backend.dto.ClaimRequestDTO;
import com.claimchain.backend.dto.ClaimResponseDTO;
import com.claimchain.backend.dto.DocumentUploadResponseDTO;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.security.AuthorizationService;
import com.claimchain.backend.storage.StorageService;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    public ClaimResponseDTO getClaimById(Long claimId, String requesterEmail) {
        User requester = authorizationService.requireUser(requesterEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(ClaimNotFoundException::new);

        authorizationService.requireClaimAccess(claim, requester);
        return mapToDTO(claim);
    }

    public DocumentUploadResponseDTO uploadClaimDocument(Long claimId, MultipartFile file, String requesterEmail) {
        User requester = authorizationService.requireUser(requesterEmail);
        Claim claim = claimRepository.findById(claimId)
                .orElseThrow(ClaimNotFoundException::new);
        authorizationService.requireClaimAccess(claim, requester);

        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("DOCUMENT_EMPTY", "File must not be empty.");
        }
        if (file.getSize() > maxDocumentBytes) {
            throw new DocumentValidationException(
                    "DOCUMENT_TOO_LARGE",
                    "File exceeds maximum allowed size."
            );
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file.", e);
        }
        if (bytes.length == 0) {
            throw new DocumentValidationException("DOCUMENT_EMPTY", "File must not be empty.");
        }

        String originalFilename = normalizeOriginalFilename(file.getOriginalFilename());
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
        ClaimDocument savedDocument = claimDocumentRepository.save(document);

        DocumentJob job = new DocumentJob();
        job.setDocument(savedDocument);
        job.setJobType(JobType.TIKA_EXTRACT);
        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setMaxAttempts(3);
        documentJobRepository.save(job);

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
                document.getOriginalFilename(),
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
        dto.setStatus(claim.getStatus());
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
        dto.setCreatedAt(document.getCreatedAt());
        return dto;
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

    private String normalizeOriginalFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload.bin";
        }
        return filename.trim();
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
