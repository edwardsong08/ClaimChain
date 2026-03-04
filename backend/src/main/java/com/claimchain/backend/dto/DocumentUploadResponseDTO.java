package com.claimchain.backend.dto;

public class DocumentUploadResponseDTO {

    private Long docId;
    private String status;
    private String filename;
    private Long size;
    private String sniffedType;

    public DocumentUploadResponseDTO() {
    }

    public Long getDocId() {
        return docId;
    }

    public void setDocId(Long docId) {
        this.docId = docId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getSniffedType() {
        return sniffedType;
    }

    public void setSniffedType(String sniffedType) {
        this.sniffedType = sniffedType;
    }
}
