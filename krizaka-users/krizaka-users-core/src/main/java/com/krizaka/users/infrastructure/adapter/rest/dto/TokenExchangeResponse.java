package com.krizaka.users.infrastructure.adapter.rest.dto;

import java.util.Objects;

/**
 * Internal response carrying the session JWT minted for a valid API key.
 *
 * @param token the compact session JWT
 * @param scopes what the key may do (e.g. {@code ai:embed}); empty = the owner's full rights. The
 *     edge enforces it: the JWT itself is the owner's.
 * @param tierKey the rate-limit tier of the key's owner (their own, else the default one); {@code
 *     null} when no tier is configured
 * @param requestsPerMinute that tier's requests per minute, a row of {@code rate_limits} the
 *     operator edits; {@code null} when no tier is configured — the edge then keeps its own default
 * @param dailyRequests that tier's daily request quota; {@code null} when the row sets none — the
 *     edge then keeps its own default. An older edge that does not know the field ignores it.
 */
public record TokenExchangeResponse(
    String token,
    java.util.List<String> scopes,
    String tierKey,
    Integer requestsPerMinute,
    Integer dailyRequests) {

  public TokenExchangeResponse {
    Objects.requireNonNull(token, "token is required");
    scopes = scopes == null ? java.util.List.of() : java.util.List.copyOf(scopes);
  }

  /** An exchange with a tier and no daily quota. */
  public TokenExchangeResponse(
      String token, java.util.List<String> scopes, String tierKey, Integer requestsPerMinute) {
    this(token, scopes, tierKey, requestsPerMinute, null);
  }

  /** An exchange with scopes and no tier. */
  public TokenExchangeResponse(String token, java.util.List<String> scopes) {
    this(token, scopes, null, null, null);
  }

  /** An unscoped exchange. */
  public TokenExchangeResponse(String token) {
    this(token, java.util.List.of());
  }
}
