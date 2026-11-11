package com.krizaka.security.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * A refused PEM keeps its cause for the logs, and neither the message nor the cause echoes the
 * text.
 */
class PemKeysTest {

  private static final String NOT_A_KEY =
      "-----BEGIN PUBLIC KEY-----\nc2VjcmV0LXRleHQ=\n-----END PUBLIC KEY-----";

  @Test
  void publicKeyRefusalKeepsItsCauseWithoutTheText() {
    assertThatThrownBy(() -> PemKeys.publicKey(NOT_A_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("c2VjcmV0")
        .hasCauseInstanceOf(java.security.GeneralSecurityException.class)
        .satisfies(
            e -> assertThat(String.valueOf(e.getCause().getMessage())).doesNotContain("c2VjcmV0"));
  }

  @Test
  void privateKeyRefusalKeepsItsCauseWithoutTheText() {
    assertThatThrownBy(() -> PemKeys.privateKey(NOT_A_KEY.replace("PUBLIC", "PRIVATE")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("c2VjcmV0")
        .hasCauseInstanceOf(java.security.GeneralSecurityException.class)
        .satisfies(
            e -> assertThat(String.valueOf(e.getCause().getMessage())).doesNotContain("c2VjcmV0"));
  }
}
