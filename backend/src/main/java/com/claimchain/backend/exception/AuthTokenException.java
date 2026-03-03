package com.claimchain.backend.exception;

public class AuthTokenException extends RuntimeException {

    private final String code;

    public AuthTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}