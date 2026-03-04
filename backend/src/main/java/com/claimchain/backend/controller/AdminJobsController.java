package com.claimchain.backend.controller;

import com.claimchain.backend.dto.RunJobsResponseDTO;
import com.claimchain.backend.service.DocumentJobRunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobsController {

    private final DocumentJobRunnerService documentJobRunnerService;

    public AdminJobsController(DocumentJobRunnerService documentJobRunnerService) {
        this.documentJobRunnerService = documentJobRunnerService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run-document-jobs")
    public ResponseEntity<RunJobsResponseDTO> runDocumentJobs(
            @RequestParam(name = "limit", defaultValue = "5") int limit
    ) {
        int processed = documentJobRunnerService.runQueuedTikaJobs(limit);
        return ResponseEntity.ok(new RunJobsResponseDTO(processed));
    }
}
