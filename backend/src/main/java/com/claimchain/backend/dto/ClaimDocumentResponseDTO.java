package com.claimchain.backend.dto;

import java.time.Instant;

public class ClaimDocumentResponseDTO {

    private Long id;
    private String filename;
    private String contentType;
    private String sniffedContentType;
    private Long sizeBytes;
    private String status;
    private String documentType;
    private String extractionStatus;
    private Integer extractedCharCount;
    private Instant createdAt;

    public ClaimDocumentResponseDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSniffedContentType() {
        return sniffedContentType;
    }

    public void setSniffedContentType(String sniffedContentType) {
        this.sniffedContentType = sniffedContentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getExtractionStatus() {
        return extractionStatus;
    }

    public void setExtractionStatus(String extractionStatus) {
        this.extractionStatus = extractionStatus;
    }

    public Integer getExtractedCharCount() {
        return extractedCharCount;
    }

    public void setExtractedCharCount(Integer extractedCharCount) {
        this.extractedCharCount = extractedCharCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
