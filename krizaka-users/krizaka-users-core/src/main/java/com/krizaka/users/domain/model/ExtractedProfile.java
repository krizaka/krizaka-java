package com.krizaka.users.domain.model;

import java.util.Objects;

/**
 * Immutable domain representation of a verified external OAuth2 identity profile.
 *
 * <p>Captures the minimal structural output produced by any {@code OAuth2ProviderVerifier} after
 * successfully validating an incoming identity token. This record is provider-agnostic and acts as
 * the canonical bridge between external identity systems and Sovkora's internal user reconciliation
 * pipeline.
 *
 * @param email The verified email address extracted from the provider token.
 * @param providerId The unique user identifier assigned by the external provider (e.g., Google sub,
 *     GitHub user ID).
 * @param name The display name or full name of the user from the provider profile.
 * @param avatarUrl The URL pointing to the user's avatar image from the provider profile. May be
 *     {@code null} if the provider does not supply one.
 */
public record ExtractedProfile(String email, String providerId, String name, String avatarUrl) {

  /** Compact canonical constructor enforcing non-null invariants on mandatory fields. */
  public ExtractedProfile {
    Objects.requireNonNull(email, "Extracted profile email cannot be null");
    Objects.requireNonNull(providerId, "Extracted profile providerId cannot be null");
    if (name == null || name.isBlank()) {
      name = email;
    }
  }
}
