package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentType;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "documents.max-bytes=128",
        "storage.local.base-dir=storage/test-document-endpoints"
})
class DocumentEndpointsIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String OWNER_EMAIL = "owner@document-endpoints-test.local";
    private static final String OTHER_PROVIDER_EMAIL = "other-provider@document-endpoints-test.local";
    private static final String ADMIN_EMAIL = "admin@document-endpoints-test.local";
    private static final String COLLECTION_EMAIL = "collector@document-endpoints-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@document-endpoints-test.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private ClaimDocumentRepository claimDocumentRepository;

    @Autowired
    private DocumentJobRepository documentJobRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long claimId;
    private Long ownerUserId;
    private String ownerToken;
    private String otherProviderToken;
    private String adminToken;
    private String collectionToken;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();

        User owner = createUser("Owner Provider", OWNER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        User otherProvider = createUser("Other Provider", OTHER_PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);
        User admin = createUser("Admin User", ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        createUser("Collection User", COLLECTION_EMAIL, Role.COLLECTION_AGENCY, VerificationStatus.APPROVED);
        ownerUserId = owner.getId();

        Claim claim = new Claim();
        claim.setUser(owner);
        claim.setClientName("Document Claim Client");
        claim.setClientContact("client@example.com");
        claim.setClientAddress("42 File St");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Contacted via phone");
        claim.setAmountOwed(new BigDecimal("99.99"));
        claim.setDateOfDefault(LocalDate.of(2026, 1, 15));
        claim.setContractFileKey("document-test-contract");
        claimId = claimRepository.saveAndFlush(claim).getId();

        ownerToken = loginAndGetAccessToken(owner.getEmail());
        otherProviderToken = loginAndGetAccessToken(otherProvider.getEmail());
        adminToken = loginAndGetAccessToken(admin.getEmail());
        collectionToken = loginAndGetAccessToken(COLLECTION_EMAIL);
    }

    @Test
    void upload_requiresAuthAndServiceProviderRole() throws Exception {
        MockMultipartFile file = pngFile("upload-role-test.png", minimalPngBytes());

        MvcResult noTokenResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .param("documentType", "INVOICE")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(noTokenResult, "UNAUTHORIZED");

        MvcResult collectionResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + collectionToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(collectionResult, "FORBIDDEN");
    }

    @Test
    void nonOwnerProvider_cannotUploadListOrDownload() throws Exception {
        MockMultipartFile file = pngFile("upload-non-owner.png", minimalPngBytes());

        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherProviderToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(uploadResult, "NOT_FOUND");

        MvcResult listResult = mockMvc.perform(
                        get("/api/claims/{id}/documents", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherProviderToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(listResult, "NOT_FOUND");

        Long documentId = uploadAsOwnerAndReturnDocId(
                "upload-owner-for-download.png",
                minimalPngBytes(),
                "upload-owner-for-download.png"
        );
        MvcResult downloadResult = mockMvc.perform(
                        get("/api/documents/{docId}/download", documentId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherProviderToken)
                )
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(downloadResult, "NOT_FOUND");
    }

    @Test
    void ownerProvider_canUploadAndList_andCreatesDocumentAndJobRows() throws Exception {
        byte[] bytes = minimalPngBytes();
        Long documentId = uploadAsOwnerAndReturnDocId("../owner-list-test.png", bytes, "owner-list-test.png");

        ClaimDocument savedDocument = claimDocumentRepository.findById(documentId).orElseThrow();
        assertThat(savedDocument.getClaim().getId()).isEqualTo(claimId);
        assertThat(savedDocument.getUploadedByUser().getId()).isEqualTo(ownerUserId);
        assertThat(savedDocument.getSniffedContentType()).isEqualTo("image/png");
        assertThat(savedDocument.getSizeBytes()).isEqualTo((long) bytes.length);
        assertThat(savedDocument.getOriginalFilename()).isEqualTo("owner-list-test.png");
        assertThat(savedDocument.getDocumentType()).isEqualTo(DocumentType.INVOICE);

        List<DocumentJob> jobs = documentJobRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(DocumentJob::getStatus)
                .containsOnly(JobStatus.QUEUED);
        assertThat(jobs).extracting(DocumentJob::getJobType)
                .containsExactlyInAnyOrder(JobType.TIKA_EXTRACT, JobType.MALWARE_SCAN);

        mockMvc.perform(
                        get("/api/claims/{id}/documents", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(documentId.intValue()))
                .andExpect(jsonPath("$[0].filename").value("owner-list-test.png"))
                .andExpect(jsonPath("$[0].sniffedContentType").value("image/png"))
                .andExpect(jsonPath("$[0].documentType").value("INVOICE"))
                .andExpect(jsonPath("$[0].status").value("UPLOADED"));
    }

    @Test
    void download_returnsFileBytesAndHeaders() throws Exception {
        byte[] bytes = minimalPngBytes();
        Long documentId = uploadAsOwnerAndReturnDocId("../download-test.png", bytes, "download-test.png");

        MvcResult downloadResult = mockMvc.perform(
                        get("/api/documents/{docId}/download", documentId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header -> assertThat(header.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
                        .isEqualTo("attachment; filename=\"download-test.png\""))
                .andReturn();

        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void upload_rejectsDisallowedTypeAndOversizedPayload_withApiErrorShape() throws Exception {
        MockMultipartFile disallowedFile = new MockMultipartFile(
                "file",
                "not-allowed.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "plain text file".getBytes()
        );

        MvcResult disallowedResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(disallowedFile)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(disallowedResult, "DOCUMENT_TYPE_NOT_ALLOWED");

        byte[] oversized = new byte[129];
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file",
                "too-large.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                oversized
        ) {
            @Override
            public long getSize() {
                return 1L;
            }
        };

        MvcResult oversizedResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(oversizedFile)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(oversizedResult, "DOCUMENT_TOO_LARGE");
    }

    @Test
    void upload_rejectsMissingAndInvalidDocumentType() throws Exception {
        MockMultipartFile file = pngFile("typed-upload-test.png", minimalPngBytes());

        MvcResult missingTypeResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(missingTypeResult, "DOCUMENT_TYPE_REQUIRED");

        MvcResult invalidTypeResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .param("documentType", "NOT_A_TYPE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        assertApiError(invalidTypeResult, "DOCUMENT_TYPE_INVALID");
    }

    @Test
    void admin_canListAndDownload_anyClaimDocument() throws Exception {
        byte[] bytes = minimalPngBytes();
        Long documentId = uploadAsOwnerAndReturnDocId("admin-access.png", bytes, "admin-access.png");

        mockMvc.perform(
                        get("/api/claims/{id}/documents", claimId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(documentId.intValue()));

        MvcResult downloadResult = mockMvc.perform(
                        get("/api/documents/{docId}/download", documentId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andReturn();

        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(bytes);
    }

    private void cleanupTestData() {
        jdbcTemplate.update(
                "UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM document_jobs WHERE document_id IN (" +
                        "SELECT d.id FROM claim_documents d " +
                        "JOIN claims c ON d.claim_id = c.id " +
                        "JOIN users u ON c.user_id = u.id " +
                        "WHERE u.email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM claim_documents WHERE claim_id IN (" +
                        "SELECT c.id FROM claims c JOIN users u ON c.user_id = u.id WHERE u.email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", TEST_EMAIL_PATTERN);
    }

    private User createUser(String name, String email, Role role, VerificationStatus verificationStatus) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setVerificationStatus(verificationStatus);
        if (verificationStatus == VerificationStatus.APPROVED) {
            user.setVerifiedAt(Instant.now());
        }
        return userRepository.save(user);
    }

    private String loginAndGetAccessToken(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode json = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }

    private Long uploadAsOwnerAndReturnDocId(String filename, byte[] bytes, String expectedPersistedFilename) throws Exception {
        MockMultipartFile file = pngFile(filename, bytes);
        MvcResult uploadResult = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .param("documentType", "INVOICE")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode uploadBody = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        assertThat(uploadBody.get("status").asText()).isEqualTo("UPLOADED");
        assertThat(uploadBody.get("filename").asText()).isEqualTo(expectedPersistedFilename);
        assertThat(uploadBody.get("size").asLong()).isEqualTo(bytes.length);
        assertThat(uploadBody.get("sniffedType").asText()).isEqualTo("image/png");

        return uploadBody.get("docId").asLong();
    }

    private MockMultipartFile pngFile(String filename, byte[] bytes) {
        return new MockMultipartFile("file", filename, "image/png", bytes);
    }

    private byte[] minimalPngBytes() {
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jxX8AAAAASUVORK5CYII="
        );
    }

    private void assertApiError(MvcResult result, String expectedCode) throws Exception {
        ApiErrorResponse error = objectMapper.readValue(result.getResponse().getContentAsString(), ApiErrorResponse.class);
        assertThat(error.getCode()).isEqualTo(expectedCode);
        assertThat(error.getRequestId()).isNotBlank();
    }
}
