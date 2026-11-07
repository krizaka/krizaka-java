package com.krizaka.billing.client.config;

import com.krizaka.billing.client.credit.CreditClientConfiguration;
import com.krizaka.billing.client.entitlement.EntitlementClientConfiguration;
import com.krizaka.billing.client.pricing.PackPricingClientConfiguration;
import com.krizaka.billing.client.provisioning.PackProvisioningClientConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * Selects the billing adapters at bootstrap.
 *
 * <p>The whole point of the two-bean arrangement in each feature: producers depend on the ports and
 * never learn whether billing is deployed. Turning billing on is a property change, not a code
 * change, and turning it off cannot leave a half-wired call site behind. Each feature package wires
 * its own port; this class imports them and configures the one HTTP service group they share.
 *
 * <p>Registered through the AutoConfiguration SPI so a consuming service inherits it by adding the
 * dependency, without widening its component scan.
 */
@AutoConfiguration
@EnableConfigurationProperties(BillingProperties.class)
@Import({
  CreditClientConfiguration.class,
  EntitlementClientConfiguration.class,
  PackPricingClientConfiguration.class,
  PackProvisioningClientConfiguration.class
})
public class BillingClientAutoConfiguration {

  /**
   * Base URL, timeouts and service token of the billing HTTP service group, only when billing is
   * enabled (a disabled host has no secret to sign with).
   *
   * @param properties the producer-side wiring
   * @param environment where the Spring Boot group properties are read
   * @return the group configurer
   */
  @Bean
  @ConditionalOnProperty(prefix = "krizaka.billing", name = "enabled", havingValue = "true")
  BillingHttpGroupConfigurer billingHttpGroupConfigurer(
      BillingProperties properties, Environment environment) {
    return new BillingHttpGroupConfigurer(properties, environment);
  }
}
