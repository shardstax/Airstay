package com.shardx.hotelManagement.airBnb.advice;

import com.shardx.hotelManagement.airBnb.exception.BadRequestException;
import com.shardx.hotelManagement.airBnb.exception.ConflictException;
import com.shardx.hotelManagement.airBnb.exception.PaymentProviderException;
import com.shardx.hotelManagement.airBnb.exception.RateLimitExceededException;
import com.shardx.hotelManagement.airBnb.exception.ResourceNotFoundException;
import com.shardx.hotelManagement.airBnb.exception.UnAuthorisedException;
import com.shardx.hotelManagement.airBnb.ai.AiSearchUnavailableException;
import com.shardx.hotelManagement.airBnb.places.PlaceLookupException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * Maps every failure to the standard ApiResponse envelope with the right HTTP status.
 * Client errors are 4xx with a useful message; unexpected errors are 500 with a generic message
 * (the details only go to the log).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleResourceNotFound(ResourceNotFoundException exception) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<?>> handleBadRequest(BadRequestException exception) {
        return build(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /** Request is valid but the resource is in the wrong state (expired hold, sold out, already active, ...). */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<?>> handleConflict(ConflictException exception) {
        return build(HttpStatus.CONFLICT, exception.getMessage());
    }

    /** 429 plus a Retry-After header, so well-behaved clients know when to try again. */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleRateLimit(RateLimitExceededException exception) {
        ApiError apiError = ApiError.builder()
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .message(exception.getMessage())
                .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(new ApiResponse<>(apiError));
    }

    @ExceptionHandler(PlaceLookupException.class)
    public ResponseEntity<ApiResponse<?>> handlePlaceLookup(PlaceLookupException exception) {
        log.warn("Place provider unavailable: {}", exception.getMessage(), exception.getCause());
        return build(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage() + ", please try again in a moment");
    }

    @ExceptionHandler(PaymentProviderException.class)
    public ResponseEntity<ApiResponse<?>> handlePaymentProvider(PaymentProviderException exception) {
        log.error("Payment provider error: {}", exception.getMessage(), exception.getCause());
        return build(HttpStatus.BAD_GATEWAY, exception.getMessage());
    }

    @ExceptionHandler(AiSearchUnavailableException.class)
    public ResponseEntity<ApiResponse<?>> handleAiSearchUnavailable(AiSearchUnavailableException exception) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrity(DataIntegrityViolationException exception) {
        log.warn("Data integrity violation: {}", exception.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleLockFailure(PessimisticLockingFailureException exception) {
        log.warn("Lock conflict: {}", exception.getMessage());
        return build(HttpStatus.CONFLICT, "The resource is busy, please retry");
    }

    /** @Valid failures on request bodies and query-parameter objects. */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(BindException exception) {
        List<String> subErrors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> error instanceof org.springframework.validation.FieldError fieldError
                        ? fieldError.getField() + ": " + fieldError.getDefaultMessage()
                        : error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", subErrors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodValidation(HandlerMethodValidationException exception) {
        List<String> subErrors = exception.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", subErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or malformed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + exception.getName() + "'");
    }

    @ExceptionHandler({AuthenticationException.class, JwtException.class})
    public ResponseEntity<ApiResponse<?>> handleAuthentication(RuntimeException exception) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    /** Ownership checks: the caller is logged in but this resource is not theirs. */
    @ExceptionHandler({AccessDeniedException.class, UnAuthorisedException.class})
    public ResponseEntity<ApiResponse<?>> handleForbidden(RuntimeException exception) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleUnexpected(Exception exception) {
        // Framework exceptions (unknown route, wrong method, missing parameter, ...) carry their own status.
        if (exception instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return build(status, errorResponse.getBody().getDetail());
        }
        log.error("Unhandled exception", exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong, please try again later");
    }

    private ResponseEntity<ApiResponse<?>> build(HttpStatus status, String message) {
        return build(status, message, null);
    }

    private ResponseEntity<ApiResponse<?>> build(HttpStatus status, String message, List<String> subErrors) {
        ApiError apiError = ApiError.builder()
                .status(status)
                .message(message)
                .subErrors(subErrors)
                .build();
        return new ResponseEntity<>(new ApiResponse<>(apiError), status);
    }
}
