package com.krizaka.billing.client.credit;

import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

/**
 * Wires {@link CreditAuthorizationClient}: the HTTP adapter when {@code krizaka.billing.enabled} is
 * true, the no-op otherwise, so the port is always satisfied. The only public type of this package;
 * the exchange, the DTOs and both adapters stay package-private.
 */
@Configuration(proxyBeanMethods = false)
public class CreditClientConfiguration {

  /** The declared HTTP service and its adapter, only when billing is enabled. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "krizaka.billing", name = "enabled", havingValue = "true")
  @ImportHttpServices(group = "billing", types = CreditExchange.class)
  static class Http {

    @Bean
    CreditAuthorizationClient httpCreditAuthorizationClient(CreditExchange exchange) {
      return new HttpCreditAuthorizationClient(exchange);
    }
  }

  /**
   * The fallback, so the port is always satisfied.
   *
   * @return a client that refuses the write rather than accepting it silently
   */
  @Bean
  @ConditionalOnMissingBean(CreditAuthorizationClient.class)
  CreditAuthorizationClient noOpCreditAuthorizationClient() {
    return new NoOpCreditAuthorizationClient();
  }
}
