package com.krizaka.users.application.service;

import com.krizaka.users.domain.model.RateLimitInfo;
import com.krizaka.users.domain.ports.inbound.RateLimitProvider;
import com.krizaka.users.persistence.domain.model.RateLimitDto;
import com.krizaka.users.persistence.domain.ports.RateLimitPersistenceProvider;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation of the {@link RateLimitProvider} inbound port [ERR-105].
 *
 * <p>Lives in {@code application.service} alongside its sibling {@code UserProfileProviderImpl}: it
 * implements an inbound port by delegating to an outbound persistence port — an application
 * service, not a persistence {@code *Adapter} ([ERR-130]). The single-use DTO mapping is inlined.
 */
@Service
@Transactional(readOnly = true)
class RateLimitProviderImpl implements RateLimitProvider {

  private final RateLimitPersistenceProvider provider;

  RateLimitProviderImpl(RateLimitPersistenceProvider provider) {
    this.provider = Objects.requireNonNull(provider, "RateLimitPersistenceProvider cannot be null");
  }

  @Override
  public Optional<RateLimitInfo> getRateLimit(String tierKey) {
    Objects.requireNonNull(tierKey, "Tier key cannot be null");
    return provider.findById(tierKey).map(RateLimitProviderImpl::toInfo);
  }

  @Override
  public Optional<String> getDefaultTierKey() {
    return provider.findDefaultTierKey();
  }

  private static RateLimitInfo toInfo(RateLimitDto dto) {
    return new RateLimitInfo(
        dto.tierKey(), dto.requestsPerMinute(), dto.concurrentJobs(), dto.dailyRequests());
  }
}
