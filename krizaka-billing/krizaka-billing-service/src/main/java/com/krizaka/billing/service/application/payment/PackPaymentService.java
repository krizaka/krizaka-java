package com.krizaka.billing.service.application.payment;

import com.krizaka.billing.service.application.service.PackPricingService;
import com.krizaka.billing.service.application.service.PackSubscriptionService;
import com.krizaka.payments.EventHandlers;
import com.krizaka.payments.EventStore;
import com.krizaka.payments.PaymentEvent;
import com.krizaka.payments.PaymentEvents;
import com.krizaka.payments.PaymentsException;
import com.krizaka.payments.PaymentsPort;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles a pack's payment from the processor's notification (krizaka-internal#3685).
 *
 * <p>The notification is believed only once the payments port has verified its proof of origin over
 * the raw body ({@link PaymentsPort#parseWebhook}); then {@link PaymentEvents#applyOnce} applies it
 * once per event id, in the same transaction as the payment it records, so a replay changes nothing
 * and a failure records neither. The payment is bound to the account and the pack the processor
 * says were paid; {@link PackSubscriptionService#subscribe} later spends it once, for that actor
 * and that pack only.
 *
 * <p>Fail closed: with no payments port configured every notification is refused ({@code 503}), and
 * an event that names no known pack, or no payment reference, settles nothing.
 */
@Service
public class PackPaymentService {

  /**
   * The checkout's plan name for a pack: {@code pack:<packKey>}. A pack is bought through the
   * processor's hosted checkout under this kind, and only this kind settles a pack.
   */
  public static final String PACK_KIND_PREFIX = "pack:";

  private static final Logger log = LoggerFactory.getLogger(PackPaymentService.class);

  private final @Nullable PaymentsPort payments;
  private final EventStore appliedEvents;
  private final PackPricingService packPricingService;
  private final PackSubscriptionService packSubscriptionService;
  private final EventHandlers handlers;

  /**
   * Wires the service.
   *
   * @param payments the payments port, absent when the processor is not configured
   * @param appliedEvents the ids of the notifications already applied
   * @param packPricingService the pack catalogue
   * @param packSubscriptionService where a settled payment is recorded
   */
  public PackPaymentService(
      Optional<PaymentsPort> payments,
      EventStore appliedEvents,
      PackPricingService packPricingService,
      PackSubscriptionService packSubscriptionService) {
    this.payments = payments.orElse(null);
    this.appliedEvents = appliedEvents;
    this.packPricingService = packPricingService;
    this.packSubscriptionService = packSubscriptionService;
    this.handlers = new EventHandlers().on(PaymentEvent.PaymentPaid.class, this::settle);
  }

  /**
   * Verifies a notification and applies it once.
   *
   * @param rawBody the body, byte for byte as received
   * @param signature the processor's signature header, {@code null} when absent
   * @return {@code APPLIED}, {@code DUPLICATE} (already applied) or {@code IGNORED} (nothing of
   *     billing's)
   * @throws PaymentsException {@code 400} on a missing, bad or replayed signature or a body that is
   *     not an event, {@code 503} when no payments processor is configured
   */
  @Transactional
  public PaymentEvents.Outcome receive(String rawBody, @Nullable String signature) {
    if (payments == null) {
      throw new PaymentsException(503, "No payments processor is configured");
    }
    PaymentEvent event = payments.parseWebhook(rawBody, signature);
    PaymentEvents.Outcome outcome = PaymentEvents.applyOnce(appliedEvents, event, handlers);
    log.info("Payment notification {} ({}): {}", event.id(), event.type(), outcome);
    return outcome;
  }

  private boolean settle(PaymentEvent.PaymentPaid paid) {
    String reference = paid.reference();
    if (reference == null || reference.isBlank() || !paid.kind().startsWith(PACK_KIND_PREFIX)) {
      return false;
    }
    String packKey = paid.kind().substring(PACK_KIND_PREFIX.length());
    if (packPricingService.find(packKey).isEmpty()) {
      log.warn("Payment {} names the unknown pack {}: nothing settled", paid.id(), packKey);
      return false;
    }
    packSubscriptionService.recordSettledPayment(reference, paid.accountId(), packKey);
    return true;
  }
}
