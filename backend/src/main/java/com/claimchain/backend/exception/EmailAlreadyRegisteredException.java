package com.claimchain.backend.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "This email already has an account. Please sign in. If you need access as both Provider and Buyer, request an additional role.";

    private final String email;

    public EmailAlreadyRegisteredException(String email) {
        super(DEFAULT_MESSAGE);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
