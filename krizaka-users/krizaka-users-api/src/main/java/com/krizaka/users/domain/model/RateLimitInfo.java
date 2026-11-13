package com.krizaka.users.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable DTO representing a rate limit tier configuration. Enforces compact constructor
 * validation per ERR-106.
 *
 * @param dailyRequests the tier's daily request quota, {@code null} when the row sets none (the
 *     edge then keeps its own default); otherwise within {@link #MIN_DAILY_REQUESTS}..{@link
 *     #MAX_DAILY_REQUESTS}
 */
public record RateLimitInfo(
    String tierKey, Integer requestsPerMinute, Integer concurrentJobs, Integer dailyRequests)
    implements Serializable {

  /** Lowest daily quota a tier may carry. */
  public static final int MIN_DAILY_REQUESTS = 1;

  /** Highest daily quota a tier may carry. */
  public static final int MAX_DAILY_REQUESTS = 10_000_000;

  public RateLimitInfo {
    Objects.requireNonNull(tierKey, "tierKey cannot be null");
    if (tierKey.isBlank()) {
      throw new IllegalArgumentException("tierKey cannot be blank");
    }
    Objects.requireNonNull(requestsPerMinute, "requestsPerMinute cannot be null");
    Objects.requireNonNull(concurrentJobs, "concurrentJobs cannot be null");
    if (dailyRequests != null
        && (dailyRequests < MIN_DAILY_REQUESTS || dailyRequests > MAX_DAILY_REQUESTS)) {
      throw new IllegalArgumentException(
          "dailyRequests must be between "
              + MIN_DAILY_REQUESTS
              + " and "
              + MAX_DAILY_REQUESTS
              + ": "
              + dailyRequests);
    }
  }

  /** A tier with no daily quota of its own. */
  public RateLimitInfo(String tierKey, Integer requestsPerMinute, Integer concurrentJobs) {
    this(tierKey, requestsPerMinute, concurrentJobs, null);
  }
}
