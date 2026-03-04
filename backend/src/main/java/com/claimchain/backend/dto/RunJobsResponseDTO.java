package com.claimchain.backend.dto;

public class RunJobsResponseDTO {

    private int processed;

    public RunJobsResponseDTO() {
    }

    public RunJobsResponseDTO(int processed) {
        this.processed = processed;
    }

    public int getProcessed() {
        return processed;
    }

    public void setProcessed(int processed) {
        this.processed = processed;
    }
}
