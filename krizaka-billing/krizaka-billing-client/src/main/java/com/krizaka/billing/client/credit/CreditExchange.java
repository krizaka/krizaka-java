package com.krizaka.billing.client.credit;

import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The billing service's credit endpoints, declared; {@code HttpCreditAuthorizationClient} adapts
 * them.
 */
@HttpExchange("/internal/v1/billing/credits")
interface CreditExchange {

  @PostExchange("/holds")
  CreditWire.@Nullable HoldResponse hold(@RequestBody CreditWire.HoldRequest body);

  @PostExchange("/{holdId}/settle")
  void settle(@PathVariable String holdId, @RequestBody CreditWire.Settle body);

  @PostExchange("/{holdId}/settle-measured")
  void settleMeasured(@PathVariable String holdId, @RequestBody CreditWire.MeasuredSettle body);

  @PostExchange("/{holdId}/settle-aggregate")
  @Nullable Boolean settleAggregate(
      @PathVariable String holdId, @RequestBody CreditWire.AggregateSettle body);

  @PostExchange("/{holdId}/release")
  void release(@PathVariable String holdId, @RequestBody CreditWire.Release body);
}
