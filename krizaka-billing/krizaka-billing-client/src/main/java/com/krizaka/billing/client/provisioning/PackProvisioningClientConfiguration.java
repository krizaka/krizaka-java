package com.krizaka.billing.client.provisioning;

import com.krizaka.billing.domain.port.PackProvisioningClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires {@link PackProvisioningClient}: the HTTP adapter when {@code krizaka.billing.enabled} is
 * true, the no-op otherwise, so the port is always satisfied. The only public type of this package;
 * the exchange, the DTOs and both adapters stay package-private.
 */
@Configuration(proxyBeanMethods = false)
public class PackProvisioningClientConfiguration {

  /** The declared HTTP service and its adapter, only when billing is enabled. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "krizaka.billing", name = "enabled", havingValue = "true")
  @ImportHttpServices(group = "billing", types = PackProvisioningExchange.class)
  static class Http {

    @Bean
    PackProvisioningClient httpPackProvisioningClient(PackProvisioningExchange exchange) {
      return new HttpPackProvisioningClient(exchange);
    }
  }

  /**
   * The fallback, so the port is always satisfied.
   *
   * @return a client that refuses the write rather than accepting it silently
   */
  @Bean
  @ConditionalOnMissingBean(PackProvisioningClient.class)
  PackProvisioningClient noOpPackProvisioningClient() {
    return new NoOpPackProvisioningClient();
  }
}
