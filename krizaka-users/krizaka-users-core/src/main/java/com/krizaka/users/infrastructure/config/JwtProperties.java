package com.krizaka.users.infrastructure.config;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of the identity session JWTs ({@code krizaka.users.jwt}).
 *
 * <p>With {@code signing} set (ADR-001), session tokens are ES256, signed by this issuer only; the
 * verifiers fetch the public keys from {@code /.well-known/jwks.json} and hold no signing material.
 * Without it the legacy HS256 shared secret signs them (deprecated). The {@code secret} still
 * verifies legacy HS256 tokens only until {@code hs256Cutoff}; {@code SERVICE} tokens are ES256,
 * per caller (slice 3).
 *
 * @param secret the HMAC-SHA256 secret (≥ 32 bytes)
 * @param issuer the {@code iss} claim — verifiers' {@code krizaka.security.jwt.issuer}
 * @param ttl session token lifetime ({@code exp = iat + ttl}) — bounds RBAC/tier staleness
 * @param signing the ES256 issuer key, or {@code null} for the legacy HS256 mode
 * @param hs256Cutoff the dated cutover: from this instant the HS256 shared secret verifies nothing
 *     (sessions nor {@code SERVICE} tokens); {@code null} keeps it until the secret is removed
 * @param callers the public key (SPKI PEM, P-256) of each calling service by the name it puts in
 *     {@code sub} — the ES256 {@code SERVICE} tokens are verified against it (ADR-001 slice 3)
 */
@ConfigurationProperties(prefix = "krizaka.users.jwt")
public record JwtProperties(
    String secret,
    @DefaultValue("krizaka-users-core") String issuer,
    @DefaultValue("PT30M") Duration ttl,
    Signing signing,
    Instant hs256Cutoff,
    Map<String, String> callers) {

  /**
   * The value of the `local` profile: public, so {@link #requireRealSecretUnlessLocal} refuses it
   * elsewhere.
   */
  public static final String LOCAL_ONLY_SECRET =
      "local-only-never-deploy-identity-jwt-secret-256bit";

  /** Values that were once committed as defaults: public, never acceptable. */
  private static final Set<String> RETIRED_PUBLIC_DEFAULTS =
      Set.of("sovkora-local-dev-identity-jwt-secret-256bit!");

  @ConstructorBinding
  public JwtProperties {
    secret = secret == null || secret.isBlank() ? null : secret;
    if (secret != null && secret.length() < 32) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt.secret (IDENTITY_JWT_SECRET) must be at least 32 characters"
              + " (256-bit HS256)");
    }
    if (secret != null && RETIRED_PUBLIC_DEFAULTS.contains(secret)) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt.secret (IDENTITY_JWT_SECRET) is a known public default: set a real one");
    }
    if (secret == null && (signing == null || !signing.configured())) {
      throw new IllegalArgumentException(
          "krizaka.users.jwt needs an ES256 signing key or, legacy, a secret:"
              + " set IDENTITY_JWT_SECRET (or AUTH_JWT_SIGNING_KEY)");
    }
    callers = callers == null ? Map.of() : Map.copyOf(callers);
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("krizaka.users.jwt.issuer cannot be blank");
    }
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("krizaka.users.jwt.ttl must be positive");
    }
  }

  /** Legacy HS256 mode: no issuer key. */
  public JwtProperties(String secret, String issuer, Duration ttl) {
    this(secret, issuer, ttl, null, null, null);
  }

  /** ES256 sessions, HS256 secret still accepted (no cutover date, no caller keys). */
  public JwtProperties(String secret, String issuer, Duration ttl, Signing signing) {
    this(secret, issuer, ttl, signing, null, null);
  }

  /**
   * Refuses, at startup, the public local-only secret outside the {@code local} profile.
   *
   * @param local whether the {@code local} profile is active
   * @throws IllegalStateException when the local-only secret is used elsewhere; the message names
   *     the variable, never the value
   */
  public void requireRealSecretUnlessLocal(boolean local) {
    if (!local && LOCAL_ONLY_SECRET.equals(secret)) {
      throw new IllegalStateException(
          "krizaka.users.jwt.secret (IDENTITY_JWT_SECRET) is the local-only value: set a real"
              + " secret outside the local profile");
    }
  }

  /**
   * Whether the HS256 shared secret still verifies at {@code now}.
   *
   * @param now the current instant
   * @return {@code false} without a secret, or from the cutover instant on
   */
  public boolean hs256Accepted(Instant now) {
    return secret != null && (hs256Cutoff == null || now.isBefore(hs256Cutoff));
  }

  /**
   * The issuer's ES256 key pair, as PEM, from the environment ({@code AUTH_JWT_SIGNING_KEY}…) —
   * never committed, never logged.
   *
   * @param privateKey PKCS#8 PEM of the P-256 private key
   * @param publicKey SPKI PEM of the matching public key
   * @param keyId the {@code kid} of the current key (new tokens carry it)
   * @param retired previous {@code kid} to public-key PEM: still published so tokens in flight
   *     verify until they expire
   */
  public record Signing(
      String privateKey, String publicKey, String keyId, Map<String, String> retired) {

    public Signing {
      retired = retired == null ? Map.of() : Map.copyOf(retired);
    }

    /**
     * Whether any issuer key setting is present. Empty environment defaults bind as blank values,
     * so a present-but-blank block is not a configuration; a partial one is, and fails at startup.
     *
     * @return {@code true} when at least one of private key, public key or key id is non-blank
     */
    public boolean configured() {
      return !isBlank(privateKey) || !isBlank(publicKey) || !isBlank(keyId);
    }

    private static boolean isBlank(String value) {
      return value == null || value.isBlank();
    }

    @Override
    public String toString() {
      return "Signing[keyId=" + keyId + ", retired=" + retired.keySet() + ", privateKey=***]";
    }
  }

  @Override
  public String toString() {
    return "JwtProperties[issuer="
        + issuer
        + ", ttl="
        + ttl
        + ", signing="
        + signing
        + ", hs256Cutoff="
        + hs256Cutoff
        + ", callers="
        + callers.keySet()
        + "]";
  }
}
