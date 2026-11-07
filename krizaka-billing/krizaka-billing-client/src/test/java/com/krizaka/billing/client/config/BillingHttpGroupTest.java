package com.krizaka.billing.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.billing.client.StubBillingServer;
import com.krizaka.billing.client.StubBillingServer.Reply;
import com.krizaka.billing.domain.port.PackPricingClient;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Where the billing HTTP service group takes its base URL and timeouts: the Spring Boot group
 * properties ({@code spring.http.serviceclient.billing.*}) when set, else the {@code
 * krizaka.billing.*} keys that existed before the clients were declared.
 */
class BillingHttpGroupTest {

  private static final String PRICES =
      "[{\"packKey\":\"a\",\"priceCents\":100,\"includedCredits\":5,\"active\":true}]";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BillingClientAutoConfiguration.class))
          .withPropertyValues(
              "krizaka.billing.enabled=true",
              "krizaka.billing.service-secret=sovkora-test-secret-at-least-32-characters!");

  private StubBillingServer viaGroupProperty;
  private StubBillingServer viaKrizakaKey;

  @BeforeEach
  void startStubs() {
    viaGroupProperty = new StubBillingServer();
    viaKrizakaKey = new StubBillingServer();
    viaGroupProperty.replyWith(Reply.json(200, PRICES));
    viaKrizakaKey.replyWith(Reply.json(200, PRICES));
  }

  @AfterEach
  void stopStubs() {
    viaGroupProperty.close();
    viaKrizakaKey.close();
  }

  @Test
  @DisplayName("krizaka.billing.base-url keeps working as it did before the clients were declared")
  void theKrizakaKeyIsTheDefault() {
    runner
        .withPropertyValues("krizaka.billing.base-url=" + viaKrizakaKey.baseUrl())
        .run(
            context -> {
              context.getBean(PackPricingClient.class).prices(Set.of("a"));

              assertThat(viaKrizakaKey.requests()).hasSize(1);
              assertThat(viaGroupProperty.requests()).isEmpty();
            });
  }

  @Test
  @DisplayName("the Boot group property wins over krizaka.billing.base-url")
  void theGroupPropertyWins() {
    runner
        .withPropertyValues(
            "krizaka.billing.base-url=" + viaKrizakaKey.baseUrl(),
            "spring.http.serviceclient.billing.base-url=" + viaGroupProperty.baseUrl())
        .run(
            context -> {
              context.getBean(PackPricingClient.class).prices(Set.of("a"));

              assertThat(viaGroupProperty.requests()).hasSize(1);
              assertThat(viaKrizakaKey.requests()).isEmpty();
            });
  }

  @Test
  @DisplayName("krizaka.billing.read-timeout bounds a slow billing service")
  void theKrizakaReadTimeoutApplies() {
    viaKrizakaKey.delayAnswersBy(Duration.ofSeconds(2));
    runner
        .withPropertyValues(
            "krizaka.billing.base-url=" + viaKrizakaKey.baseUrl(),
            "krizaka.billing.read-timeout=200ms")
        .run(
            context -> {
              long start = System.nanoTime();

              assertThat(context.getBean(PackPricingClient.class).prices(Set.of("a"))).isEmpty();

              assertThat(Duration.ofNanos(System.nanoTime() - start))
                  .isLessThan(Duration.ofSeconds(1));
            });
  }

  @Test
  @DisplayName("the Boot group read-timeout wins over krizaka.billing.read-timeout")
  void theGroupReadTimeoutWins() {
    viaKrizakaKey.delayAnswersBy(Duration.ofSeconds(2));
    runner
        .withPropertyValues(
            "krizaka.billing.base-url=" + viaKrizakaKey.baseUrl(),
            "krizaka.billing.read-timeout=10s",
            "spring.http.serviceclient.billing.read-timeout=200ms")
        .run(
            context -> {
              long start = System.nanoTime();

              assertThat(context.getBean(PackPricingClient.class).prices(Set.of("a"))).isEmpty();

              assertThat(Duration.ofNanos(System.nanoTime() - start))
                  .isLessThan(Duration.ofSeconds(1));
            });
  }
}
