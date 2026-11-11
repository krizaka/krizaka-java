package com.krizaka.users.infrastructure.config;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

/**
 * Configuration properties for passive generic identity workflows in Sovkora. Properties backing
 * the {@code krizaka.users} configuration namespace. They represent the technical and physical
 * constraints of the Identity hexagon, independent of core orchestrator logic.
 */
@ConfigurationProperties(prefix = "krizaka.users")
public record IdentityInfrastructureProperties(
    EmailVerification emailVerification,
    Interceptions interceptions,
    Preferences preferences,
    Registration registration) {

  /** Compact constructor providing null-safe defaults for optional sub-records. */
  public IdentityInfrastructureProperties {
    if (emailVerification == null) {
      emailVerification = new EmailVerification(false);
    }
    if (interceptions == null) {
      interceptions = new Interceptions(false, Map.of());
    }
    if (preferences == null) {
      preferences = new Preferences(List.of());
    }
    if (registration == null) {
      registration = new Registration(true);
    }
  }

  /**
   * Whether new accounts may be created.
   *
   * @param enabled {@code false} closes every way an account is created — the register endpoint and
   *     the first sign-in of an unknown federated (Google/GitHub) identity — while existing
   *     accounts keep signing in. Open when absent, so a host that never sets it keeps registering.
   */
  public record Registration(boolean enabled) {}

  /**
   * Settings for the email verification subsystem.
   *
   * @param enabled Whether email verification is enabled. If true, newly registered users are
   *     created disabled and must be activated via verification token.
   */
  public record EmailVerification(boolean enabled) {}

  /**
   * Settings for the user interception engine.
   *
   * @param enabled Whether user interceptions are enabled.
   * @param schemas Mapping of schema ID to its Resource context.
   */
  public record Interceptions(boolean enabled, Map<String, Resource> schemas) {

    /** Compact constructor ensuring schemas is never null. */
    public Interceptions {
      if (schemas == null) {
        schemas = Map.of();
      }
    }
  }

  /**
   * Rules on the preferences users write about themselves.
   *
   * @param reservedPrefixes key prefixes the hosting application reads as its own configuration — a
   *     user may not store a preference under them (e.g. {@code app.}). {@code preference.} is
   *     always reserved.
   */
  public record Preferences(List<String> reservedPrefixes) {

    /** Compact constructor ensuring the list is never null and holds no blank prefix. */
    public Preferences {
      reservedPrefixes =
          reservedPrefixes == null
              ? List.of()
              : reservedPrefixes.stream().filter(p -> p != null && !p.isBlank()).toList();
    }
  }
}
