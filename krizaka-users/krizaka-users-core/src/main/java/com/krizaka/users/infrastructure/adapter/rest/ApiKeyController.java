package com.krizaka.users.infrastructure.adapter.rest;

import com.krizaka.users.domain.model.GeneratedApiKey;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.ports.inbound.ApiKeyService;
import com.krizaka.users.infrastructure.adapter.rest.dto.ApiKeyResponse;
import com.krizaka.users.infrastructure.adapter.rest.dto.CreateApiKeyRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.GeneratedApiKeyResponse;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller managing a user's inbound API keys (Personal Access Tokens) that authenticate
 * their programmatic calls to the Sovkora API. Resource-oriented per ERR-128.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
public class ApiKeyController {

  private final ApiKeyService apiKeyService;

  public ApiKeyController(ApiKeyService apiKeyService) {
    this.apiKeyService = Objects.requireNonNull(apiKeyService, "ApiKeyService must not be null");
  }

  @GetMapping
  public ResponseEntity<List<ApiKeyResponse>> list(@AuthenticationPrincipal User user) {
    List<ApiKeyResponse> responses =
        apiKeyService.list(user.id().toString()).stream().map(ApiKeyResponse::from).toList();
    return ResponseEntity.ok(responses);
  }

  @PostMapping
  public ResponseEntity<GeneratedApiKeyResponse> create(
      @RequestBody CreateApiKeyRequest request, @AuthenticationPrincipal User user) {
    GeneratedApiKey generated =
        apiKeyService.create(user.id().toString(), request.name(), request.scopes());
    return ResponseEntity.status(HttpStatus.CREATED).body(GeneratedApiKeyResponse.from(generated));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> revoke(@PathVariable String id, @AuthenticationPrincipal User user) {
    boolean revoked = apiKeyService.revoke(user.id().toString(), id);
    return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
