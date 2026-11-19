package com.krizaka.users.service.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.users.infrastructure.config.JwtConfig;
import com.krizaka.users.infrastructure.config.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The service must not start with a session secret an attacker can know (krizaka-restricted#351):
 * the context fails when it is missing, empty, short, a retired public default, or the local-only
 * value outside the {@code local} profile; the message names the variable, never the value.
 */
class SessionSecretStartupTest {

  private static final String RETIRED_DEFAULT = "sovkora-local-dev-identity-jwt-secret-256bit!";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

  private static String rootMessage(Throwable failure) {
    Throwable cause = failure;
    while (cause.getCause() != null) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  @Test
  @DisplayName("No secret, an empty one or a short one: the context does not start")
  void missingEmptyOrShortSecretFailsStartup() {
    for (String property :
        new String[] {
          "krizaka.users.jwt.issuer=iss",
          "krizaka.users.jwt.secret=",
          "krizaka.users.jwt.secret=too-short"
        }) {
      runner
          .withPropertyValues(property)
          .run(
              context -> {
                assertThat(context).hasFailed();
                String message = rootMessage(context.getStartupFailure());
                assertThat(message).contains("IDENTITY_JWT_SECRET").doesNotContain("too-short");
              });
    }
  }

  @Test
  @DisplayName("A known public default fails the start without printing it")
  void knownDefaultFailsStartup() {
    runner
        .withPropertyValues("krizaka.users.jwt.secret=" + RETIRED_DEFAULT)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootMessage(context.getStartupFailure()))
                  .contains("IDENTITY_JWT_SECRET")
                  .doesNotContain(RETIRED_DEFAULT);
            });
  }

  @Test
  @DisplayName("The local-only secret starts under the local profile only")
  void localOnlySecretStartsOnlyUnderLocal() {
    String local = "krizaka.users.jwt.secret=" + JwtProperties.LOCAL_ONLY_SECRET;
    runner
        .withPropertyValues(local)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(rootMessage(context.getStartupFailure()))
                  .contains("IDENTITY_JWT_SECRET")
                  .doesNotContain(JwtProperties.LOCAL_ONLY_SECRET);
            });
    runner
        .withPropertyValues(local, "spring.profiles.active=local")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
