package com.krizaka.users.infrastructure.adapter.rest;

import com.krizaka.users.domain.model.RateLimitInfo;
import com.krizaka.users.domain.model.User;
import com.krizaka.users.domain.model.UserProfile;
import com.krizaka.users.domain.ports.inbound.ApiKeyService;
import com.krizaka.users.domain.ports.inbound.IdentityService;
import com.krizaka.users.domain.ports.inbound.RateLimitProvider;
import com.krizaka.users.domain.ports.inbound.TokenService;
import com.krizaka.users.domain.ports.inbound.UserProfileProvider;
import com.krizaka.users.infrastructure.adapter.rest.dto.InternalUserResponse;
import com.krizaka.users.infrastructure.adapter.rest.dto.TokenExchangeRequest;
import com.krizaka.users.infrastructure.adapter.rest.dto.TokenExchangeResponse;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal service-to-service API of the identity context (Phase 2 cutover): downstream hosts
 * hydrate principals, profiles and tiers over HTTP once the identity library leaves their process;
 * the edge exchanges {@code oz_} API keys for session JWTs here. A BYOK provider key is released
 * only by {@link InternalCredentialController}, against a run-bound grant.
 *
 * <p>{@code /internal/**} is not reachable through the edge route table (only {@code /api/**} flows
 * there) — LAN-local in this phase; per-service credentials arrive with the off-laptop hardening
 * pass.
 */
@RestController
@RequestMapping("/internal/v1")
class InternalUserController {

  private final IdentityService identityService;
  private final UserProfileProvider userProfileProvider;
  private final ApiKeyService apiKeyService;
  private final TokenService tokenService;
  private final RateLimitProvider rateLimitProvider;

  InternalUserController(
      IdentityService identityService,
      UserProfileProvider userProfileProvider,
      ApiKeyService apiKeyService,
      TokenService tokenService,
      RateLimitProvider rateLimitProvider) {
    this.identityService = Objects.requireNonNull(identityService, "IdentityService required");
    this.userProfileProvider =
        Objects.requireNonNull(userProfileProvider, "UserProfileProvider required");
    this.apiKeyService = Objects.requireNonNull(apiKeyService, "ApiKeyService required");
    this.tokenService = Objects.requireNonNull(tokenService, "TokenService required");
    this.rateLimitProvider =
        Objects.requireNonNull(rateLimitProvider, "RateLimitProvider required");
  }

  /** Full user snapshot for downstream principal hydration (404 via UserNotFoundException). */
  @GetMapping("/users/{id}")
  public InternalUserResponse getUser(@PathVariable String id) {
    return InternalUserResponse.from(identityService.getUser(id));
  }

  /** The user's profile for pipeline context assembly. */
  @GetMapping("/users/{id}/profile")
  public ResponseEntity<UserProfile> getProfile(@PathVariable String id) {
    UserProfile profile = userProfileProvider.getProfile(id);
    return profile != null ? ResponseEntity.ok(profile) : ResponseEntity.notFound().build();
  }

  /** Exchanges a valid {@code oz_} API key for a session JWT (performed by the edge). */
  @PostMapping("/tokens/exchange")
  public ResponseEntity<TokenExchangeResponse> exchange(@RequestBody TokenExchangeRequest request) {
    return apiKeyService
        .authenticateKey(request.apiKey())
        .filter(key -> key.user().enabled())
        .map(
            key -> {
              Optional<RateLimitInfo> tier = tierOf(key.user());
              return ResponseEntity.ok(
                  new TokenExchangeResponse(
                      tokenService.issue(key.user()),
                      key.scopes().stream().sorted().toList(),
                      tier.map(RateLimitInfo::tierKey).orElse(null),
                      tier.map(RateLimitInfo::requestsPerMinute).orElse(null),
                      tier.map(RateLimitInfo::dailyRequests).orElse(null)));
            })
        .orElseGet(() -> ResponseEntity.status(401).build());
  }

  /** The user's own tier, else the default one: the limit the edge holds this caller to. */
  private Optional<RateLimitInfo> tierOf(User user) {
    return Optional.ofNullable(user.rateLimitTier())
        .flatMap(rateLimitProvider::getRateLimit)
        .or(() -> rateLimitProvider.getDefaultTierKey().flatMap(rateLimitProvider::getRateLimit));
  }

  /** The DB-flagged default rate-limit tier (anonymous callers). */
  @GetMapping("/tiers/default")
  public ResponseEntity<RateLimitInfo> getDefaultTier() {
    return rateLimitProvider
        .getDefaultTierKey()
        .flatMap(rateLimitProvider::getRateLimit)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /** Bucket parameters of a rate-limit tier. */
  @GetMapping("/tiers/{key}")
  public ResponseEntity<RateLimitInfo> getTier(@PathVariable String key) {
    return rateLimitProvider
        .getRateLimit(key)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
