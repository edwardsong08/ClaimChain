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
    private final MalwareScanService malwareScanService;
    private final TransactionTemplate requiresNewTransaction;
    private final Tika tika;

    public DocumentJobRunnerService(
            DocumentJobRepository documentJobRepository,
            ClaimDocumentRepository claimDocumentRepository,
            StorageService storageService,
            MalwareScanService malwareScanService,
            PlatformTransactionManager transactionManager
    ) {
        this.documentJobRepository = documentJobRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.storageService = storageService;
        this.malwareScanService = malwareScanService;
        this.tika = new Tika();

        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int runQueuedTikaJobs(int limit) {
        return runQueuedJobsByType(JobType.TIKA_EXTRACT, limit);
    }

    public int runQueuedMalwareScanJobs(int limit) {
        return runQueuedJobsByType(JobType.MALWARE_SCAN, limit);
    }

    private int runQueuedJobsByType(JobType jobType, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        int processed = 0;

        for (int i = 0; i < normalizedLimit; i++) {
            Boolean claimedAndProcessed = requiresNewTransaction.execute(status -> claimAndProcessSingleJob(jobType));
            if (!Boolean.TRUE.equals(claimedAndProcessed)) {
                break;
            }
            processed++;
        }

        return processed;
    }

    private boolean claimAndProcessSingleJob(JobType jobType) {
        List<DocumentJob> claimedJobs = documentJobRepository.claimQueuedJobsForType(jobType, 1);
        if (claimedJobs.isEmpty()) {
            return false;
        }

        DocumentJob job = claimedJobs.get(0);

        ClaimDocument document = job.getDocument();
        Instant startedAt = Instant.now();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(startedAt);
        job.setFinishedAt(null);
        job.setNextRunAt(null);
        documentJobRepository.save(job);

        if (jobType == JobType.TIKA_EXTRACT) {
            processTikaExtractionJob(job, document);
            return true;
        }

        processMalwareScanJob(job, document);
        return true;
    }

    private void processTikaExtractionJob(DocumentJob job, ClaimDocument document) {
        document.setStatus(DocumentStatus.PROCESSING);
        claimDocumentRepository.save(document);

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
            job.setNextRunAt(null);
            documentJobRepository.save(job);
        } catch (Exception ex) {
            handleFailure(job, document, ex);
        }
    }

    private void processMalwareScanJob(DocumentJob job, ClaimDocument document) {
        try (InputStream inputStream = storageService.load(document.getStorageKey())) {
            byte[] bytes = inputStream.readAllBytes();
            MalwareScanResult result = malwareScanService.scan(bytes, document.getSniffedContentType());

            if (result != MalwareScanResult.CLEAN) {
                throw new RuntimeException("Malware scan did not return CLEAN.");
            }

            Instant completedAt = Instant.now();
            job.setStatus(JobStatus.DONE);
            job.setLastError(null);
            job.setFinishedAt(completedAt);
            job.setNextRunAt(null);
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

        if (nextAttemptCount >= maxAttempts) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(failedAt);
            job.setNextRunAt(null);
            document.setStatus(DocumentStatus.FAILED);
        } else {
            job.setStatus(JobStatus.QUEUED);
            job.setFinishedAt(null);
            job.setNextRunAt(failedAt.plusSeconds(computeBackoffSeconds(nextAttemptCount)));
            document.setStatus(DocumentStatus.QUEUED);
        }

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

    private long computeBackoffSeconds(int attemptCount) {
        long delay = 30L;
        for (int i = 1; i < attemptCount; i++) {
            delay = Math.min(600L, delay * 2L);
            if (delay == 600L) {
                break;
            }
        }
        return delay;
    }
}
