package com.krizaka.billing.client.credit;

import com.krizaka.billing.domain.exception.InsufficientCreditsException;
import com.krizaka.billing.domain.model.ConsumptionReport;
import com.krizaka.billing.domain.model.CreditHoldCommand;
import com.krizaka.billing.domain.model.CreditHoldResponse;
import com.krizaka.billing.domain.model.MeteredStep;
import com.krizaka.billing.domain.model.SettleCreditCommand;
import com.krizaka.billing.domain.port.CreditAuthorizationClient;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Calls the billing service over synchronous HTTP.
 *
 * <p>Synchronous on purpose (AGENTS.md §6): the hold is a blocking precondition of an interactive
 * request, and putting it on the broker would add a queue hop to every chat turn and turn a broker
 * hiccup into a total outage.
 *
 * <p>Only {@link #hold} propagates a refusal. {@link #settle} and {@link #release} run off the
 * response path and swallow transport failures into a log line: a settlement that fails to reach
 * billing is a reconciliation problem, but a settlement that throws would surface as an error on a
 * request the user already saw succeed.
 */
final class HttpCreditAuthorizationClient implements CreditAuthorizationClient {

  private static final Logger log = LoggerFactory.getLogger(HttpCreditAuthorizationClient.class);
  private static final int PAYMENT_REQUIRED = 402;

  private final CreditExchange exchange;

  HttpCreditAuthorizationClient(CreditExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public CreditHoldResponse hold(CreditHoldCommand command) {
    try {
      return toDomain(exchange.hold(toWire(command)));
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == PAYMENT_REQUIRED) {
        // The billing service already computed the shortfall and which remedies this actor's plan
        // permits; carry them across rather than re-deriving numbers this side of the boundary
        // cannot see.
        throw refusal(command, e.getResponseBodyAs(CreditWire.Refusal.class));
      }
      throw e;
    } catch (IllegalArgumentException e) {
      throw new RestClientException("billing sent a hold the domain refuses", e);
    }
  }

  private static InsufficientCreditsException refusal(
      CreditHoldCommand command, CreditWire.@Nullable Refusal body) {
    if (body == null) {
      return new InsufficientCreditsException(
          command.actorId(), command.capability(), command.estimatedCredits(), 0L);
    }
    return new InsufficientCreditsException(
        command.actorId(), command.capability(), body.required(), body.balance(), body.remedies());
  }

  @Override
  public void settle(SettleCreditCommand command) {
    try {
      exchange.settle(
          command.holdId(),
          new CreditWire.Settle(
              command.holdId(),
              command.unit().name(),
              command.quantity(),
              command.idempotencyKey()));
    } catch (RestClientException e) {
      log.error("Settlement did not reach billing for hold={}", command.holdId(), e);
    }
  }

  @Override
  public void settleMeasured(String holdId, ConsumptionReport report, String idempotencyKey) {
    try {
      exchange.settleMeasured(
          holdId, new CreditWire.MeasuredSettle(toWire(report), idempotencyKey));
    } catch (RestClientException e) {
      log.error("Measured settlement did not reach billing for hold={}", holdId, e);
    }
  }

  @Override
  public boolean settleAggregate(String holdId, List<MeteredStep> steps, String idempotencyKey) {
    try {
      List<CreditWire.Step> wireSteps =
          steps.stream().map(HttpCreditAuthorizationClient::toWire).toList();
      return Boolean.TRUE.equals(
          exchange.settleAggregate(
              holdId, new CreditWire.AggregateSettle(wireSteps, idempotencyKey)));
    } catch (RestClientException e) {
      // Logged, not thrown, like every other settle on this client: a run that finished must not be
      // reported as failed because the ledger was briefly unreachable. Returning false makes the
      // caller release, and a release that also fails leaves the hold to the billing sweeper —
      // which is the recoverable end of this failure, unlike a false failure shown to the user.
      log.error("Aggregate settlement did not reach billing for hold={}", holdId, e);
      return false;
    }
  }

  @Override
  public void release(String holdId, String reason) {
    try {
      exchange.release(holdId, new CreditWire.Release(reason));
    } catch (RestClientException e) {
      log.error("Release did not reach billing for hold={}", holdId, e);
    }
  }

  private static CreditWire.HoldRequest toWire(CreditHoldCommand command) {
    return new CreditWire.HoldRequest(
        command.actorId(),
        command.capability().name(),
        command.modelName(),
        command.correlationId(),
        command.jobId(),
        command.estimatedCredits());
  }

  private static CreditHoldResponse toDomain(CreditWire.@Nullable HoldResponse response) {
    if (response == null) {
      throw new RestClientException("billing answered a hold with no body");
    }
    return new CreditHoldResponse(
        response.holdId(),
        response.granted(),
        response.dryRun(),
        response.estimatedCredits(),
        response.balanceAfterHold(),
        response.pricebookVersion());
  }

  private static CreditWire.Step toWire(MeteredStep step) {
    return new CreditWire.Step(
        step.capability().name(), step.modelName(), toWire(step.consumption()));
  }

  private static CreditWire.Consumption toWire(ConsumptionReport report) {
    return new CreditWire.Consumption(
        report.gpuSeconds(),
        report.frames(),
        report.fps(),
        report.images(),
        report.steps(),
        report.width(),
        report.height(),
        report.characters(),
        report.audioSeconds(),
        report.tokens(),
        report.durationSeconds(),
        report.calls());
  }
}
