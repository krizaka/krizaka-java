package com.krizaka.users.infrastructure.adapter.rest;

import com.krizaka.users.domain.exception.CredentialGrantRefusedException;
import com.krizaka.users.domain.model.CredentialGrant;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.CredentialGrantService;
import com.krizaka.users.infrastructure.adapter.rest.dto.CredentialGrantRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.CredentialGrantResponse;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Grants on the user's own authority (GHSA-3pf7-rgf2-4x3m): the service that owns a run forwards
 * the user's session here and receives a single-use grant for that run and provider. Only the
 * authenticated user's own key can be granted; another user's answers 404. Consent is a live
 * session: a request authenticated by an {@code oz_} API key is refused (403).
 */
@RestController
@RequestMapping("/api/v1/credentials")
public class CredentialGrantController {

  /** The prefix of a Sovkora API key ({@code ApiKeyServiceImpl.TOKEN_PREFIX}). */
  private static final String API_KEY_PREFIX = "oz_";

  private final CredentialGrantService grants;

  public CredentialGrantController(CredentialGrantService grants) {
    this.grants = Objects.requireNonNull(grants, "CredentialGrantService must not be null");
  }

  /** 201 with the grant, 404 when the user has no key for the provider. */
  @PostMapping("/{providerName}/grants")
  public ResponseEntity<CredentialGrantResponse> issue(
      @PathVariable String providerName,
      @RequestBody CredentialGrantRequest request,
      Authentication authentication) {
    if (authentication.getCredentials() instanceof String token
        && token.startsWith(API_KEY_PREFIX)) {
      throw new CredentialGrantRefusedException();
    }
    User user = (User) authentication.getPrincipal();
    CredentialGrant issued = grants.issue(user.id().toString(), providerName, request.runId());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new CredentialGrantResponse(issued.grant(), issued.expiresAt()));
  }
}
