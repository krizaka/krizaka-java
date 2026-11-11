package com.krizaka.billing.client.pricing;

import com.krizaka.billing.domain.port.PackPricingClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires {@link PackPricingClient}: the HTTP adapter when {@code krizaka.billing.enabled} is true,
 * the no-op otherwise, so the port is always satisfied. The only public type of this package; the
 * exchange, the DTO and both adapters stay package-private.
 */
@Configuration(proxyBeanMethods = false)
public class PackPricingClientConfiguration {

  /** The declared HTTP service and its adapter, only when billing is enabled. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "krizaka.billing", name = "enabled", havingValue = "true")
  @ImportHttpServices(group = "billing", types = PackPricingExchange.class)
  static class Http {

    @Bean
    PackPricingClient httpPackPricingClient(PackPricingExchange exchange) {
      return new HttpPackPricingClient(exchange);
    }
  }

  /**
   * The fallback, so the port is always satisfied.
   *
   * @return a client that knows no price, leaving the catalogue to render "—"
   */
  @Bean
  @ConditionalOnMissingBean(PackPricingClient.class)
  PackPricingClient noOpPackPricingClient() {
    return new NoOpPackPricingClient();
  }
}
