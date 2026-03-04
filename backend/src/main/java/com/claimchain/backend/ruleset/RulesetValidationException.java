package com.claimchain.backend.ruleset;

import java.util.List;

public class RulesetValidationException extends RuntimeException {

    private final List<String> errors;

    public RulesetValidationException(List<String> errors) {
        super("Ruleset config is invalid.");
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
