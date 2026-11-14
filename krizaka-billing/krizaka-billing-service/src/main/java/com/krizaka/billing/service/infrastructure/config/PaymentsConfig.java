package com.krizaka.billing.service.infrastructure.config;

import com.krizaka.payments.PaymentsPort;
import com.krizaka.payments.stripe.StripeConfig;
import com.krizaka.payments.stripe.StripePayments;
import java.time.Clock;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * The payments port billing verifies the processor's notifications with (krizaka-internal#3685).
 *
 * <p>Billing only verifies and translates webhooks; it creates no checkout and calls no processor
 * API, so the endpoint's signing secret is all it needs. With no secret the port is absent and the
 * webhook refuses every notification (fail closed): nothing can settle a payment unverified.
 */
@Configuration
@EnableConfigurationProperties(PaymentsConfig.PaymentsProperties.class)
public class PaymentsConfig {

  static final String WEBHOOK_SECRET = "krizaka.billing-service.payments.webhook-secret";

  /**
   * The Stripe adapter, when the webhook's signing secret is set.
   *
   * @param properties the payments settings
   * @param clock the clock the signature's replay window is measured on
   * @return the payments port
   */
  @Bean
  @Conditional(WebhookSecretSet.class)
  public PaymentsPort paymentsPort(PaymentsProperties properties, Clock clock) {
    String secretKey = properties.secretKey();
    return new StripePayments(
        new StripeConfig(
            secretKey == null ? "" : secretKey,
            String.valueOf(properties.webhookSecret()),
            Map.of(),
            null),
        clock);
  }

  /**
   * The payments settings ({@code krizaka.billing-service.payments}).
   *
   * @param webhookSecret the signing secret of the processor's webhook endpoint that points at
   *     billing ({@code PAYMENTS_WEBHOOK_SECRET}); unset, no notification is accepted
   * @param secretKey the processor's API key, unused by billing today (it calls no processor API)
   */
  @ConfigurationProperties(prefix = "krizaka.billing-service.payments")
  public record PaymentsProperties(@Nullable String webhookSecret, @Nullable String secretKey) {

    /** Secrets are never printed. */
    @Override
    public String toString() {
      return "PaymentsProperties[webhookSecret=" + (webhookSecret == null ? "unset" : "set") + "]";
    }
  }

  /** The port exists only when the signing secret has text: a blank secret verifies nothing. */
  static final class WebhookSecretSet implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return StringUtils.hasText(context.getEnvironment().getProperty(WEBHOOK_SECRET));
    }
  }
}
