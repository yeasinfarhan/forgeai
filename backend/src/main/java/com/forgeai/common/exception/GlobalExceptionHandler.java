package com.forgeai.common.exception;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.forgeai.ai.AiException;
import com.forgeai.auth.EmailAlreadyRegisteredException;
import com.forgeai.auth.InvalidCredentialsException;
import com.forgeai.intake.IntakeStateException;

/**
 * Translates exceptions into RFC 9457 problem details so every API error has the same shape.
 * Spring MVC's own exceptions (malformed JSON, unsupported method, unknown route, ...) are
 * handled by {@link ResponseEntityExceptionHandler}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed.");
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email already registered");
        return problem;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail handleConflict(ConflictException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    /** Two requests changed the same row; the second one lost. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "This item was changed in another tab or window. Reload to see the latest version.");
        problem.setTitle("Conflict");
        return problem;
    }

    /** A database constraint rejected the change, typically two requests racing for the same unique value. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The change conflicts with existing data. Reload and try again.");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(IntakeStateException.class)
    ProblemDetail handleIntakeState(IntakeStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Intake conflict");
        return problem;
    }

    /** AI failures: technical detail goes to the log, a safe explanation goes to the client. */
    @ExceptionHandler(AiException.class)
    ProblemDetail handleAi(AiException ex) {
        log.warn("AI call failed ({}): {}", ex.failure(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.failure().httpStatus(), ex.failure().userMessage());
        problem.setTitle("AI service error");
        problem.setProperty("reason", ex.failure().name());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }

    record FieldViolation(String field, String message) {
    }
}
