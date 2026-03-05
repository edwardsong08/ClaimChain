package com.claimchain.backend.dto;

import jakarta.validation.constraints.Size;

public class PackageCreateRequestDTO {

    @Size(max = 5000, message = "notes must be at most 5000 characters")
    private String notes;

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
