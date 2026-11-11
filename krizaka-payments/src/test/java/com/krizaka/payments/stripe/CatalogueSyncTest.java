package com.krizaka.payments.stripe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.krizaka.payments.PaymentsException;
import com.krizaka.payments.Price;
import java.util.List;
import org.junit.jupiter.api.Test;

class CatalogueSyncTest {

  private static final CataloguePlan PLAN =
      new CataloguePlan(
          "sovkora.plan.pro", "Sovkora Pro", 4900L, "usd", Price.Interval.MONTH, "txcd_10103001");

  private static FakeStripe.Reply existing(FakeStripe.Call call, String listJson) {
    return switch (call.path()) {
      case "/v1/prices" ->
          "GET".equals(call.method())
              ? new FakeStripe.Reply(200, "{\"data\":[" + listJson + "]}")
              : new FakeStripe.Reply(200, "{\"id\":\"price_new\"}");
      case "/v1/products" -> new FakeStripe.Reply(200, "{\"id\":\"prod_1\"}");
      default -> new FakeStripe.Reply(200, "{\"id\":\"price_old\"}");
    };
  }

  @Test
  void createsTheProductAndThePriceWhenTheLookupKeyIsNew() throws Exception {
    try (var fake = new FakeStripe(c -> existing(c, ""))) {
      var out = new CatalogueSync("sk_test_x", fake.base(), "sovkora", false).sync(List.of(PLAN));
      assertThat(out)
          .containsExactly(
              new CatalogueSync.Result("sovkora.plan.pro", CatalogueSync.Status.CREATED));
      var product = fake.calls().get(1).form();
      assertThat(product)
          .containsEntry("name", "Sovkora Pro")
          .containsEntry("tax_code", "txcd_10103001")
          .containsEntry("metadata[product]", "sovkora");
      assertThat(fake.calls().get(2).form())
          .containsEntry("product", "prod_1")
          .containsEntry("unit_amount", "4900")
          .containsEntry("currency", "usd")
          .containsEntry("tax_behavior", "exclusive")
          .containsEntry("lookup_key", "sovkora.plan.pro")
          .containsEntry("recurring[interval]", "month")
          .doesNotContainKey("transfer_lookup_key");
    }
  }

  @Test
  void leavesAPriceThatAlreadyMatches() throws Exception {
    String row =
        "{\"id\":\"price_old\",\"product\":\"prod_1\",\"unit_amount\":4900,\"currency\":\"usd\",\"recurring\":{\"interval\":\"month\"}}";
    try (var fake = new FakeStripe(c -> existing(c, row))) {
      var out = new CatalogueSync("sk_test_x", fake.base(), "sovkora", false).sync(List.of(PLAN));
      assertThat(out.getFirst().status()).isEqualTo(CatalogueSync.Status.EXISTS);
      assertThat(fake.calls()).hasSize(1);
    }
  }

  @Test
  void aChangedAmountCreatesANewPriceThatTakesTheKeyOverAndRetiresTheOldOne() throws Exception {
    String row =
        "{\"id\":\"price_old\",\"product\":\"prod_1\",\"unit_amount\":3900,\"currency\":\"usd\",\"recurring\":{\"interval\":\"month\"}}";
    try (var fake = new FakeStripe(c -> existing(c, row))) {
      var out = new CatalogueSync("sk_test_x", fake.base(), "sovkora", false).sync(List.of(PLAN));
      assertThat(out.getFirst().status()).isEqualTo(CatalogueSync.Status.REPLACED);
      assertThat(fake.calls().get(1).form())
          .containsEntry("product", "prod_1")
          .containsEntry("unit_amount", "4900")
          .containsEntry("transfer_lookup_key", "true");
      assertThat(fake.calls().get(2).path()).isEqualTo("/v1/prices/price_old");
      assertThat(fake.calls().get(2).form()).containsEntry("active", "false");
    }
  }

  @Test
  void liveModeIsTheOwnersAndNothingIsCalledWithoutIt() throws Exception {
    try (var fake = new FakeStripe(c -> existing(c, ""))) {
      assertThatThrownBy(
              () ->
                  new CatalogueSync("sk_live_x", fake.base(), "sovkora", false).sync(List.of(PLAN)))
          .isInstanceOfSatisfying(
              PaymentsException.class, e -> assertThat(e.status()).isEqualTo(403));
      assertThat(fake.calls()).isEmpty();
      new CatalogueSync("sk_live_x", fake.base(), "sovkora", true).sync(List.of());
    }
  }
}
