package com.claimchain.backend.exception;

import com.claimchain.backend.config.RequestIdFilter;
import com.claimchain.backend.dto.ApiErrorResponse;
import com.claimchain.backend.ruleset.RulesetValidationException;
import com.claimchain.backend.service.ClaimService;
import com.claimchain.backend.service.PackageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<String> details = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            details.add(fe.getField() + ": " + (fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
        }
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed JSON request", List.of("Request body is unreadable"), request);
    }

    @ExceptionHandler(RulesetValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleRulesetValidation(
            RulesetValidationException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getErrors() == null || ex.getErrors().isEmpty()
                ? List.of("Ruleset config is invalid.")
                : ex.getErrors();
        return build(HttpStatus.BAD_REQUEST, "RULESET_INVALID", "Ruleset config is invalid.", details, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage(), List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(AuthTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthToken(AuthTokenException ex, HttpServletRequest request) {
        return build(
                HttpStatus.BAD_REQUEST,
                ex.getCode(),
                ex.getMessage(),
                List.of(ex.getMessage()),
                request
        );
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex,
            HttpServletRequest request
    ) {
        String email = ex.getEmail() == null || ex.getEmail().isBlank() ? null : ex.getEmail();
        List<String> details = email == null ? List.of(ex.getMessage()) : List.of("email: " + email);
        return build(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", ex.getMessage(), details, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", List.of("You do not have permission to access this resource"), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required", List.of("Missing or invalid credentials"), request);
    }

    @ExceptionHandler(PackageService.PackageValidationException.class)
    public ResponseEntity<ApiErrorResponse> handlePackageValidation(
            PackageService.PackageValidationException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(PackageService.PackageConflictException.class)
    public ResponseEntity<ApiErrorResponse> handlePackageConflict(
            PackageService.PackageConflictException ex,
            HttpServletRequest request
    ) {
        List<String> details = ex.getDetails() == null || ex.getDetails().isEmpty()
                ? List.of(ex.getMessage())
                : ex.getDetails();
        return build(HttpStatus.CONFLICT, ex.getCode(), ex.getMessage(), details, request);
    }

    @ExceptionHandler(ClaimService.ClaimFrozenException.class)
    public ResponseEntity<ApiErrorResponse> handleClaimFrozen(
            ClaimService.ClaimFrozenException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.CONFLICT, "CLAIM_FROZEN", ex.getMessage(), List.of(ex.getMessage()), request);
    }

    @ExceptionHandler({
            PackageService.PackageNotFoundException.class,
            PackageService.ClaimNotFoundException.class,
            ClaimService.ClaimNotFoundException.class
    })
    public ResponseEntity<ApiErrorResponse> handlePackageNotFound(
            RuntimeException ex,
            HttpServletRequest request
    ) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(), List.of(ex.getMessage()), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(
            Exception ex,
            HttpServletRequest request
    ) {
        // Do not leak internal exception messages to clients
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", List.of("Contact support with requestId"), request);
    }

    private ResponseEntity<ApiErrorResponse> build(
            HttpStatus status,
            String code,
            String message,
            List<String> details,
            HttpServletRequest request
    ) {
        Object rid = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        String requestId = rid == null ? null : rid.toString();

        ApiErrorResponse body = new ApiErrorResponse(
                code,
                message,
                details,
                Instant.now(),
                requestId
        );

        return ResponseEntity.status(status).body(body);
    }
}
