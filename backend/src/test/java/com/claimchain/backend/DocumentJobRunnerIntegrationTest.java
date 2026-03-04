package com.claimchain.backend;

import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.model.Claim;
import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.model.Role;
import com.claimchain.backend.model.User;
import com.claimchain.backend.model.VerificationStatus;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.ClaimRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.repository.UserRepository;
import com.claimchain.backend.storage.StorageService;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "storage.local.base-dir=storage/test-document-job-runner",
        "documents.allowed-types=application/pdf,image/png,image/jpeg,text/plain"
})
class DocumentJobRunnerIntegrationTest {

    private static final String PASSWORD = "Password123!";
    private static final String ADMIN_EMAIL = "admin@document-job-runner-test.local";
    private static final String PROVIDER_EMAIL = "provider@document-job-runner-test.local";
    private static final String TEST_EMAIL_PATTERN = "%@document-job-runner-test.local";
    private static final Path STORAGE_BASE_DIR = Path.of("storage/test-document-job-runner");

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
    private StorageService storageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String providerToken;
    private Long documentId;
    private Long jobId;
    private String sourceText;

    @BeforeEach
    void setUp() throws Exception {
        cleanupTestData();
        cleanupStorage();

        User admin = createUser("Runner Admin", ADMIN_EMAIL, Role.ADMIN, VerificationStatus.APPROVED);
        User provider = createUser("Runner Provider", PROVIDER_EMAIL, Role.SERVICE_PROVIDER, VerificationStatus.APPROVED);

        Claim claim = new Claim();
        claim.setUser(provider);
        claim.setClientName("Runner Client");
        claim.setClientContact("runner-client@example.com");
        claim.setClientAddress("500 Runner Ave");
        claim.setDebtType("CONSUMER");
        claim.setContactHistory("Reminder sent");
        claim.setAmountOwed(new BigDecimal("150.00"));
        claim.setDateOfDefault(LocalDate.of(2026, 2, 1));
        claim.setContractFileKey("runner-contract-key");
        Long claimId = claimRepository.saveAndFlush(claim).getId();

        adminToken = loginAndGetAccessToken(admin.getEmail());
        providerToken = loginAndGetAccessToken(provider.getEmail());

        sourceText = "Hello from Tika job runner integration test.";
        documentId = uploadTextDocument(claimId, sourceText);

        List<DocumentJob> jobs = documentJobRepository.findByDocumentIdOrderByCreatedAtDesc(documentId);
        assertThat(jobs).hasSize(1);
        DocumentJob job = jobs.get(0);
        assertThat(job.getJobType()).isEqualTo(JobType.TIKA_EXTRACT);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        jobId = job.getId();

        ClaimDocument document = claimDocumentRepository.findById(documentId).orElseThrow();
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    }

    @Test
    void adminRunEndpoint_processesQueuedTikaJob_andPersistsExtractionOutput() throws Exception {
        mockMvc.perform(
                        post("/api/admin/jobs/run-document-jobs")
                                .param("limit", "5")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.processed").value(1));

        DocumentJob updatedJob = documentJobRepository.findById(jobId).orElseThrow();
        assertThat(updatedJob.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(updatedJob.getFinishedAt()).isNotNull();

        ClaimDocument updatedDocument = claimDocumentRepository.findById(documentId).orElseThrow();
        assertThat(updatedDocument.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(updatedDocument.getExtractedStorageKey()).isNotBlank();
        assertThat(updatedDocument.getExtractedAt()).isNotNull();
        assertThat(storageService.exists(updatedDocument.getExtractedStorageKey())).isTrue();

        String extractedText;
        try (InputStream inputStream = storageService.load(updatedDocument.getExtractedStorageKey())) {
            extractedText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(extractedText).contains("Hello from Tika job runner integration test");
    }

    @Test
    void nonAdminRunEndpointCall_returns403() throws Exception {
        MvcResult forbiddenResult = mockMvc.perform(
                        post("/api/admin/jobs/run-document-jobs")
                                .param("limit", "5")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        ApiErrorResponse error = objectMapper.readValue(
                forbiddenResult.getResponse().getContentAsString(),
                ApiErrorResponse.class
        );
        assertThat(error.getCode()).isEqualTo("FORBIDDEN");
        assertThat(error.getRequestId()).isNotBlank();
    }

    private Long uploadTextDocument(Long claimId, String textContent) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "runner-input.txt",
                MediaType.TEXT_PLAIN_VALUE,
                textContent.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/claims/{id}/documents", claimId)
                                .file(file)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerToken)
                )
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("docId").asLong();
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

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM document_jobs");
        jdbcTemplate.update("DELETE FROM claim_documents");

        jdbcTemplate.update(
                "UPDATE admin_bootstrap_state SET used_by_user_id = NULL WHERE used_by_user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "UPDATE users SET verified_by = NULL WHERE verified_by IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update(
                "DELETE FROM claims WHERE user_id IN (SELECT id FROM users WHERE email LIKE ?)",
                TEST_EMAIL_PATTERN
        );
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", TEST_EMAIL_PATTERN);
    }

    private void cleanupStorage() {
        if (Files.notExists(STORAGE_BASE_DIR)) {
            return;
        }

        try (var paths = Files.walk(STORAGE_BASE_DIR)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
