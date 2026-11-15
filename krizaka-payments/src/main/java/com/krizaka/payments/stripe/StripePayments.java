package com.krizaka.payments.stripe;

import com.krizaka.payments.CheckoutRequest;
import com.krizaka.payments.Language;
import com.krizaka.payments.PaymentEvent;
import com.krizaka.payments.PaymentsException;
import com.krizaka.payments.PaymentsPort;
import com.krizaka.payments.Price;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PriceListParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Stripe adapter of the payments port. Stripe Tax computes the taxes from the buyer's address;
 * prices are found by lookup key. The clock is injected; nothing is read from the environment.
 */
public final class StripePayments implements PaymentsPort {

  private static final String ACCOUNT_ID_KEY = "account_id";

  private final StripeConfig config;
  private final Clock clock;
  private final StripeClient client;

  /**
   * Creates the adapter.
   *
   * @param config keys, plans and (for tests) the API base
   * @param clock the clock the webhook's replay window is measured on
   */
  public StripePayments(StripeConfig config, Clock clock) {
    this.config = config;
    this.clock = clock;
    this.client = StripeClients.create(config.secretKey(), config.apiBase());
  }

  @Override
  public Price price(String kind) {
    return StripeClients.toPrice(priceRow(kind));
  }

  @Override
  public String createCustomer(String email, String accountId) {
    return call(
        () ->
            client
                .customers()
                .create(
                    CustomerCreateParams.builder()
                        .setEmail(email)
                        .putMetadata(ACCOUNT_ID_KEY, accountId)
                        .build(),
                    RequestOptions.builder().setIdempotencyKey("customer-" + accountId).build())
                .getId());
  }

  @Override
  public String checkout(CheckoutRequest request) {
    PlanConfig plan = plan(request.kind());
    String priceId = priceRow(request.kind()).getId();
    boolean subscription = plan.mode() == PlanConfig.Mode.SUBSCRIPTION;
    var params =
        SessionCreateParams.builder()
            .setMode(
                subscription
                    ? SessionCreateParams.Mode.SUBSCRIPTION
                    : SessionCreateParams.Mode.PAYMENT)
            .setCustomer(request.customerId())
            .setClientReferenceId(request.accountId())
            .addLineItem(
                SessionCreateParams.LineItem.builder().setPrice(priceId).setQuantity(1L).build())
            .setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build())
            .setCustomerUpdate(
                SessionCreateParams.CustomerUpdate.builder()
                    .setAddress(SessionCreateParams.CustomerUpdate.Address.AUTO)
                    .setName(SessionCreateParams.CustomerUpdate.Name.AUTO)
                    .build())
            .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED)
            .setTaxIdCollection(
                SessionCreateParams.TaxIdCollection.builder().setEnabled(true).build())
            .putMetadata(ACCOUNT_ID_KEY, request.accountId())
            .putMetadata("kind", request.kind())
            .setSuccessUrl(request.successUrl())
            .setCancelUrl(request.cancelUrl())
            .setLocale(
                request.language() == Language.FR
                    ? SessionCreateParams.Locale.FR
                    : SessionCreateParams.Locale.EN);
    if (subscription) {
      params.setSubscriptionData(
          SessionCreateParams.SubscriptionData.builder()
              .putMetadata(ACCOUNT_ID_KEY, request.accountId())
              .build());
    } else {
      params.setInvoiceCreation(
          SessionCreateParams.InvoiceCreation.builder().setEnabled(true).build());
    }
    return call(() -> client.checkout().sessions().create(params.build()).getUrl());
  }

  @Override
  public String portal(String customerId, String returnUrl, Language language) {
    var params =
        com.stripe.param.billingportal.SessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .setLocale(
                language == Language.FR
                    ? com.stripe.param.billingportal.SessionCreateParams.Locale.FR
                    : com.stripe.param.billingportal.SessionCreateParams.Locale.EN)
            .build();
    return call(() -> client.billingPortal().sessions().create(params).getUrl());
  }

  @Override
  public PaymentEvent parseWebhook(String rawBody, @Nullable String signature) {
    WebhookSignature.verify(rawBody, signature, config.webhookSecret(), Instant.now(clock));
    return WebhookTranslator.translate(rawBody);
  }

  private PlanConfig plan(String kind) {
    PlanConfig found = config.plans().get(kind);
    if (found == null) {
      throw new PaymentsException(404, "Unknown plan " + kind);
    }
    return found;
  }

  private com.stripe.model.Price priceRow(String kind) {
    String lookupKey = plan(kind).lookupKey();
    List<com.stripe.model.Price> rows =
        call(
            () ->
                client
                    .prices()
                    .list(
                        PriceListParams.builder()
                            .addLookupKey(lookupKey)
                            .setActive(true)
                            .setLimit(1L)
                            .build())
                    .getData());
    if (rows.isEmpty()) {
      throw new PaymentsException(404, "No active price with the lookup key " + lookupKey);
    }
    return rows.getFirst();
  }

  static <T> T call(StripeCall<T> action) {
    try {
      return action.run();
    } catch (StripeException e) {
      int status = e.getStatusCode() == null ? 502 : e.getStatusCode();
      throw new PaymentsException(status, messageOf(e, status), e);
    }
  }

  private static String messageOf(StripeException e, int status) {
    if (e.getStripeError() != null && e.getStripeError().getMessage() != null) {
      return e.getStripeError().getMessage();
    }
    return "Stripe answered " + status;
  }

  /** One SDK call. */
  @FunctionalInterface
  interface StripeCall<T> {
    T run() throws StripeException;
  }
}
