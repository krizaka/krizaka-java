package com.krizaka.billing.client.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.krizaka.billing.domain.model.PackPrice;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/** The adapter's mapping, cache and degradation, against a stubbed exchange: no HTTP involved. */
class HttpPackPricingClientTest {

  private final AtomicInteger calls = new AtomicInteger();
  private @Nullable List<PackPriceBody> table = List.of(new PackPriceBody("a", 100, 5, true));
  private boolean down;

  private final HttpPackPricingClient client =
      new HttpPackPricingClient(
          () -> {
            calls.incrementAndGet();
            if (down) {
              throw new ResourceAccessException("down");
            }
            return table;
          });

  @Test
  void pricesAreMappedToTheDomainAndFilteredToTheRequestedPacks() {
    table = List.of(new PackPriceBody("a", 100, 5, true), new PackPriceBody("b", 200, 7, false));

    assertThat(client.prices(Set.of("a", "unknown")))
        .containsOnly(Map.entry("a", new PackPrice("a", 100, 5, true)));
  }

  @Test
  void theTableIsFetchedOnceWhateverTheNumberOfPacksAsked() {
    client.prices(Set.of("a"));
    client.prices(Set.of("b"));

    assertThat(calls).hasValue(1);
  }

  @Test
  void anOutageYieldsNoPriceAndIsNotRetriedAtOnce() {
    down = true;

    assertThat(client.prices(Set.of("a"))).isEmpty();
    assertThat(client.prices(Set.of("a"))).isEmpty();
    assertThat(calls).hasValue(1);
  }

  @Test
  void aPriceTheDomainRefusesYieldsNoPrice() {
    table = List.of(new PackPriceBody("a", -1, 5, true));

    assertThat(client.prices(Set.of("a"))).isEmpty();
  }

  @Test
  void aMissingTableYieldsNoPrice() {
    table = null;

    assertThat(client.prices(Set.of("a"))).isEmpty();
  }
}
