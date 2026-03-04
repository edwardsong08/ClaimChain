package com.claimchain.backend.service;

import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.storage.StorageService;
import org.apache.tika.Tika;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class DocumentJobRunnerService {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 2000;

    private final DocumentJobRepository documentJobRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final StorageService storageService;
    private final TransactionTemplate requiresNewTransaction;
    private final Tika tika;

    public DocumentJobRunnerService(
            DocumentJobRepository documentJobRepository,
            ClaimDocumentRepository claimDocumentRepository,
            StorageService storageService,
            PlatformTransactionManager transactionManager
    ) {
        this.documentJobRepository = documentJobRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.storageService = storageService;
        this.tika = new Tika();

        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int runQueuedTikaJobs(int limit) {
        int normalizedLimit = normalizeLimit(limit);

        List<Long> jobIds = documentJobRepository
                .findByStatusAndJobTypeOrderByCreatedAtAsc(
                        JobStatus.QUEUED,
                        JobType.TIKA_EXTRACT,
                        PageRequest.of(0, normalizedLimit)
                )
                .stream()
                .map(DocumentJob::getId)
                .toList();

        for (Long jobId : jobIds) {
            requiresNewTransaction.executeWithoutResult(status -> processSingleJob(jobId));
        }

        return jobIds.size();
    }

    private void processSingleJob(Long jobId) {
        DocumentJob job = documentJobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        if (job.getStatus() != JobStatus.QUEUED || job.getJobType() != JobType.TIKA_EXTRACT) {
            return;
        }

        ClaimDocument document = job.getDocument();
        Instant startedAt = Instant.now();

        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setFinishedAt(null);

        document.setStatus(DocumentStatus.PROCESSING);
        claimDocumentRepository.save(document);
        documentJobRepository.save(job);

        try (InputStream inputStream = storageService.load(document.getStorageKey())) {
            String extractedText = tika.parseToString(inputStream);
            String extractedStorageKey = document.getStorageKey() + ".tika.txt";

            byte[] extractedBytes = extractedText.getBytes(StandardCharsets.UTF_8);
            storageService.save(new ByteArrayInputStream(extractedBytes), extractedStorageKey);

            Instant completedAt = Instant.now();
            document.setExtractedStorageKey(extractedStorageKey);
            document.setExtractedAt(completedAt);
            document.setStatus(DocumentStatus.READY);
            claimDocumentRepository.save(document);

            job.setStatus(JobStatus.DONE);
            job.setLastError(null);
            job.setFinishedAt(completedAt);
            documentJobRepository.save(job);
        } catch (Exception ex) {
            handleFailure(job, document, ex);
        }
    }

    private void handleFailure(DocumentJob job, ClaimDocument document, Exception ex) {
        int attemptCount = job.getAttemptCount() == null ? 0 : job.getAttemptCount();
        int nextAttemptCount = attemptCount + 1;
        int maxAttempts = job.getMaxAttempts() == null ? DEFAULT_MAX_ATTEMPTS : job.getMaxAttempts();
        Instant failedAt = Instant.now();

        job.setAttemptCount(nextAttemptCount);
        job.setLastError(truncateError(ex));
        job.setFinishedAt(failedAt);

        if (nextAttemptCount >= maxAttempts) {
            job.setStatus(JobStatus.FAILED);
        } else {
            job.setStatus(JobStatus.QUEUED);
        }

        document.setStatus(DocumentStatus.FAILED);
        claimDocumentRepository.save(document);
        documentJobRepository.save(job);
    }

    private String truncateError(Exception ex) {
        String raw = ex.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = ex.getClass().getSimpleName();
        }
        if (raw.length() <= MAX_ERROR_LENGTH) {
            return raw;
        }
        return raw.substring(0, MAX_ERROR_LENGTH);
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
