package com.krizaka.billing.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class BillingPropertiesTest {

  private static BillingProperties bind(Map<String, String> properties) {
    return new Binder(new MapConfigurationPropertySource(properties))
        .bindOrCreate("krizaka.billing", BillingProperties.class);
  }

  @Test
  void theSecretNeverLeavesThroughToString() {
    BillingProperties properties =
        bind(Map.of("krizaka.billing.service-secret", "a-secret-of-at-least-thirty-two-chars"));

    assertThat(properties.toString())
        .doesNotContain("a-secret-of-at-least-thirty-two-chars")
        .contains("serviceSecret=<redacted>");
  }

  @Test
  void defaults() {
    BillingProperties properties = bind(Map.of());

    assertThat(properties.enabled()).isFalse();
    assertThat(properties.baseUrl()).isEqualTo(BillingProperties.DEFAULT_BASE_URL);
    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofMillis(500));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void aBlankBaseUrlFallsBackToTheDefault() {
    assertThat(bind(Map.of("krizaka.billing.base-url", " ")).baseUrl())
        .isEqualTo(BillingProperties.DEFAULT_BASE_URL);
  }
}
