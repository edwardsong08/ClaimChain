package com.claimchain.backend.service;

import com.claimchain.backend.model.ClaimDocument;
import com.claimchain.backend.model.DocumentJob;
import com.claimchain.backend.model.DocumentStatus;
import com.claimchain.backend.model.ExtractionStatus;
import com.claimchain.backend.model.JobStatus;
import com.claimchain.backend.model.JobType;
import com.claimchain.backend.repository.ClaimDocumentRepository;
import com.claimchain.backend.repository.DocumentJobRepository;
import com.claimchain.backend.scoring.ScoringEngine;
import com.claimchain.backend.storage.StorageService;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

    private static final Logger log = LoggerFactory.getLogger(DocumentJobRunnerService.class);
    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 100;
    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int MAX_EXTRACTION_ERROR_MESSAGE_LENGTH = 255;

    private final DocumentJobRepository documentJobRepository;
    private final ClaimDocumentRepository claimDocumentRepository;
    private final StorageService storageService;
    private final MalwareScanService malwareScanService;
    private final ScoringEngine scoringEngine;
    private final TransactionTemplate requiresNewTransaction;
    private final Tika tika;

    public DocumentJobRunnerService(
            DocumentJobRepository documentJobRepository,
            ClaimDocumentRepository claimDocumentRepository,
            StorageService storageService,
            MalwareScanService malwareScanService,
            ScoringEngine scoringEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.documentJobRepository = documentJobRepository;
        this.claimDocumentRepository = claimDocumentRepository;
        this.storageService = storageService;
        this.malwareScanService = malwareScanService;
        this.scoringEngine = scoringEngine;
        this.tika = new Tika();

        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public int runQueuedTikaJobs(int limit) {
        return runQueuedJobsByType(JobType.TIKA_EXTRACT, limit);
    }

    @Async
    public void runQueuedTikaJobsForClaimAsync(Long claimId) {
        if (claimId == null) {
            return;
        }
        while (runQueuedJobsByTypeAndClaim(JobType.TIKA_EXTRACT, claimId, MAX_LIMIT) > 0) {
            // Keep draining due queued TIKA jobs for this claim.
        }
    }

    public int runQueuedTikaJobsForClaim(Long claimId, int limit) {
        return runQueuedJobsByTypeAndClaim(JobType.TIKA_EXTRACT, claimId, limit);
    }

    public int runQueuedMalwareScanJobs(int limit) {
        return runQueuedJobsByType(JobType.MALWARE_SCAN, limit);
    }

    private int runQueuedJobsByType(JobType jobType, int limit) {
        return runQueuedJobsByTypeAndClaim(jobType, null, limit);
    }

    private int runQueuedJobsByTypeAndClaim(JobType jobType, Long claimId, int limit) {
        int normalizedLimit = normalizeLimit(limit);
        int processed = 0;

        for (int i = 0; i < normalizedLimit; i++) {
            Boolean claimedAndProcessed = requiresNewTransaction.execute(status -> claimAndProcessSingleJob(jobType, claimId));
            if (!Boolean.TRUE.equals(claimedAndProcessed)) {
                break;
            }
            processed++;
        }

        return processed;
    }

    private boolean claimAndProcessSingleJob(JobType jobType, Long claimId) {
        List<DocumentJob> claimedJobs = claimId == null
                ? documentJobRepository.claimQueuedJobsForType(jobType, 1)
                : documentJobRepository.claimQueuedJobsForClaimAndType(claimId, jobType, 1);
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
            triggerAutoScoringAfterExtraction(document);
            return true;
        }

        processMalwareScanJob(job, document);
        return true;
    }

    private void triggerAutoScoringAfterExtraction(ClaimDocument document) {
        if (document == null || document.getClaim() == null || document.getClaim().getId() == null) {
            return;
        }

        Long claimId = document.getClaim().getId();
        try {
            if (hasPendingTikaJobs(claimId)) {
                return;
            }
            scoringEngine.autoScoreOnDocumentsReadyIfApproved(claimId);
        } catch (Exception ex) {
            log.warn("Auto-scoring on document completion failed for claimId={}", claimId, ex);
        }
    }

    private boolean hasPendingTikaJobs(Long claimId) {
        return documentJobRepository.existsByDocumentClaimIdAndJobTypeAndStatusIn(
                claimId,
                JobType.TIKA_EXTRACT,
                List.of(JobStatus.QUEUED, JobStatus.RUNNING)
        );
    }

    private void processTikaExtractionJob(DocumentJob job, ClaimDocument document) {
        document.setStatus(DocumentStatus.PROCESSING);
        claimDocumentRepository.save(document);

        InputStream storageInputStream;
        try {
            storageInputStream = storageService.load(document.getStorageKey());
        } catch (Exception ex) {
            handleFailure(job, document, ex, "STORAGE_ERROR", true);
            return;
        }

        String extractedText;
        try (InputStream inputStream = storageInputStream) {
            extractedText = tika.parseToString(inputStream);
        } catch (Exception ex) {
            handleFailure(job, document, ex, "TIKA_ERROR", true);
            return;
        }

        String extractedStorageKey = document.getStorageKey() + ".tika.txt";
        try {
            byte[] extractedBytes = extractedText.getBytes(StandardCharsets.UTF_8);
            storageService.save(new ByteArrayInputStream(extractedBytes), extractedStorageKey);
        } catch (Exception ex) {
            handleFailure(job, document, ex, "STORAGE_ERROR", true);
            return;
        }

        Instant completedAt = Instant.now();
        document.setExtractedStorageKey(extractedStorageKey);
        document.setExtractedAt(completedAt);
        document.setStatus(DocumentStatus.READY);
        document.setExtractionStatus(ExtractionStatus.SUCCEEDED);
        document.setExtractedCharCount(extractedText == null || extractedText.isBlank() ? 0 : extractedText.length());
        document.setExtractionErrorCode(null);
        document.setExtractionErrorMessage(null);
        claimDocumentRepository.save(document);

        job.setStatus(JobStatus.DONE);
        job.setLastError(null);
        job.setFinishedAt(completedAt);
        job.setNextRunAt(null);
        documentJobRepository.save(job);
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
            handleFailure(job, document, ex, null, false);
        }
    }

    private void handleFailure(
            DocumentJob job,
            ClaimDocument document,
            Exception ex,
            String errorCode,
            boolean updateExtractionSignals
    ) {
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
            if (updateExtractionSignals) {
                document.setExtractionStatus(ExtractionStatus.TERMINAL_FAILED);
            }
        } else {
            job.setStatus(JobStatus.QUEUED);
            job.setFinishedAt(null);
            job.setNextRunAt(failedAt.plusSeconds(computeBackoffSeconds(nextAttemptCount)));
            document.setStatus(DocumentStatus.QUEUED);
            if (updateExtractionSignals) {
                document.setExtractionStatus(ExtractionStatus.FAILED);
            }
        }

        if (updateExtractionSignals) {
            document.setExtractedCharCount(null);
            document.setExtractionErrorCode(normalizeExtractionErrorCode(errorCode));
            document.setExtractionErrorMessage(truncateExtractionErrorMessage(ex));
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

    private String normalizeExtractionErrorCode(String code) {
        String candidate = code == null || code.isBlank() ? "UNKNOWN_ERROR" : code.trim().toUpperCase();
        if (candidate.length() <= 80) {
            return candidate;
        }
        return candidate.substring(0, 80);
    }

    private String truncateExtractionErrorMessage(Exception ex) {
        String raw = ex.getMessage();
        if (raw == null || raw.isBlank()) {
            raw = ex.getClass().getSimpleName();
        }
        if (raw.length() <= MAX_EXTRACTION_ERROR_MESSAGE_LENGTH) {
            return raw;
        }
        return raw.substring(0, MAX_EXTRACTION_ERROR_MESSAGE_LENGTH);
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
