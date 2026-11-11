package com.krizaka.billing.client.entitlement;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** The billing service's entitlement read, declared; {@code HttpEntitlementProvider} adapts it. */
@HttpExchange("/internal/v1/billing/entitlements")
interface EntitlementExchange {

  @GetExchange("/{actorId}")
  @Nullable EntitlementBody forActor(@PathVariable String actorId);
}
