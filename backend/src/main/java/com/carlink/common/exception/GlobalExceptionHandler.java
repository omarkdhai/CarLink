package com.carlink.common.exception;

import com.carlink.common.dto.ApiError;
import com.carlink.security.monitoring.SecurityEvents;
import com.carlink.security.monitoring.SecurityEvents.Type;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Set;

/**
 * Central exception handling. Never leaks internal details, stack traces,
 * tokens, phone numbers or message content in responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Security plan S-25: errors that matter for alerting are counted and logged here, centrally. */
    private final SecurityEvents securityEvents;

    public GlobalExceptionHandler(SecurityEvents securityEvents) {
        this.securityEvents = securityEvents;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        switch (ex.getCode()) {
            case "INVALID_CREDENTIALS" -> securityEvents.record(Type.LOGIN_FAILURE);
            case "TOO_MANY_LOGIN_ATTEMPTS" -> securityEvents.record(Type.LOGIN_BLOCKED);
            case "CAPTCHA_FAILED" -> securityEvents.record(Type.CAPTCHA_FAILED);
            case "CAPTCHA_REQUIRED" -> securityEvents.record(Type.LOGIN_CHALLENGED);
            default -> { }
        }
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    /**
     * 429 does not name the offending key (IP or QR token) — just a retry
     * window. Never leaks rate-limit subjects back to the client.
     */
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException ex,
                                                          HttpServletRequest request) {
        // The route pattern (e.g. /api/v1/auth/login), never the raw path: bounded, no tokens.
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        securityEvents.record(Type.RATE_LIMITED, pattern == null ? "unknown" : pattern.toString());
        return ResponseEntity.status(429)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiError.of(429, ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withFieldErrors(400, "VALIDATION_ERROR",
                        "Validation failed", errors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withFieldErrors(400, "VALIDATION_ERROR",
                        "Validation failed", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        Set<ConstraintViolation<?>> violations = ex.getConstraintViolations();
        List<ApiError.FieldError> errors = violations.stream()
                .map(v -> new ApiError.FieldError(
                        v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest()
                .body(ApiError.withFieldErrors(400, "VALIDATION_ERROR",
                        "Validation failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex) {
        // Same message as "account disabled" / "not found" — no user enumeration.
        return ResponseEntity.status(401)
                .body(ApiError.of(401, "INVALID_CREDENTIALS",
                        "Invalid email or password"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403)
                .body(ApiError.of(403, "FORBIDDEN", "Access denied"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiError.of(400, "MALFORMED_REQUEST", "Malformed request body"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405)
                .body(ApiError.of(405, "METHOD_NOT_ALLOWED", "Method not allowed"));
    }

    // Client mistakes that used to fall through to the catch-all 500 (pentest
    // 2026-11-19): anyone could trigger them, unauthenticated for the media
    // type, which counted as server errors, logged stack traces and could
    // fire the 5xx-burst alert. They are plain 4xx now.

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415)
                .body(ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", "Send the request as application/json"));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(406)
                .body(ApiError.of(406, "NOT_ACCEPTABLE", "Requested response format is not available"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    public ResponseEntity<ApiError> handleBadParameter(Exception ex) {
        // Never echo the parameter value back: it may be a token.
        return ResponseEntity.status(400)
                .body(ApiError.of(400, "BAD_REQUEST", "Invalid or missing request parameter"));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404)
                .body(ApiError.of(404, "NOT_FOUND", "Resource not found"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        // Log the stack trace server-side only; never return it to the client.
        log.error("Unexpected error", ex);
        securityEvents.record(Type.SERVER_ERROR, ex.getClass().getSimpleName());
        return ResponseEntity.status(500)
                .body(ApiError.of(500, "INTERNAL_ERROR",
                        "An unexpected error occurred"));
    }
}