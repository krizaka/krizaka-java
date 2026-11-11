package com.krizaka.messaging.dedup;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * The {@code dedup} component of {@code /actuator/health}: DOWN when the claim store cannot claim
 * (its database does not answer, its table is missing) — every listener deduplicating through it
 * then fails, and the messages go to their dead-letter queues. UP otherwise, naming the store.
 */
public final class MessageDedupHealthIndicator implements HealthIndicator {
  private static final Logger LOG = LoggerFactory.getLogger(MessageDedupHealthIndicator.class);

  private final MessageDedup dedup;

  /**
   * The indicator over a store.
   *
   * @param dedup the store, observed or not
   */
  public MessageDedupHealthIndicator(MessageDedup dedup) {
    this.dedup = Objects.requireNonNull(dedup, "dedup");
  }

  @Override
  public Health health() {
    MessageDedup store =
        dedup instanceof ObservedMessageDedup observed ? observed.delegate() : dedup;
    try {
      dedup.verify();
      return Health.up().withDetail("store", store.getClass().getSimpleName()).build();
    } catch (RuntimeException e) {
      LOG.warn("message dedup store unhealthy", e);
      return Health.down()
          .withDetail("store", store.getClass().getSimpleName())
          .withDetail("error", e.getClass().getName())
          .build();
    }
  }
}
