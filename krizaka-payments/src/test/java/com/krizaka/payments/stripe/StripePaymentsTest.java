package com.krizaka.payments.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.payments.CheckoutRequest;
import com.krizaka.payments.Language;
import com.krizaka.payments.PaymentEvent;
import com.krizaka.payments.PaymentsException;
import com.krizaka.payments.Price;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class StripePaymentsTest {

  private static final String SIGNING = "signing-material-for-unit-tests";
  private static final Clock NOW =
      Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);
  private static final Map<String, PlanConfig> PLANS =
      Map.of(
          "founder", new PlanConfig("k_founder", PlanConfig.Mode.SUBSCRIPTION),
          "audit", new PlanConfig("k_audit", PlanConfig.Mode.PAYMENT));

  private static StripePayments adapter(FakeStripe fake) {
    return new StripePayments(new StripeConfig("test-api-key", SIGNING, PLANS, fake.base()), NOW);
  }

  private static Function<FakeStripe.Call, FakeStripe.Reply> prices(
      String priceJson, String sessionJson) {
    return call ->
        "/v1/prices".equals(call.path())
            ? new FakeStripe.Reply(200, "{\"object\":\"list\",\"data\":[" + priceJson + "]}")
            : new FakeStripe.Reply(200, sessionJson);
  }

  private static final CheckoutRequest FOUNDER =
      new CheckoutRequest("founder", "cus_1", "a1", "https://s", "https://c", Language.FR);

  @Test
  void checkoutOfASubscriptionReadsThePriceByLookupKeyAndEnablesAutomaticTax() throws Exception {
    try (var fake =
        new FakeStripe(
            prices(
                "{\"id\":\"price_f\"}",
                "{\"id\":\"cs_1\",\"url\":\"https://checkout.example/pay\"}"))) {
      assertThat(adapter(fake).checkout(FOUNDER)).isEqualTo("https://checkout.example/pay");
      var list = fake.calls().getFirst();
      assertThat(list.method()).isEqualTo("GET");
      assertThat(list.form()).containsEntry("lookup_keys[0]", "k_founder");
      assertThat(list.headers().get("authorization")).isEqualTo("Bearer test-api-key");
      var form = fake.calls().get(1).form();
      assertThat(form)
          .containsEntry("mode", "subscription")
          .containsEntry("line_items[0][price]", "price_f")
          .containsEntry("automatic_tax[enabled]", "true")
          .containsEntry("tax_id_collection[enabled]", "true")
          .containsEntry("subscription_data[metadata][account_id]", "a1")
          .containsEntry("metadata[kind]", "founder")
          .containsEntry("locale", "fr")
          .doesNotContainKey("invoice_creation[enabled]");
    }
  }

  @Test
  void aOneOffPlanIsAPaymentWithAnInvoice() throws Exception {
    try (var fake =
        new FakeStripe(
            prices("{\"id\":\"price_a\"}", "{\"url\":\"https://checkout.example/a\"}"))) {
      adapter(fake).checkout(new CheckoutRequest("audit", "c", "a1", "s", "c", Language.EN));
      assertThat(fake.calls().get(1).form())
          .containsEntry("mode", "payment")
          .containsEntry("invoice_creation[enabled]", "true")
          .doesNotContainKey("subscription_data[metadata][account_id]");
    }
  }

  @Test
  void readsThePriceAmountCurrencyAndInterval() throws Exception {
    try (var fake =
        new FakeStripe(
            prices(
                "{\"id\":\"p\",\"unit_amount\":3900,\"currency\":\"cad\",\"recurring\":{\"interval\":\"month\"}}",
                "{}"))) {
      assertThat(adapter(fake).price("founder"))
          .isEqualTo(new Price(3900L, "cad", Price.Interval.MONTH));
    }
    try (var fake =
        new FakeStripe(prices("{\"id\":\"p\",\"unit_amount\":999,\"currency\":\"cad\"}", "{}"))) {
      assertThat(adapter(fake).price("audit")).isEqualTo(new Price(999L, "cad", null));
    }
    try (var fake = new FakeStripe(prices("{\"id\":\"p\"}", "{}"))) {
      assertThat(adapter(fake).price("audit")).isEqualTo(new Price(0L, "usd", null));
    }
  }

  @Test
  void aMissingPriceOrAnUnknownPlanIsAnErrorNotAGuess() throws Exception {
    try (var fake = new FakeStripe(prices("", "{}"))) {
      assertThatThrownBy(() -> adapter(fake).price("founder"))
          .isInstanceOfSatisfying(
              PaymentsException.class, e -> assertThat(e.status()).isEqualTo(404))
          .hasMessageContaining("lookup key k_founder");
      assertThatThrownBy(() -> adapter(fake).price("nope")).hasMessageContaining("Unknown plan");
    }
  }

  @Test
  void createsTheCustomerOncePerAccountAndOpensThePortal() throws Exception {
    try (var fake =
        new FakeStripe(
            c ->
                new FakeStripe.Reply(
                    200, "{\"id\":\"cus_1\",\"url\":\"https://portal.example\"}"))) {
      var payments = adapter(fake);
      assertThat(payments.createCustomer("e@x.test", "a1")).isEqualTo("cus_1");
      assertThat(fake.calls().getFirst().headers().get("idempotency-key")).isEqualTo("customer-a1");
      assertThat(fake.calls().getFirst().form()).containsEntry("metadata[account_id]", "a1");
      assertThat(payments.portal("cus_1", "https://r", Language.EN))
          .isEqualTo("https://portal.example");
      assertThat(fake.calls().get(1).form())
          .containsEntry("return_url", "https://r")
          .containsEntry("locale", "en");
    }
  }

  @Test
  void aProcessorErrorBecomesAPaymentsExceptionWithItsMessageOrAGenericOne() throws Exception {
    try (var fake =
        new FakeStripe(
            c -> new FakeStripe.Reply(404, "{\"error\":{\"message\":\"No such customer\"}}"))) {
      assertThatThrownBy(() -> adapter(fake).portal("c", "r", Language.EN))
          .isInstanceOfSatisfying(
              PaymentsException.class, e -> assertThat(e.status()).isEqualTo(404))
          .hasMessage("No such customer");
    }
    try (var fake = new FakeStripe(c -> new FakeStripe.Reply(400, "{}"))) {
      assertThatThrownBy(() -> adapter(fake).portal("c", "r", Language.EN))
          .isInstanceOfSatisfying(
              PaymentsException.class, e -> assertThat(e.status()).isEqualTo(400))
          .hasMessage("Stripe answered 400");
    }
  }

  @Test
  void parsesASignedWebhookOnTheInjectedClockAndRefusesAnUnsignedOne() throws Exception {
    try (var fake = new FakeStripe(c -> new FakeStripe.Reply(200, "{}"))) {
      var payments = adapter(fake);
      String body =
          "{\"id\":\"evt_1\",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_1\",\"customer\":\"cus_1\"}}}";
      var event = payments.parseWebhook(body, WebhookSignature.sign(body, SIGNING, 1_800_000_000L));
      assertThat(event)
          .isInstanceOfSatisfying(
              PaymentEvent.SubscriptionChanged.class,
              e -> assertThat(e.status()).isEqualTo("canceled"));
      assertThatThrownBy(() -> payments.parseWebhook(body, null))
          .isInstanceOf(PaymentsException.class);
      assertThatThrownBy(
              () ->
                  payments.parseWebhook(
                      "not json", WebhookSignature.sign("not json", SIGNING, 1_800_000_000L)))
          .isInstanceOf(PaymentsException.class);
    }
  }

  @Test
  void aPaidCheckoutCarriesItsSessionIdAsThePaymentReference() {
    String body =
        "{\"id\":\"evt_2\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":"
            + "{\"id\":\"cs_test_1\",\"mode\":\"payment\",\"payment_status\":\"paid\","
            + "\"customer\":\"cus_1\",\"metadata\":{\"account_id\":\"a1\",\"kind\":\"audit\"}}}}";

    assertThat(WebhookTranslator.translate(body))
        .isEqualTo(new PaymentEvent.PaymentPaid("evt_2", "audit", "a1", "cus_1", "cs_test_1"));
  }

  @Test
  void aPaymentEventBuiltWithoutAReferenceHasNone() {
    assertThat(new PaymentEvent.PaymentPaid("evt_3", "audit", "a1", null).reference()).isNull();
  }

  @Test
  void theConfigurationNeverPrintsItsSecrets() {
    assertThat(new StripeConfig("sk_live_x", "whsec_y", PLANS, null).toString())
        .doesNotContain("sk_live_x")
        .doesNotContain("whsec_y");
  }
}
