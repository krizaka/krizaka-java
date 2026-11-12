package com.krizaka.users.infrastructure.adapter.rest;

import com.krizaka.users.domain.exception.AdminActionRefusedException;
import com.krizaka.users.domain.exception.ApiKeyLimitExceededException;
import com.krizaka.users.domain.exception.CredentialConflictException;
import com.krizaka.users.domain.exception.CredentialGrantRefusedException;
import com.krizaka.users.domain.exception.CredentialNotFoundException;
import com.krizaka.users.domain.exception.InvalidRequestException;
import com.krizaka.users.domain.exception.OrganizationAccessDeniedException;
import com.krizaka.users.domain.exception.OrganizationConflictException;
import com.krizaka.users.domain.exception.OrganizationNotFoundException;
import com.krizaka.users.domain.exception.RegistrationClosedException;
import com.krizaka.users.domain.exception.ServiceAccountProtectedException;
import com.krizaka.users.domain.exception.UserAlreadyExistsException;
import com.krizaka.users.domain.exception.UserNotFoundException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps identity domain exceptions to HTTP responses. Lives with the identity context so every host
 * (identity service, and the router during the dual-run window) renders identical errors — the
 * advice applies context-wide, including to non-identity controllers that raise {@link
 * InvalidRequestException}.
 */
@RestControllerAdvice
public class IdentityErrorResolver {

  private static final Logger logger = LoggerFactory.getLogger(IdentityErrorResolver.class);

  /** Maps a duplicate registration to 409 Conflict. */
  @ExceptionHandler(UserAlreadyExistsException.class)
  public ResponseEntity<Map<String, String>> handleUserAlreadyExists(
      UserAlreadyExistsException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex);
  }

  /** Maps a registration attempt while registration is closed to 403 Forbidden. */
  @ExceptionHandler(RegistrationClosedException.class)
  public ResponseEntity<Map<String, String>> handleRegistrationClosed(
      RegistrationClosedException ex) {
    return errorResponse(HttpStatus.FORBIDDEN, ex);
  }

  /** Maps an exhausted API key allowance to 409 Conflict. */
  @ExceptionHandler(ApiKeyLimitExceededException.class)
  public ResponseEntity<Map<String, String>> handleApiKeyLimitExceeded(
      ApiKeyLimitExceededException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex);
  }

  /**
   * Maps a protected service account (erase, last key) to 409 Problem Details with a stable code.
   */
  @ExceptionHandler(ServiceAccountProtectedException.class)
  public ProblemDetail handleServiceAccountProtected(ServiceAccountProtectedException ex) {
    logger.warn("Service account change refused ({})", ex.code());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problem.setTitle("Service account protected");
    problem.setProperty("code", ex.code());
    return problem;
  }

  /** Maps a refused administrative action to 409 Conflict. */
  @ExceptionHandler(AdminActionRefusedException.class)
  public ResponseEntity<Map<String, String>> handleAdminActionRefused(
      AdminActionRefusedException ex) {
    logger.warn("Administrative action refused ({}): {}", ex.code(), ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", ex.getMessage(), "code", ex.code()));
  }

  @ExceptionHandler(InvalidRequestException.class)
  public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException ex) {
    return errorResponse(HttpStatus.BAD_REQUEST, ex);
  }

  /** Maps an unknown user to 404 Not Found (internal user-directory lookups). */
  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex);
  }

  /** Maps an organization that does not exist, or is not visible to the caller, to 404. */
  @ExceptionHandler(OrganizationNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleOrganizationNotFound(
      OrganizationNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex);
  }

  /** Maps an action the caller's organization role does not allow to 403. */
  @ExceptionHandler(OrganizationAccessDeniedException.class)
  public ResponseEntity<Map<String, String>> handleOrganizationAccessDenied(
      OrganizationAccessDeniedException ex) {
    return errorResponse(HttpStatus.FORBIDDEN, ex);
  }

  /** Maps a taken slug or a missing last owner to 409. */
  @ExceptionHandler(OrganizationConflictException.class)
  public ResponseEntity<Map<String, String>> handleOrganizationConflict(
      OrganizationConflictException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex);
  }

  /** Maps a missing provider key (or another user's: its existence is not revealed) to 404. */
  @ExceptionHandler(CredentialNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleCredentialNotFound(
      CredentialNotFoundException ex) {
    return errorResponse(HttpStatus.NOT_FOUND, ex);
  }

  /** Maps a second key for the same provider to 409: rotate it instead. */
  @ExceptionHandler(CredentialConflictException.class)
  public ResponseEntity<Map<String, String>> handleCredentialConflict(
      CredentialConflictException ex) {
    return errorResponse(HttpStatus.CONFLICT, ex);
  }

  /**
   * Maps a refused credential grant to 403 Problem Details; which check failed is not said, and the
   * grant is never logged (GHSA-3pf7-rgf2-4x3m).
   */
  @ExceptionHandler(CredentialGrantRefusedException.class)
  public ProblemDetail handleCredentialGrantRefused(CredentialGrantRefusedException ex) {
    logger.warn("Credential grant refused");
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    problem.setTitle("Credential grant refused");
    return problem;
  }

  private ResponseEntity<Map<String, String>> errorResponse(
      HttpStatus status, RuntimeException ex) {
    logger.warn("Identity request rejected ({}): {}", status.value(), ex.getMessage());
    return ResponseEntity.status(status).body(Map.of("error", ex.getMessage()));
  }
}
