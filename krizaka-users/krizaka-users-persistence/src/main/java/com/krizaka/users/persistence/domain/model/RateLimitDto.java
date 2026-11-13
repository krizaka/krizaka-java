package com.krizaka.users.persistence.domain.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clean domain DTO representing RateLimit configuration, satisfying ERR-106.
 *
 * @param dailyRequests the daily request quota of the tier, {@code null} for the edge's default;
 *     its bounds are the service's rule and the column's CHECK
 */
public record RateLimitDto(
    String tierKey, Integer requestsPerMinute, Integer concurrentJobs, Integer dailyRequests)
    implements Serializable {

  public RateLimitDto {
    Objects.requireNonNull(tierKey, "tierKey cannot be null");
    Objects.requireNonNull(requestsPerMinute, "requestsPerMinute cannot be null");
    Objects.requireNonNull(concurrentJobs, "concurrentJobs cannot be null");
  }

  /** A row with no daily quota of its own. */
  public RateLimitDto(String tierKey, Integer requestsPerMinute, Integer concurrentJobs) {
    this(tierKey, requestsPerMinute, concurrentJobs, null);
  }
}
