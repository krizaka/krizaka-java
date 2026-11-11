package com.krizaka.users.domain.model;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Predefined user personas for testing and fallback scenarios.
 *
 * <p>Extracted to reduce code duplication in tests per AGENTS.md guidelines.
 */
public final class Persona {

  private static final String ROLE_USER = "ROLE_USER";

  private Persona() {
    // Utility class — no instantiation
  }

  /** Predefined standard free user persona. */
  public static User freeUser() {
    return freeUser(Map.of());
  }

  /** Predefined standard free user persona with custom preferences. */
  public static User freeUser(Map<String, Object> preferences) {
    return new User(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
        "test-user",
        "test-user@sovkora.com",
        true,
        Set.of(ROLE_USER),
        preferences,
        List.of(),
        "free");
  }

  /** Predefined premium user persona. */
  public static User premiumUser() {
    return new User(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
        "test-user",
        "test-user@sovkora.com",
        true,
        Set.of(ROLE_USER),
        Map.of(),
        List.of(),
        "premium");
  }

  /** Predefined admin user persona. */
  public static User adminUser() {
    return new User(
        UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
        "admin",
        "admin@sovkora.com",
        true,
        Set.of("ROLE_ADMIN", ROLE_USER),
        Map.of(),
        List.of(),
        "unlimited");
  }
}
