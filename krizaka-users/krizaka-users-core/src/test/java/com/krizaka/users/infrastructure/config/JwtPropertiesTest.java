package com.krizaka.users.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

  private static final String SECRET = "unit-test-identity-jwt-secret-256bit-key!";

  @Test
  @DisplayName("Valid properties are accepted")
  void validPropertiesAccepted() {
    var properties = new JwtProperties(SECRET, "krizaka-users", Duration.ofMinutes(30));

    assertThat(properties.issuer()).isEqualTo("krizaka-users");
  }

  @Test
  @DisplayName("A short secret is rejected (needs 256-bit HS256 material)")
  void shortSecretRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("too-short", "iss", Duration.ofMinutes(30)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(null, "iss", Duration.ofMinutes(30)));
  }

  @Test
  @DisplayName("Blank issuer and non-positive ttl are rejected")
  void blankIssuerAndBadTtlRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(SECRET, " ", Duration.ofMinutes(30)));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(SECRET, "iss", Duration.ZERO));
  }

  @Test
  @DisplayName("A known public default secret is refused, and the message never shows it")
  void publicDefaultRefused() {
    String known = "sovkora-local-dev-identity-jwt-secret-256bit!";

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties(known, "iss", Duration.ofMinutes(30)))
        .withMessageContaining("IDENTITY_JWT_SECRET")
        .withMessageNotContaining(known);
  }

  @Test
  @DisplayName("The local-only secret is refused outside the local profile")
  void localOnlySecretRefusedOutsideLocal() {
    var properties =
        new JwtProperties(JwtProperties.LOCAL_ONLY_SECRET, "iss", Duration.ofMinutes(30));

    assertThatIllegalStateException()
        .isThrownBy(() -> properties.requireRealSecretUnlessLocal(false))
        .withMessageContaining("IDENTITY_JWT_SECRET")
        .withMessageNotContaining(JwtProperties.LOCAL_ONLY_SECRET);
    properties.requireRealSecretUnlessLocal(true);
    new JwtProperties(SECRET, "iss", Duration.ofMinutes(30)).requireRealSecretUnlessLocal(false);
  }

  @Test
  @DisplayName(
      "A missing or empty secret fails the context start outside local, naming the variable")
  void missingSecretFailsStartNamingTheVariable() {
    for (String missing : new String[] {null, "", "  "}) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new JwtProperties(missing, "iss", Duration.ofMinutes(30)))
          .withMessageContaining("IDENTITY_JWT_SECRET");
    }
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new JwtProperties("too-short", "iss", Duration.ofMinutes(30)))
        .withMessageContaining("IDENTITY_JWT_SECRET")
        .withMessageNotContaining("too-short");
  }
}
