package com.krizaka.users.persistence.application.service;

import com.krizaka.users.persistence.domain.model.RateLimitDto;
import com.krizaka.users.persistence.domain.ports.RateLimitPersistenceProvider;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.entity.RateLimitEntity;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.repository.RateLimitRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of RateLimitPersistenceProvider. */
@Service
@Transactional
class RateLimitPersistenceProviderImpl implements RateLimitPersistenceProvider {

  private final RateLimitRepository repository;

  RateLimitPersistenceProviderImpl(RateLimitRepository repository) {
    this.repository = Objects.requireNonNull(repository, "RateLimitRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RateLimitDto> findById(String tierKey) {
    Objects.requireNonNull(tierKey, "Tier key cannot be null");
    return repository.findById(tierKey).map(RateLimitPersistenceProviderImpl::toDto);
  }

  @Override
  public RateLimitDto save(RateLimitDto rateLimitDto) {
    Objects.requireNonNull(rateLimitDto, "RateLimitDto cannot be null");
    RateLimitEntity entity = toEntity(rateLimitDto);
    RateLimitEntity saved = repository.save(entity);
    return toDto(saved);
  }

  @Override
  public RateLimitDto updateDailyRequests(String tierKey, Integer dailyRequests) {
    Objects.requireNonNull(tierKey, "Tier key cannot be null");
    RateLimitEntity entity =
        repository
            .findById(tierKey)
            .orElseThrow(() -> new IllegalArgumentException("Unknown tier: " + tierKey));
    entity.setDailyRequests(dailyRequests);
    return toDto(repository.save(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> findDefaultTierKey() {
    return repository.findFirstByIsDefaultTrue().map(RateLimitEntity::getTierKey);
  }

  private static RateLimitDto toDto(RateLimitEntity entity) {
    if (entity == null) {
      return null;
    }
    return new RateLimitDto(
        entity.getTierKey(),
        entity.getRequestsPerMinute(),
        entity.getConcurrentJobs(),
        entity.getDailyRequests());
  }

  static RateLimitEntity toEntity(RateLimitDto dto) {
    if (dto == null) {
      return null;
    }
    RateLimitEntity entity = new RateLimitEntity();
    entity.setTierKey(dto.tierKey());
    entity.setRequestsPerMinute(dto.requestsPerMinute());
    entity.setConcurrentJobs(dto.concurrentJobs());
    entity.setDailyRequests(dto.dailyRequests());
    return entity;
  }
}
