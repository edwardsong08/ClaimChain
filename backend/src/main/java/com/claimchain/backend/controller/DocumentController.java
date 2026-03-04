package com.claimchain.backend.controller;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.service.ClaimService;
import com.claimchain.backend.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final ClaimService claimService;
    private final StorageService storageService;

    public DocumentController(ClaimService claimService, StorageService storageService) {
        this.claimService = claimService;
        this.storageService = storageService;
    }

    @PreAuthorize("hasAnyRole('SERVICE_PROVIDER','ADMIN')")
    @GetMapping("/{docId}/download")
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable Long docId, java.security.Principal principal) {
        ClaimService.DocumentDownloadDescriptor descriptor = claimService.prepareDocumentDownload(docId, principal.getName());

        String filename = descriptor.getOriginalFilename().replace("\"", "");
        String contentDisposition = "attachment; filename=\"" + filename + "\"";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(MediaType.parseMediaType(descriptor.getContentType()))
                .contentLength(descriptor.getSizeBytes())
                .body(new InputStreamResource(storageService.load(descriptor.getStorageKey())));
    }

    @ExceptionHandler({ClaimService.ClaimNotFoundException.class, ClaimService.DocumentNotFoundException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);

        ApiErrorResponse body = new ApiErrorResponse(
                "NOT_FOUND",
                "Resource not found",
                List.of("Document not found"),
                Instant.now(),
                requestId
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }
}
