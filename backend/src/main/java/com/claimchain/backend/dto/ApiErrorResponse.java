package com.claimchain.backend.dto;

import java.time.Instant;
import java.util.List;

public class ApiErrorResponse {

    private String code;
    private String message;
    private List<String> details;
    private Instant timestamp;
    private String requestId;

    public ApiErrorResponse() {}

    public ApiErrorResponse(String code, String message, List<String> details, Instant timestamp, String requestId) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = timestamp;
        this.requestId = requestId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getDetails() {
        return details;
    }

    public void setDetails(List<String> details) {
        this.details = details;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
