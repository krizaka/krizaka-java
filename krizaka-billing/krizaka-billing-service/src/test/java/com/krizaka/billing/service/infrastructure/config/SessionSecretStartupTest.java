package com.krizaka.billing.service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.billing.service.infrastructure.config.SecurityConfig.SessionSecretGuard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Billing must not start with a session secret anyone can know (krizaka-restricted#351). */
class SessionSecretStartupTest {

  private static final String RETIRED = "sovkora-local-dev-identity-jwt-secret-256bit!";
  private static final String KEY = "krizaka.security.jwt.secret=";
  private static final String LOCAL = "spring.profiles.active=local";

  /** The start failure's innermost message, or null when the context started. */
  private static String startFailure(String... properties) {
    String[] message = new String[1];
    new ApplicationContextRunner()
        .withUserConfiguration(SessionSecretGuard.class)
        .withPropertyValues(properties)
        .run(
            context -> {
              Throwable cause = context.getStartupFailure();
              while (cause != null && cause.getCause() != null) {
                cause = cause.getCause();
              }
              message[0] = cause == null ? null : cause.getMessage();
            });
    return message[0];
  }

  @Test
  @DisplayName("A missing, empty or short secret stops the start, naming the variable only")
  void missingEmptyOrShort() {
    assertThat(startFailure("unrelated=1")).contains("IDENTITY_JWT_SECRET");
    assertThat(startFailure(KEY)).contains("IDENTITY_JWT_SECRET");
    assertThat(startFailure(KEY + "too-short"))
        .contains("IDENTITY_JWT_SECRET")
        .doesNotContain("too-short");
  }

  @Test
  @DisplayName("The retired public default is refused everywhere and never printed")
  void retiredDefault() {
    assertThat(startFailure(KEY + RETIRED, LOCAL))
        .contains("IDENTITY_JWT_SECRET")
        .doesNotContain(RETIRED);
  }

  @Test
  @DisplayName("The local-only secret is accepted under the local profile only")
  void localOnly() {
    String value = SessionSecretGuard.LOCAL_ONLY_SECRET;

    assertThat(startFailure(KEY + value)).contains("IDENTITY_JWT_SECRET").doesNotContain(value);
    assertThat(startFailure(KEY + value, LOCAL)).isNull();
  }

  @Test
  @DisplayName("A real secret starts")
  void realSecret() {
    assertThat(startFailure(KEY + "a-real-deployment-secret-0123456789-abcdef")).isNull();
  }
}
