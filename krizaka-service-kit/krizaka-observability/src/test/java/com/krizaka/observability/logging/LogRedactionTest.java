package com.krizaka.observability.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.observability.logging.LogRedaction.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Each rule of the one rule set, on its own and together. */
class LogRedactionTest {

  /** A JWT-shaped string, built from its three segments so no scanner mistakes it for a key. */
  private static final String JWT =
      String.join(".", "eyJhbGciOiJub25lIn0", "eyJzdWIiOiJ0ZXN0In0", "c2lnbmF0dXJl");

  @Test
  void theRuleSetIsOneImmutableListInATestedOrder() {
    assertThat(LogRedaction.RULES)
        .extracting(Rule::name)
        .containsExactly("authorization", "bearer", "jwt", "secret", "email");
    assertThatThrownBy(() -> LogRedaction.RULES.add(LogRedaction.RULES.getFirst()))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void anEmailAddressIsRedacted() {
    assertThat(LogRedaction.redact("invoice sent to jane.doe+billing@mail.example.co.uk today"))
        .isEqualTo("invoice sent to [REDACTED] today");
  }

  @Test
  void aBearerTokenIsRedacted() {
    assertThat(LogRedaction.redact("calling with bearer s3cr3t-T0ken_value.x/y+z= done"))
        .isEqualTo("calling with Bearer [REDACTED] done");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Authorization: Basic dXNlcjpwYXNz",
        "authorization=Bearer abc.def",
        "\"Authorization\":\"Token abc\"",
        "Proxy-Authorization: Basic dXNlcjpwYXNz"
      })
  void anAuthorizationValueIsRedactedWhateverItsScheme(String header) {
    String redacted = LogRedaction.redact("headers " + header + " end");

    assertThat(redacted)
        .contains("[REDACTED]")
        .doesNotContain("dXNlcjpwYXNz")
        .doesNotContain("abc")
        .endsWith(" end");
  }

  @Test
  void aJwtShapedStringIsRedactedWithoutAKeyOrAScheme() {
    assertThat(LogRedaction.redact("session " + JWT + " expired"))
        .isEqualTo("session [REDACTED] expired");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "token=abc123",
        "access_token=abc123",
        "client-secret: abc123",
        "password=abc123",
        "dbPassword = abc123",
        "api_key=abc123",
        "x-api-key: abc123",
        "apiKey=abc123",
        "\"refreshToken\": \"abc123\""
      })
  void theValueOfASecretNamedKeyIsRedacted(String assignment) {
    String redacted = LogRedaction.redact("config " + assignment + ", next");

    assertThat(redacted).doesNotContain("abc123").contains("[REDACTED]").endsWith(", next");
  }

  @Test
  void aSecretInAQueryStringStopsAtTheNextParameter() {
    assertThat(LogRedaction.redact("GET /cb?access_token=abc123&state=ok"))
        .isEqualTo("GET /cb?access_token=[REDACTED]&state=ok");
  }

  @Test
  void anOrdinaryLineIsLeftAlone() {
    String line = "order 42 shipped to warehouse 7 by TokenService (password reset flow)";

    assertThat(LogRedaction.redact(line)).isEqualTo(line);
  }

  @Test
  void aFieldNamedLikeASecretLosesItsWholeValue() {
    assertThat(LogRedaction.redact("apiToken", "plain-value")).isEqualTo("[REDACTED]");
    assertThat(LogRedaction.redact("db.password", "hunter2")).isEqualTo("[REDACTED]");
    assertThat(LogRedaction.redact("Authorization", "Basic x")).isEqualTo("[REDACTED]");
    assertThat(LogRedaction.redact("user", "jane@example.com")).isEqualTo("[REDACTED]");
    assertThat(LogRedaction.redact("orderId", "42")).isEqualTo("42");
  }

  @Test
  void aLineWithAnEmailAndABearerTokenKeepsNeither() {
    String redacted =
        LogRedaction.redact(
            "user jane.doe@example.com called /me with Authorization: Bearer abc.def-ghi");

    assertThat(redacted)
        .doesNotContain("jane.doe@example.com")
        .doesNotContain("abc.def-ghi")
        .isEqualTo("user [REDACTED] called /me with Authorization: [REDACTED]");
  }

  @Test
  void theSelfTestPassesOnTheShippedRules() {
    assertThat(LogRedaction.selfTest()).isTrue();
  }

  @Test
  void aLongInputDoesNotBacktrackForever() {
    String hostile = "a".repeat(50_000) + "@" + "b".repeat(50_000);

    long start = System.nanoTime();
    LogRedaction.redact(hostile);

    assertThat(System.nanoTime() - start).isLessThan(2_000_000_000L);
  }
}
