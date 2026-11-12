package com.krizaka.users.persistence.application.service;

import com.krizaka.users.persistence.domain.model.ApiKeyDto;
import com.krizaka.users.persistence.domain.ports.ApiKeyPersistenceProvider;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.entity.ApiKeyEntity;
import com.krizaka.users.persistence.infrastructure.adapter.persistence.repository.ApiKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-private implementation of {@link ApiKeyPersistenceProvider}. */
@Service
@Transactional
class ApiKeyPersistenceProviderImpl implements ApiKeyPersistenceProvider {

  private static final String USER_ID_NULL = "userId cannot be null";
  private static final String ID_NULL = "id cannot be null";

  private final ApiKeyRepository repository;

  ApiKeyPersistenceProviderImpl(ApiKeyRepository repository) {
    this.repository = Objects.requireNonNull(repository, "ApiKeyRepository cannot be null");
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApiKeyDto> findByUserId(String userId) {
    Objects.requireNonNull(userId, USER_ID_NULL);
    return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(ApiKeyPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ApiKeyDto> findByUserIds(java.util.Collection<String> userIds) {
    Objects.requireNonNull(userIds, "userIds cannot be null");
    if (userIds.isEmpty()) {
      return List.of();
    }
    return repository.findByUserIdInOrderByCreatedAtDesc(userIds).stream()
        .map(ApiKeyPersistenceProviderImpl::toDto)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countByUserId(String userId) {
    Objects.requireNonNull(userId, USER_ID_NULL);
    return repository.countByUserId(userId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ApiKeyDto> findByKeyHash(String keyHash) {
    Objects.requireNonNull(keyHash, "keyHash cannot be null");
    return repository.findByKeyHash(keyHash).map(ApiKeyPersistenceProviderImpl::toDto);
  }

  @Override
  public ApiKeyDto save(ApiKeyDto apiKeyDto) {
    Objects.requireNonNull(apiKeyDto, "apiKeyDto cannot be null");
    ApiKeyEntity entity = new ApiKeyEntity();
    entity.setId(apiKeyDto.id());
    entity.setUserId(apiKeyDto.userId());
    entity.setName(apiKeyDto.name());
    entity.setKeyPrefix(apiKeyDto.keyPrefix());
    entity.setKeyHash(apiKeyDto.keyHash());
    entity.setCreatedAt(apiKeyDto.createdAt() != null ? apiKeyDto.createdAt() : Instant.now());
    entity.setLastUsedAt(apiKeyDto.lastUsedAt());
    entity.setScopes(String.join(",", new java.util.TreeSet<>(apiKeyDto.scopes())));
    return toDto(repository.save(entity));
  }

  @Override
  public long deleteByIdAndUserId(String id, String userId) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(userId, USER_ID_NULL);
    return repository.deleteByIdAndUserId(id, userId);
  }

  @Override
  public long deleteUnlessLast(String id, String userId) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(userId, USER_ID_NULL);
    repository.lockOwner(userId);
    return repository.deleteUnlessLast(id, userId);
  }

  @Override
  public void touchLastUsed(String id, Instant lastUsedAt) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(lastUsedAt, "lastUsedAt cannot be null");
    repository.touchLastUsed(id, lastUsedAt);
  }

  private static ApiKeyDto toDto(ApiKeyEntity entity) {
    if (entity == null) {
      return null;
    }
    return new ApiKeyDto(
        entity.getId(),
        entity.getUserId(),
        entity.getName(),
        entity.getKeyPrefix(),
        entity.getKeyHash(),
        entity.getCreatedAt(),
        entity.getLastUsedAt(),
        parseScopes(entity.getScopes()));
  }

  private static java.util.Set<String> parseScopes(String raw) {
    if (raw == null || raw.isBlank()) {
      return java.util.Set.of();
    }
    return java.util.Arrays.stream(raw.split(","))
        .map(String::strip)
        .filter(x -> !x.isEmpty())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
