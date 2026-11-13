package com.krizaka.users.persistence.domain.ports;

import com.krizaka.users.persistence.domain.model.RateLimitDto;
import java.util.Optional;

/** Port interface for managing RateLimit persistence operations. */
public interface RateLimitPersistenceProvider {

  Optional<RateLimitDto> findById(String tierKey);

  RateLimitDto save(RateLimitDto rateLimitDto);

  /**
   * Sets (or clears, with {@code null}) the daily request quota of an existing tier, leaving its
   * other columns as they are.
   *
   * @throws IllegalArgumentException when the tier does not exist
   */
  RateLimitDto updateDailyRequests(String tierKey, Integer dailyRequests);

  /**
   * Resolves the tier key flagged as the system default in the database.
   *
   * @return the default tier key, or empty if none is flagged.
   */
  Optional<String> findDefaultTierKey();
}
