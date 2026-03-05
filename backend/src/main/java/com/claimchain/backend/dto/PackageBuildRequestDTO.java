package com.claimchain.backend.dto;

import jakarta.validation.constraints.Size;

public class PackageBuildRequestDTO {

    @Size(max = 5000, message = "notes must be at most 5000 characters")
    private String notes;

    private Boolean dryRun;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getDryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }
}
