package com.krizaka.users.infrastructure.adapter.persistence;

import com.krizaka.users.domain.model.ApiKey;
import com.krizaka.users.domain.ports.outbound.ApiKeyRepositoryPort;
import com.krizaka.users.persistence.domain.model.ApiKeyDto;
import com.krizaka.users.persistence.domain.ports.ApiKeyPersistenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Concrete persistence adapter implementing {@link ApiKeyRepositoryPort}. Delegates to the clean
 * {@link ApiKeyPersistenceProvider} port and maps its DTOs into hash-free domain records.
 */
@Component
class ApiKeyRepositoryAdapter implements ApiKeyRepositoryPort {

  private static final String USER_ID_NULL = "userId cannot be null";
  private static final String ID_NULL = "id cannot be null";

  private final ApiKeyPersistenceProvider provider;

  ApiKeyRepositoryAdapter(ApiKeyPersistenceProvider provider) {
    this.provider = Objects.requireNonNull(provider, "ApiKeyPersistenceProvider cannot be null");
  }

  @Override
  public List<ApiKey> findByUserId(String userId) {
    Objects.requireNonNull(userId, USER_ID_NULL);
    return provider.findByUserId(userId).stream().map(ApiKeyRepositoryAdapter::toDomain).toList();
  }

  @Override
  public long countByUserId(String userId) {
    Objects.requireNonNull(userId, USER_ID_NULL);
    return provider.countByUserId(userId);
  }

  @Override
  public Optional<ApiKey> findByKeyHash(String keyHash) {
    Objects.requireNonNull(keyHash, "keyHash cannot be null");
    return provider.findByKeyHash(keyHash).map(ApiKeyRepositoryAdapter::toDomain);
  }

  @Override
  public void save(
      String id,
      String userId,
      String name,
      String keyPrefix,
      String keyHash,
      java.util.Set<String> scopes) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(userId, USER_ID_NULL);
    Objects.requireNonNull(name, "name cannot be null");
    Objects.requireNonNull(keyPrefix, "keyPrefix cannot be null");
    Objects.requireNonNull(keyHash, "keyHash cannot be null");
    provider.save(new ApiKeyDto(id, userId, name, keyPrefix, keyHash, null, null, scopes));
  }

  @Override
  public long deleteByIdAndUserId(String id, String userId) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(userId, USER_ID_NULL);
    return provider.deleteByIdAndUserId(id, userId);
  }

  @Override
  public long deleteUnlessLast(String id, String userId) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(userId, USER_ID_NULL);
    return provider.deleteUnlessLast(id, userId);
  }

  @Override
  public void touchLastUsed(String id, Instant lastUsedAt) {
    Objects.requireNonNull(id, ID_NULL);
    Objects.requireNonNull(lastUsedAt, "lastUsedAt cannot be null");
    provider.touchLastUsed(id, lastUsedAt);
  }

  private static ApiKey toDomain(ApiKeyDto dto) {
    return new ApiKey(
        dto.id(),
        dto.userId(),
        dto.name(),
        dto.keyPrefix(),
        dto.createdAt(),
        dto.lastUsedAt(),
        dto.scopes());
  }
}
