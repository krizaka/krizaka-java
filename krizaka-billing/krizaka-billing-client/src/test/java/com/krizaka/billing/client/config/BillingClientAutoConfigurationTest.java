package com.krizaka.billing.client.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import com.krizaka.billing.domain.port.EntitlementProvider;
import com.krizaka.billing.domain.port.PackPricingClient;
import com.krizaka.billing.domain.port.PackProvisioningClient;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;

/**
 * The seam's contract: exactly one bean of each billing port exists in every configuration, so a
 * consumer injects the ports unconditionally and never branches on whether billing is deployed.
 * What each adapter does on the wire is pinned by {@code BillingClientFailureContractTest}.
 */
class BillingClientAutoConfigurationTest {

  private static final String SECRET = "sovkora-test-secret-at-least-32-characters!";

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BillingClientAutoConfiguration.class));

  @Test
  @DisplayName("a fresh clone gets the no-op of every port — nothing needs billing deployed")
  void defaultsToNoOp() {
    runner.run(context -> assertThat(noOpNames(context)).hasSize(4));
  }

  @Test
  @DisplayName("explicitly disabled still yields the no-op, never a missing bean")
  void disabledYieldsNoOp() {
    runner
        .withPropertyValues("krizaka.billing.enabled=false")
        .run(context -> assertThat(noOpNames(context)).hasSize(4));
  }

  @Test
  @DisplayName("one switch wires the four HTTP adapters, and only one bean per port is present")
  void enabledYieldsTheHttpAdapters() {
    runner
        .withPropertyValues(
            "krizaka.billing.enabled=true", "krizaka.billing.service-secret=" + SECRET)
        .run(
            context -> {
              assertThat(noOpNames(context)).isEmpty();
              assertThat(context.getBeansOfType(CreditAuthorizationClient.class)).hasSize(1);
              assertThat(context.getBeansOfType(EntitlementProvider.class)).hasSize(1);
              assertThat(context.getBeansOfType(PackPricingClient.class)).hasSize(1);
              assertThat(context.getBeansOfType(PackProvisioningClient.class)).hasSize(1);
            });
  }

  @Test
  @DisplayName("the invalidation queue is named per host, so every cache gets its own copy")
  void invalidationQueueIsPerHost() {
    runner
        .withPropertyValues(
            "krizaka.billing.enabled=true",
            "spring.application.name=sovkora-conversation-service",
            "krizaka.billing.service-secret=" + SECRET)
        .run(
            context -> {
              Queue queue = context.getBean("entitlementInvalidationQueue", Queue.class);
              // Competing consumers on one shared queue would leave exactly one service's cache
              // correct and the rest stale — worse than no invalidation, because it is
              // intermittent.
              assertThat(queue.getName())
                  .isEqualTo("krizaka.billing.entitlement-cache.sovkora-conversation-service");
              assertThat(queue.isAutoDelete()).isTrue();
            });
  }

  @Test
  @DisplayName("with billing off there is no cache, so no listener is wired to invalidate one")
  void noListenerWithoutTheHttpProvider() {
    runner.run(
        context ->
            assertThat(context.containsBean("entitlementCacheInvalidationListener")).isFalse());
  }

  @Test
  @DisplayName("with billing on the listener that evicts the cache is wired")
  void listenerWiredWhenEnabled() {
    runner
        .withPropertyValues(
            "krizaka.billing.enabled=true", "krizaka.billing.service-secret=" + SECRET)
        .run(
            context ->
                assertThat(context.containsBean("entitlementCacheInvalidationListener")).isTrue());
  }

  /** The simple class names of the four ports' beans that are a no-op. */
  private static List<String> noOpNames(ApplicationContext context) {
    return Stream.of(
            context.getBean(CreditAuthorizationClient.class),
            context.getBean(EntitlementProvider.class),
            context.getBean(PackPricingClient.class),
            context.getBean(PackProvisioningClient.class))
        .map(bean -> bean.getClass().getSimpleName())
        .filter(name -> name.startsWith("NoOp"))
        .toList();
  }
}
