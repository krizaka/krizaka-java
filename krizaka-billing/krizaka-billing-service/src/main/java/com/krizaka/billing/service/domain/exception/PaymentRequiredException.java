package com.krizaka.billing.service.domain.exception;

import java.io.Serial;

/**
 * Thrown when a priced pack is asked for without a settled payment of its own.
 *
 * <p>Maps to {@code 402}: the offer exists and the caller may buy it, but the payment the processor
 * confirmed is missing, belongs to another actor or pack, or was already used.
 */
public class PaymentRequiredException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs the refusal.
   *
   * @param packKey the pack that needs a payment
   */
  public PaymentRequiredException(String packKey) {
    super("pack %s needs a settled payment".formatted(packKey));
  }
}
