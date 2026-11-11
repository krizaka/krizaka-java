package com.krizaka.users.domain.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable domain representation of a Sovkora user, carrying identity, security authorities, and
 * multi-modal AI execution preferences.
 *
 * @param id The universally unique identifier for this user. Generated automatically if not
 *     provided at construction time.
 * @param username The human-readable login name of the user.
 * @param email The verified email address of the user.
 * @param enabled Whether the user account is enabled/active.
 * @param authorities The set of role name strings assigned to this user. Defaults to an empty set
 *     if {@code null} is supplied.
 * @param preferences A map of user-specific AI execution overrides. Defaults to an empty map if
 *     {@code null} is supplied.
 * @param activeInterceptions A list of active user interception types. Defaults to an empty list if
 *     {@code null} is supplied.
 * @param rateLimitTier The rate limiting tier assigned to this user.
 */
public record User(
    UUID id,
    String username,
    String email,
    boolean enabled,
    Set<String> authorities,
    Map<String, Object> preferences,
    List<String> activeInterceptions,
    String rateLimitTier) {

  /** Compact canonical constructor that applies defensive defaults and enforces invariants. */
  public User {
    Objects.requireNonNull(id, "User ID cannot be null");
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username cannot be empty");
    }
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email cannot be empty");
    }
    authorities = authorities == null ? Set.of() : Set.copyOf(authorities);

    Map<String, Object> tempPrefs =
        preferences == null ? new HashMap<>() : new HashMap<>(preferences);
    tempPrefs.compute("language", (k, v) -> v == null || String.valueOf(v).isBlank() ? "en" : v);
    preferences = Map.copyOf(tempPrefs);

    activeInterceptions =
        activeInterceptions == null ? List.of() : List.copyOf(activeInterceptions);
  }

  /**
   * Overloaded constructor for backwards compatibility without a rate limiting tier.
   *
   * @param id The universally unique identifier for this user.
   * @param username The human-readable login name of the user.
   * @param email The verified email address of the user.
   * @param enabled Whether the user account is enabled/active.
   * @param authorities The set of role name strings assigned to this user.
   * @param preferences A map of user-specific AI execution overrides.
   * @param activeInterceptions A list of active user interception types.
   */
  public User(
      UUID id,
      String username,
      String email,
      boolean enabled,
      Set<String> authorities,
      Map<String, Object> preferences,
      List<String> activeInterceptions) {
    this(
        id == null ? UUID.randomUUID() : id,
        username,
        email,
        enabled,
        authorities,
        preferences,
        activeInterceptions,
        null);
  }

  /**
   * Overloaded constructor for backwards compatibility without active interceptions or rate limit
   * tier.
   *
   * @param id The universally unique identifier for this user.
   * @param username The display name.
   * @param email The email address.
   * @param enabled Whether the user account is enabled.
   * @param authorities The set of security authorities/roles.
   * @param preferences A map of user preferences.
   */
  public User(
      UUID id,
      String username,
      String email,
      boolean enabled,
      Set<String> authorities,
      Map<String, Object> preferences) {
    this(
        id == null ? UUID.randomUUID() : id,
        username,
        email,
        enabled,
        authorities,
        preferences,
        List.of(),
        null);
  }
}
