package com.claimchain.backend.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class AdminClaimDecisionRequestDTO {

    @NotBlank(message = "decision is required")
    private String decision;

    private String notes;

    private List<String> missingDocs;

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<String> getMissingDocs() {
        return missingDocs;
    }

    public void setMissingDocs(List<String> missingDocs) {
        this.missingDocs = missingDocs;
    }
}
