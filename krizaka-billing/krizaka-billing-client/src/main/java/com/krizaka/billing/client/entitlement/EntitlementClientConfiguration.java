package com.krizaka.billing.client.entitlement;

import com.krizaka.billing.domain.port.EntitlementProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires {@link EntitlementProvider}: the HTTP adapter when {@code krizaka.billing.enabled} is true,
 * the no-op otherwise, and the cache invalidation fed by {@code evt.subscription.*}. The only
 * public type of this package.
 */
@Configuration(proxyBeanMethods = false)
@Import(EntitlementCacheInvalidationConfiguration.class)
public class EntitlementClientConfiguration {

  /** The declared HTTP service and its adapter, only when billing is enabled. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "krizaka.billing", name = "enabled", havingValue = "true")
  @ImportHttpServices(group = "billing", types = EntitlementExchange.class)
  static class Http {

    /**
     * Typed as the adapter, not the port: the invalidation listener evicts from its cache.
     *
     * @param exchange the declared HTTP service
     * @return a provider that reads entitlements from the billing service, cached to their own
     *     staleness bound
     */
    @Bean
    HttpEntitlementProvider httpEntitlementProvider(EntitlementExchange exchange) {
      return new HttpEntitlementProvider(exchange);
    }
  }

  /**
   * The fallback, so the port is always satisfied.
   *
   * @return a provider that gates nothing
   */
  @Bean
  @ConditionalOnMissingBean(EntitlementProvider.class)
  EntitlementProvider noOpEntitlementProvider() {
    return new NoOpEntitlementProvider();
  }
}
