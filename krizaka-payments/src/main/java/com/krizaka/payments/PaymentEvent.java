package com.krizaka.payments;

import org.jspecify.annotations.Nullable;

/**
 * What the domain learns from a processor, vendor-free. {@link #id()} is unique per event: the
 * domain applies each once ({@link PaymentEvents#applyOnce}).
 */
public sealed interface PaymentEvent {

  /**
   * The event's unique id.
   *
   * @return the id
   */
  String id();

  /**
   * The event's type name, the same words as {@code @krizaka/payments}.
   *
   * @return {@code payment_paid}, {@code subscription_started}, {@code subscription_changed} or
   *     {@code ignored}
   */
  String type();

  /**
   * A one-off plan was paid.
   *
   * @param id the event's unique id
   * @param kind the plan, named by the product
   * @param accountId the product's account that paid
   * @param customerId the processor's customer, {@code null} when the processor names none
   * @param reference the processor's reference of this payment (Stripe: the Checkout Session id,
   *     which the success URL hands back to the buyer's browser), {@code null} when unknown
   */
  record PaymentPaid(
      String id,
      String kind,
      String accountId,
      @Nullable String customerId,
      @Nullable String reference)
      implements PaymentEvent {

    /**
     * A paid plan with no payment reference (the shape before the reference was carried).
     *
     * @param id the event's unique id
     * @param kind the plan, named by the product
     * @param accountId the product's account that paid
     * @param customerId the processor's customer, {@code null} when the processor names none
     */
    public PaymentPaid(String id, String kind, String accountId, @Nullable String customerId) {
      this(id, kind, accountId, customerId, null);
    }

    @Override
    public String type() {
      return "payment_paid";
    }
  }

  /** A subscription's Checkout completed. */
  record SubscriptionStarted(
      String id, String accountId, @Nullable String customerId, @Nullable String subscriptionId)
      implements PaymentEvent {
    @Override
    public String type() {
      return "subscription_started";
    }
  }

  /** A subscription changed (created, updated, deleted); {@code periodEnd} is epoch seconds. */
  record SubscriptionChanged(
      String id,
      @Nullable String accountId,
      @Nullable String customerId,
      String subscriptionId,
      String status,
      boolean active,
      @Nullable Long periodEnd)
      implements PaymentEvent {
    @Override
    public String type() {
      return "subscription_changed";
    }
  }

  /** Anything the domain does not need. */
  record Ignored(String id) implements PaymentEvent {
    @Override
    public String type() {
      return "ignored";
    }
  }
}
