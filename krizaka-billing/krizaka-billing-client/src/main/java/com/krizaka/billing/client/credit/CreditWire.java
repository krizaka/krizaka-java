package com.krizaka.billing.client.credit;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The wire shapes of the credit endpoints, as package-private records: the JSON is converted to and
 * from the domain at the adapter and never travels further (anti-corruption layer, ERR-127). Enums
 * travel by name, as the domain's own enums serialise.
 */
final class CreditWire {

  private CreditWire() {}

  /** Request body of a hold. */
  record HoldRequest(
      String actorId,
      String capability,
      @Nullable String modelName,
      String correlationId,
      @Nullable String jobId,
      long estimatedCredits) {}

  /** Billing's answer to a granted hold. */
  record HoldResponse(
      String holdId,
      boolean granted,
      boolean dryRun,
      long estimatedCredits,
      long balanceAfterHold,
      int pricebookVersion) {}

  /** Request body of a settlement by quantity. */
  record Settle(String holdId, String unit, BigDecimal quantity, String idempotencyKey) {}

  /** What a run consumed, in every unit billing can price. */
  record Consumption(
      @Nullable BigDecimal gpuSeconds,
      @Nullable Integer frames,
      @Nullable Integer fps,
      @Nullable Integer images,
      @Nullable Integer steps,
      @Nullable Integer width,
      @Nullable Integer height,
      @Nullable Long characters,
      @Nullable BigDecimal audioSeconds,
      @Nullable Long tokens,
      @Nullable BigDecimal durationSeconds,
      @Nullable Long calls) {}

  /** Request body of the measured settlement. */
  record MeasuredSettle(Consumption consumption, String idempotencyKey) {}

  /** One metered step of an aggregate settlement. */
  record Step(String capability, @Nullable String modelName, Consumption consumption) {}

  /** Request body of the aggregate settlement. */
  record AggregateSettle(List<Step> steps, String idempotencyKey) {}

  /** Request body of the release. */
  record Release(String reason) {}

  /**
   * Billing's structured refusal (402): the shortfall and the remedies the actor's plan permits.
   * Contract copy: that DTO is owned by the billing service, so this SDK reads the wire shape
   * rather than importing it.
   */
  record Refusal(
      @Nullable String status,
      @Nullable String capability,
      long required,
      long balance,
      @Nullable List<String> remedies) {}
}
