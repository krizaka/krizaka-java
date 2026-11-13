package com.krizaka.users.persistence.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clean domain DTO representing RateLimit configuration, satisfying ERR-106.
 *
 * @param dailyRequests the daily request quota of the tier, {@code null} for the edge's default;
 *     otherwise within {@link #MIN_DAILY_REQUESTS}..{@link #MAX_DAILY_REQUESTS}
 */
public record RateLimitDto(
    String tierKey, Integer requestsPerMinute, Integer concurrentJobs, Integer dailyRequests)
    implements Serializable {

  /** Lowest daily quota a row may carry (the column's CHECK). */
  public static final int MIN_DAILY_REQUESTS = 1;

  /** Highest daily quota a row may carry (the column's CHECK). */
  public static final int MAX_DAILY_REQUESTS = 10_000_000;

  public RateLimitDto {
    Objects.requireNonNull(tierKey, "tierKey cannot be null");
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

  /** A row with no daily quota of its own. */
  public RateLimitDto(String tierKey, Integer requestsPerMinute, Integer concurrentJobs) {
    this(tierKey, requestsPerMinute, concurrentJobs, null);
  }
}
