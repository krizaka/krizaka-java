package com.krizaka.billing.client.pricing;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** The billing service's pack price table, declared; {@code HttpPackPricingClient} adapts it. */
@HttpExchange("/api/v1/billing/packs")
interface PackPricingExchange {

  @GetExchange("/prices")
  @Nullable List<PackPriceBody> prices();
}
